package org.mewx.wenku8.reader.engine

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.data.local.db.Wenku8Database
import org.mewx.wenku8.data.local.db.entity.ChapterContentEntity
import org.mewx.wenku8.data.local.db.entity.NovelEntity
import org.mewx.wenku8.data.remote.api.OnlineSearchField
import org.mewx.wenku8.data.remote.api.WenkuApiResult
import org.mewx.wenku8.data.remote.api.WenkuCatalogGateway
import org.mewx.wenku8.data.remote.parser.ParsedCloudBookshelfEntry
import org.mewx.wenku8.data.remote.parser.ParsedNovelFullMeta
import org.mewx.wenku8.data.remote.parser.ParsedNovelListItem
import org.mewx.wenku8.data.remote.parser.ParsedNovelListPage
import org.mewx.wenku8.data.repository.NovelTocSyncRepository
import org.mewx.wenku8.data.repository.ParsedChapterToc
import org.mewx.wenku8.data.repository.ParsedVolumeToc
import org.mewx.wenku8.util.DigestUtils
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReaderEngineV2Test {

    private lateinit var database: Wenku8Database
    private lateinit var testScope: CoroutineScope
    private lateinit var tocSyncRepository: NovelTocSyncRepository
    private lateinit var fakeGateway: FakeReaderEngineGateway

    @Before
    fun setUp() {
        val directExecutor = Executor { runnable -> runnable.run() }
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            Wenku8Database::class.java,
        )
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()

        testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        tocSyncRepository = NovelTocSyncRepository(database)
        fakeGateway = FakeReaderEngineGateway()
    }

    @After
    fun tearDown() {
        testScope.cancel()
        database.close()
    }

    @Test
    fun wenkuChapterParser_normalizesIndentationExtractsInlineImagesAndSearchesParagraphs(): Unit {
        val rawDoText = buildString {
            append("\uFEFF第一卷 序章\r\n")
            append("\r\n")
            append("　　罗伦斯驾驶着装满麦子的马车穿过金黄的原野。\r\n")
            append("    「咱可是被奉为神明的贤狼赫萝！」\r\n")
            append("傍晚微风吹拂<!--image-->http://pic.wenku8.cn/pictures/0/5/101/001.jpg<!--image-->麦浪轻轻摇曳<!--image-->http://pic.wenku8.cn/pictures/0/5/101/002.jpg<!--image-->远方钟声响起。\r\n")
        }

        val ast = WenkuChapterParser.parse(
            rawChapterText = rawDoText,
            aid = 5,
            vid = 10,
            cid = 101,
            volumeTitle = "第一卷",
            chapterTitle = "序章",
        )

        assertFalse(ast.isDedicatedIllustrationChapter)
        assertEquals(8, ast.blocks.size)
        assertEquals(2, ast.illustrationBlocks.size)

        val title = ast.blocks[0] as ChapterBlock.TitleBlock
        assertEquals(0, title.blockIndex)
        assertEquals("第一卷 序章", title.title)

        val narration = ast.blocks[1] as ChapterBlock.ParagraphBlock
        assertEquals(1, narration.blockIndex)
        assertFalse(narration.isDialogue)
        assertEquals("罗伦斯驾驶着装满麦子的马车穿过金黄的原野。", narration.text)
        // Must normalize existing U+3000 without double-indenting into 4 full-width spaces.
        assertEquals("\u3000\u3000罗伦斯驾驶着装满麦子的马车穿过金黄的原野。", narration.formattedText)

        val dialogue = ast.blocks[2] as ChapterBlock.ParagraphBlock
        assertTrue(dialogue.isDialogue)
        assertEquals("「咱可是被奉为神明的贤狼赫萝！」", dialogue.text)

        val beforeImg = ast.blocks[3] as ChapterBlock.ParagraphBlock
        assertEquals("傍晚微风吹拂", beforeImg.text)

        val img0 = ast.blocks[4] as ChapterBlock.IllustrationBlock
        assertEquals(0, img0.imageIndexInChapter)
        assertEquals("http://pic.wenku8.cn/pictures/0/5/101/001.jpg", img0.imageUrl)

        val midText = ast.blocks[5] as ChapterBlock.ParagraphBlock
        assertEquals("麦浪轻轻摇曳", midText.text)

        val img1 = ast.blocks[6] as ChapterBlock.IllustrationBlock
        assertEquals(1, img1.imageIndexInChapter)
        assertEquals("http://pic.wenku8.cn/pictures/0/5/101/002.jpg", img1.imageUrl)

        val afterImg1 = ast.blocks[7] as ChapterBlock.ParagraphBlock
        assertEquals("远方钟声响起。", afterImg1.text)
    }

    @Test
    fun wenkuChapterParser_preservesTrailingTextAfterImageAndFindsSearchMatches(): Unit {
        val raw = "第一卷 第一话\n前段<!--image-->http://pic.wenku8.cn/a.jpg<!--image-->后段出现了贤狼赫萝的身影。"
        val ast = WenkuChapterParser.parse(
            rawChapterText = raw,
            aid = 5,
            vid = 10,
            cid = 102,
            volumeTitle = "第一卷",
            chapterTitle = "第一话",
        )
        assertEquals(4, ast.blocks.size)
        val trailing = ast.blocks[3] as ChapterBlock.ParagraphBlock
        assertEquals("后段出现了贤狼赫萝的身影。", trailing.text)

        val matches = ast.findOccurrences("贤狼赫萝")
        assertEquals(1, matches.size)
        assertEquals(3, matches.first().blockIndex)
        assertEquals(5, matches.first().startCharOffset)
    }

    @Test
    fun staticLayoutPaginator_enforcesCjkKinsokuShoriAndJustificationAcrossDeterministicWidths(): Unit {
        // Construct a paragraph specifically engineered so naive 10-cell breaking would place
        // closing punctuation `。」` at the start of line 2 and leave opening bracket `「` hanging
        // alone at the end of line 2!
        // With `fontSizePx = 20f` and `contentWidthPx = 200` (10 full-width CJK cells per line):
        // formattedText = "　　一二三四五六七八。」九十甲乙丙丁戊「己庚辛壬！」"
        // Naive 10-cell split:
        //   [0..10): "　　一二三四五六七八" -> next line starts with '。' (FORBIDDEN!)
        val trickyProse = buildString {
            append("第一卷 禁则测试\n")
            append("一二三四五六七八。」九十甲乙丙丁戊「己庚辛壬！」\n")
            append("甲乙丙丁戊己庚辛，壬癸子丑寅卯辰巳（午未申酉戌亥）。")
        }
        val ast = WenkuChapterParser.parse(
            rawChapterText = trickyProse,
            aid = 1,
            vid = 1,
            cid = 1,
            volumeTitle = "第一卷",
            chapterTitle = "禁则测试",
        )

        val paginator = StaticLayoutPaginator(lineBreaker = DeterministicCellLineBreaker)
        val config = ReaderLayoutConfig(
            viewportWidthPx = 240,
            viewportHeightPx = 900,
            fontSizePx = 20f,
            lineDistancePx = 10f,
            paragraphDistancePx = 16f,
            pageEdgeDistancePx = 20f, // contentWidthPx = 200 -> exactly 10 CJK cells max per line
        )

        val layout = paginator.paginate(ast, config)
        val textLines = layout.pages
            .flatMap { it.elements }
            .filterIsInstance<ReaderPageElement.TextLine>()

        assertTrue(textLines.size >= 5)
        for (line in textLines) {
            if (!line.isParagraphFirstLine) {
                val firstChar = line.text.first()
                assertFalse(
                    "Line '${line.text}' illegally started with Kinsoku char '$firstChar'",
                    StaticLayoutPaginator.isForbiddenLineStartChar(firstChar),
                )
            }
            if (!line.isParagraphLastLine) {
                val lastChar = line.text.last()
                assertFalse(
                    "Line '${line.text}' illegally ended with hanging opening bracket '$lastChar'",
                    StaticLayoutPaginator.isForbiddenLineEndChar(lastChar),
                )
            }
        }

        // At least one Kinsoku-pulled wrapped line must carry positive CJK justification letter spacing,
        // whereas paragraph-ending lines must never be justified (`0f`).
        assertTrue(textLines.any { !it.isParagraphLastLine && it.extraLetterSpacingPx > 0f })
        assertTrue(textLines.filter { it.isParagraphLastLine }.all { it.extraLetterSpacingPx == 0f })
    }

    @Test
    fun staticLayoutPaginator_placesInlineImageWhenSpaceAllowsAndFullBleedWhenTightOrDedicatedGallery(): Unit {
        val paginator = StaticLayoutPaginator(lineBreaker = DeterministicCellLineBreaker)
        val config = ReaderLayoutConfig(
            viewportWidthPx = 400,
            viewportHeightPx = 800,
            fontSizePx = 20f,
            lineDistancePx = 8f,
            paragraphDistancePx = 14f,
            pageEdgeDistancePx = 20f,
        )

        // 1. Short opening paragraph + 1 inline image -> fits inline on Page 0!
        val inlineAst = WenkuChapterParser.parse(
            rawChapterText = "第一卷 序章\n短短一句话。\n<!--image-->http://pic.wenku8.cn/inline.jpg<!--image-->",
            aid = 1,
            vid = 1,
            cid = 10,
            volumeTitle = "第一卷",
            chapterTitle = "序章",
        )
        val inlineLayout = paginator.paginate(inlineAst, config)
        assertEquals(1, inlineLayout.pages.size)
        val inlineSlot = inlineLayout.pages[0].elements.filterIsInstance<ReaderPageElement.ImageSlot>().single()
        assertFalse(inlineSlot.isFullBleedArtbookSlot)
        assertFalse(inlineLayout.pages[0].isFullBleedArtbookPage)

        // 2. Long prose filling ~85% of Page 0 + image -> remainingHeight < minInlineImageHeight ->
        //    pushes image onto a dedicated Full-Bleed Artbook Page (Page 1)!
        val denseParagraphs = (1..14).joinToString("\n") {
            "这是第${it}段测试文字，用来填满当前页面的大部分高度以确保插图不会被挤压变形。"
        }
        val tightAst = WenkuChapterParser.parse(
            rawChapterText = "第一卷 第一章\n$denseParagraphs\n<!--image-->http://pic.wenku8.cn/plate.jpg<!--image-->",
            aid = 1,
            vid = 1,
            cid = 11,
            volumeTitle = "第一卷",
            chapterTitle = "第一章",
        )
        val tightLayout = paginator.paginate(tightAst, config)
        assertTrue(tightLayout.pages.size >= 2)
        val artbookPage = tightLayout.pages.first { page ->
            page.elements.any { it is ReaderPageElement.ImageSlot }
        }
        assertTrue(artbookPage.isFullBleedArtbookPage)
        val fullSlot = artbookPage.elements.filterIsInstance<ReaderPageElement.ImageSlot>().single()
        assertTrue(fullSlot.isFullBleedArtbookSlot)

        // 3. Dedicated `<chapter>插图</chapter>` gallery -> Page 0 combines the chapter heading
        //    AND the first illustration (never a title-only Page 0!), while every illustration
        //    still gets its own Artbook Page (`pages.size == 2` for 2 images).
        val galleryAst = WenkuChapterParser.parse(
            rawChapterText = "第一卷 插图\n<!--image-->http://pic.wenku8.cn/p1.jpg<!--image-->\n<!--image-->http://pic.wenku8.cn/p2.jpg<!--image-->",
            aid = 1,
            vid = 1,
            cid = 12,
            volumeTitle = "第一卷",
            chapterTitle = "插图",
        )
        assertTrue(galleryAst.isDedicatedIllustrationChapter)
        assertTrue(galleryAst.isPureIllustrationContent)
        val galleryLayout = paginator.paginate(galleryAst, config)
        assertEquals(2, galleryLayout.pages.size)
        assertTrue(galleryLayout.pages[0].elements.any { it is ReaderPageElement.TitleLine })
        assertTrue(galleryLayout.pages[0].elements.any { it is ReaderPageElement.ImageSlot })
        val artbookPages = galleryLayout.pages.filter { it.isFullBleedArtbookPage }
        assertEquals(2, artbookPages.size)

        // Vertical Bunkobon (`竖排右翻`) also places the first illustration on Page 0 beside the title column!
        val verticalGalleryLayout = paginator.paginate(
            galleryAst,
            config.copy(isVerticalBunkobon = true),
        )
        assertEquals(2, verticalGalleryLayout.pages.size)
        assertTrue(
            verticalGalleryLayout.pages[0].elements.any {
                it is ReaderPageElement.VerticalColumn && it.isTitleColumn
            },
        )
        assertTrue(verticalGalleryLayout.pages[0].elements.any { it is ReaderPageElement.ImageSlot })
    }

    @Test
    fun simplifiedAndTraditionalIllustrationChapters_areDetectedAcrossTitleAndContentForms() {
        val positiveTitles = listOf(
            "插图",
            "插圖",
            "插画",
            "插畫",
            "彩页",
            "彩頁",
            "口绘",
            "口繪",
            "第一卷 插图",
            "第一卷 插圖",
            "第二卷插圖",
            "【彩頁】",
            "插圖1",
            "Illustrations",
        )
        for (title in positiveTitles) {
            assertTrue(
                "Expected '$title' to be recognized as an illustration chapter",
                org.mewx.wenku8.data.local.db.entity.ChapterEntity.isIllustrationChapterTitle(title),
            )
        }

        val negativeProseTitles = listOf(
            "第一章 少女的插画日记",
            "第二章 彩页背后的秘密",
            "第三章 插图画家的独白",
            "序章",
        )
        for (title in negativeProseTitles) {
            assertFalse(
                "Expected prose chapter '$title' NOT to be misclassified as illustration-only",
                org.mewx.wenku8.data.local.db.entity.ChapterEntity.isIllustrationChapterTitle(title),
            )
        }

        // Content-based pure-illustration detection works even when a chapter has an unusual title!
        val untitledGalleryAst = WenkuChapterParser.parse(
            rawChapterText = "第一卷 特典\n<!--image-->https://pic.wenku8.com/pictures/1/p1.jpg<!--image-->",
            aid = 1,
            vid = 1,
            cid = 99,
            volumeTitle = "第一卷",
            chapterTitle = "特典",
        )
        assertTrue(untitledGalleryAst.isPureIllustrationContent)
    }

    @Test
    fun staticLayoutPaginator_supportsVerticalBunkobonAndPreservesReadingAnchorAcrossFontReflows(): Unit {
        val longChapter = buildString {
            append("第一卷 琥珀色的旅途\n")
            for (idx in 1..40) {
                append("第${idx}段：「旅途漫长——但只要车轮仍在转动……终会抵达北方的故乡。」罗伦斯望着远方说道。\n")
            }
        }
        val ast = WenkuChapterParser.parse(
            rawChapterText = longChapter,
            aid = 5,
            vid = 10,
            cid = 101,
            volumeTitle = "第一卷",
            chapterTitle = "琥珀色的旅途",
        )

        val paginator = StaticLayoutPaginator(lineBreaker = DeterministicCellLineBreaker)
        val smallFontConfig = ReaderLayoutConfig(
            viewportWidthPx = 420,
            viewportHeightPx = 780,
            fontSizePx = 16f,
            lineDistancePx = 10f,
            paragraphDistancePx = 16f,
            pageEdgeDistancePx = 20f,
        )
        val largeFontConfig = smallFontConfig.copy(fontSizePx = 30f)

        val smallLayout = paginator.paginate(ast, smallFontConfig)
        val largeLayout = paginator.paginate(ast, largeFontConfig)
        assertTrue(largeLayout.pages.size > smallLayout.pages.size * 2)

        // Pick a middle page in 16px layout, capture its exact ReadingAnchor, reflow to 30px,
        // and verify the resolved page contains that exact anchor AND reflowing back to 16px
        // returns to the exact original page index!
        val midPageIndex = smallLayout.pages.size / 2
        val lockedAnchor = smallLayout.pages[midPageIndex].startAnchor

        val resolvedInLarge = largeLayout.resolvePageIndexForAnchor(lockedAnchor)
        val largePage = largeLayout.pages[resolvedInLarge]
        assertTrue(lockedAnchor >= largePage.startAnchor && lockedAnchor <= largePage.endAnchor)

        val roundTripSmallIndex = smallLayout.resolvePageIndexForAnchor(lockedAnchor)
        assertEquals(midPageIndex, roundTripSmallIndex)

        // Also switch into Mode 5 `日式文库竖排` (`isVerticalBunkobon = true`) and verify RTL columns,
        // vertical punctuation mapping (`「」→﹁﹂`, `—→︱`, `…→⋮`), and anchor resolution!
        val verticalConfig = smallFontConfig.copy(isVerticalBunkobon = true)
        val verticalLayout = paginator.paginate(ast, verticalConfig)
        val firstPageColumns = verticalLayout.pages.first().elements
            .filterIsInstance<ReaderPageElement.VerticalColumn>()
        assertTrue(firstPageColumns.size >= 3)
        for (i in 0 until firstPageColumns.lastIndex) {
            assertTrue(
                "Expected RTL columns to move right-to-left (`centerXPx` decreasing)",
                firstPageColumns[i].centerXPx > firstPageColumns[i + 1].centerXPx,
            )
        }
        val allVerticalText = verticalLayout.pages
            .flatMap { it.elements }
            .filterIsInstance<ReaderPageElement.VerticalColumn>()
            .joinToString("") { it.verticalText }
        assertTrue(allVerticalText.contains('﹁'))
        assertTrue(allVerticalText.contains('﹂'))
        assertTrue(allVerticalText.contains('︱'))
        assertTrue(allVerticalText.contains('⋮'))

        val resolvedInVertical = verticalLayout.resolvePageIndexForAnchor(lockedAnchor)
        val verticalPage = verticalLayout.pages[resolvedInVertical]
        assertTrue(lockedAnchor >= verticalPage.startAnchor && lockedAnchor <= verticalPage.endAnchor)
    }

    @Test
    fun chapterWindowBuffer_loadsOfflineFirstPrefetchesAt70PercentAndShiftsAcrossVolumeBoundaries(): Unit = runBlocking {
        val aid = 5
        database.novelDao().upsertNovel(
            NovelEntity(
                aid = aid,
                contentLang = Wenku8API.AppLanguage.SC.name,
                title = "狼与香辛料",
                metaSyncedAtMs = 1_000L,
            ),
        )
        // Deliberately use non-monotonic volume IDs (vid = 500 for Volume 1, vid = 120 for Volume 2)
        // to prove `TocDao.getOrderedChaptersWithVolume` orders strictly by `volume_order`!
        tocSyncRepository.syncNovelIndexAtomically(
            aid = aid,
            language = Wenku8API.AppLanguage.SC,
            requestStartedAtMs = 2_000L,
            parsedVolumes = listOf(
                ParsedVolumeToc(
                    vid = 500,
                    volumeTitle = "第一卷",
                    chapters = listOf(
                        ParsedChapterToc(cid = 1001, chapterTitle = "第一话 离线已缓存"),
                        ParsedChapterToc(cid = 1002, chapterTitle = "第二话 70%预加载"),
                    ),
                ),
                ParsedVolumeToc(
                    vid = 120,
                    volumeTitle = "第二卷",
                    chapters = listOf(
                        ParsedChapterToc(cid = 2001, chapterTitle = "第一话 跨卷无缝翻页"),
                    ),
                ),
            ),
        )

        // Pre-seed ONLY cid = 1001 in Room `chapter_contents` (~4 pages at 22px font).
        val ch1Body = buildMultiPageChapter("第一卷", "第一话 离线已缓存", paragraphCount = 18)
        database.chapterContentDao().upsertChapterContent(
            ChapterContentEntity(
                cid = 1001,
                aid = aid,
                contentLang = Wenku8API.AppLanguage.SC.name,
                contentText = ch1Body,
                byteLength = ch1Body.toByteArray().size,
                sha256Checksum = DigestUtils.sha256Hex(ch1Body),
                downloadedAtMs = 3_000L,
            ),
        )

        fakeGateway.remoteChapters[1002] = buildMultiPageChapter("第一卷", "第二话 70%预加载", paragraphCount = 14)
        fakeGateway.remoteChapters[2001] = buildMultiPageChapter("第二卷", "第一话 跨卷无缝翻页", paragraphCount = 12)

        val buffer = ChapterWindowBuffer(
            aid = aid,
            appLanguageProvider = { Wenku8API.AppLanguage.SC },
            tocDao = database.tocDao(),
            chapterContentDao = database.chapterContentDao(),
            catalogGateway = fakeGateway,
            novelTocSyncRepository = tocSyncRepository,
            paginator = StaticLayoutPaginator(lineBreaker = DeterministicCellLineBreaker),
            scope = testScope,
            ioDispatcher = Dispatchers.Unconfined,
            computeDispatcher = Dispatchers.Unconfined,
        )

        val config = ReaderLayoutConfig(
            viewportWidthPx = 380,
            viewportHeightPx = 640,
            fontSizePx = 22f,
            lineDistancePx = 10f,
            paragraphDistancePx = 16f,
            pageEdgeDistancePx = 20f,
        )

        // 1. Open cached Chapter 1001 at Page 0: zero network calls!
        val initialSnap = buffer.openChapter(cid = 1001, layoutConfig = config)
        assertEquals(emptyList<Int>(), fakeGateway.requestedCids)
        assertEquals(null, initialSnap.prevSlot)
        assertTrue(initialSnap.currentSlot is ChapterWindowSlot.Loaded)
        assertTrue(initialSnap.currentPageCount >= 3)
        assertTrue(initialSnap.nextSlot is ChapterWindowSlot.Unloaded)

        // 2. Settle at >= 70% of Chapter 1001 -> triggers automatic pre-fetch + pre-pagination of 1002
        //    AND caches 1002 into Room `chapter_contents`!
        val lastPageOfCh1 = initialSnap.currentPageCount - 1
        val afterPrefetchSnap = buffer.onVirtualPageSettled(lastPageOfCh1)
        assertEquals(listOf(1002), fakeGateway.requestedCids)
        assertTrue(afterPrefetchSnap.nextSlot is ChapterWindowSlot.Loaded)
        assertNotNull(database.chapterContentDao().loadChapterTextSafely(1002))

        // 3. Swipe 1 page past the end of Chapter 1001 -> lands on Page 0 of Chapter 1002 seamlessly!
        val ch2FirstVirtualPage = afterPrefetchSnap.prevPageCount + afterPrefetchSnap.currentPageCount
        val shiftedToCh2 = buffer.onVirtualPageSettled(ch2FirstVirtualPage)
        assertEquals(1002, shiftedToCh2.activeChapter?.cid)
        assertEquals(0, shiftedToCh2.activePageIndexInCurrentChapter)
        assertEquals(1001, shiftedToCh2.prevSlot?.chapter?.cid)
        assertEquals(2001, shiftedToCh2.nextSlot?.chapter?.cid)
        assertEquals(120, shiftedToCh2.nextSlot?.chapter?.vid)

        // 4. Advance to >= 70% of Chapter 1002 -> pre-fetches Volume 2 Chapter 2001 (`vid = 120`),
        //    then swipe across the volume boundary into 2001!
        val lastVirtualOfCh2 = shiftedToCh2.prevPageCount + shiftedToCh2.currentPageCount - 1
        val ch2NearEnd = buffer.onVirtualPageSettled(lastVirtualOfCh2)
        assertTrue(ch2NearEnd.nextSlot is ChapterWindowSlot.Loaded)
        val shiftedToVol2 = buffer.onVirtualPageSettled(lastVirtualOfCh2 + 1)
        assertEquals(2001, shiftedToVol2.activeChapter?.cid)
        assertEquals("第二卷", shiftedToVol2.activeChapter?.volumeTitle)

        // 5. Swipe 1 page backward (`shiftedToVol2.prevPageCount - 1`) -> shifts back to 1002's FINAL page!
        val shiftedBackToCh2 = buffer.onVirtualPageSettled(shiftedToVol2.prevPageCount - 1)
        assertEquals(1002, shiftedBackToCh2.activeChapter?.cid)
        assertEquals(
            shiftedBackToCh2.currentPageCount - 1,
            shiftedBackToCh2.activePageIndexInCurrentChapter,
        )

        // 6. Live font-size reflow (`22px -> 30px`) runs purely in memory without extra network calls!
        val networkCallsBeforeReflow = fakeGateway.requestedCids.size
        val anchorBeforeReflow = shiftedBackToCh2.activeReaderPage!!.startAnchor
        val reflowedSnap = buffer.updateLayoutConfig(
            newConfig = config.copy(fontSizePx = 30f),
            lockedAnchorOverride = anchorBeforeReflow,
        )
        assertEquals(networkCallsBeforeReflow, fakeGateway.requestedCids.size)
        val reflowedPage = reflowedSnap.activeReaderPage!!
        assertTrue(anchorBeforeReflow >= reflowedPage.startAnchor && anchorBeforeReflow <= reflowedPage.endAnchor)

        buffer.close()
    }

    @Test
    fun cachedChapterOpen_emitsSingleLoadedSnapshot_andIgnoresStaleCoordinateRevisionSettle() = runBlocking {
        val aid = 4100
        database.novelDao().upsertNovel(
            NovelEntity(
                aid = aid,
                title = "防闪烁与防双跳测试作品",
                metaSyncedAtMs = 1_000L,
            ),
        )
        tocSyncRepository.syncNovelIndexAtomically(
            aid = aid,
            language = Wenku8API.AppLanguage.SC,
            requestStartedAtMs = 2_000L,
            parsedVolumes = listOf(
                ParsedVolumeToc(
                    vid = 10,
                    volumeTitle = "第一卷",
                    chapters = listOf(
                        ParsedChapterToc(cid = 1001, chapterTitle = "第一章 长篇正文"),
                        ParsedChapterToc(cid = 1002, chapterTitle = "第二章 后记"),
                        ParsedChapterToc(cid = 1003, chapterTitle = "插图"),
                    ),
                ),
            ),
        )

        val ch1Long = buildMultiPageChapter("第一卷", "第一章 长篇正文", paragraphCount = 22)
        val ch2Short = "第一卷 第二章 后记\r\n\r\n　　简短的一页后记正文。\r\n"
        val ch3Illus = "第一卷 插图\r\n\r\n<!--image-->http://pic.wenku8.cn/pictures/4/4100/1003/1.jpg<!--image-->"

        listOf(1001 to ch1Long, 1002 to ch2Short, 1003 to ch3Illus).forEach { (cid, body) ->
            database.chapterContentDao().upsertChapterContent(
                ChapterContentEntity(
                    cid = cid,
                    aid = aid,
                    contentLang = Wenku8API.AppLanguage.SC.name,
                    contentText = body,
                    byteLength = body.toByteArray().size,
                    sha256Checksum = DigestUtils.sha256Hex(body),
                    downloadedAtMs = 3_000L,
                ),
            )
        }

        val buffer = ChapterWindowBuffer(
            aid = aid,
            appLanguageProvider = { Wenku8API.AppLanguage.SC },
            tocDao = database.tocDao(),
            chapterContentDao = database.chapterContentDao(),
            catalogGateway = fakeGateway,
            novelTocSyncRepository = tocSyncRepository,
            paginator = StaticLayoutPaginator(lineBreaker = DeterministicCellLineBreaker),
            scope = testScope,
            ioDispatcher = Dispatchers.Unconfined,
            computeDispatcher = Dispatchers.Unconfined,
        )

        val config = ReaderLayoutConfig(
            viewportWidthPx = 380,
            viewportHeightPx = 640,
            fontSizePx = 22f,
            lineDistancePx = 10f,
            paragraphDistancePx = 16f,
            pageEdgeDistancePx = 20f,
        )

        // Opening cached chapter 1002 with cached prev chapter 1001 must go straight to Loaded
        // in 1 revision bump (`0 -> 1`) without intermediate Loading skeleton or second prevSlot shift!
        val openedCh2 = buffer.openChapter(cid = 1002, layoutConfig = config)
        assertEquals(1L, openedCh2.coordinateRevision)
        assertTrue(openedCh2.currentSlot is ChapterWindowSlot.Loaded)
        assertTrue(openedCh2.prevSlot is ChapterWindowSlot.Loaded)
        assertTrue(openedCh2.prevPageCount >= 4)
        assertEquals(1002, openedCh2.activeChapter?.cid)

        // Open illustration chapter 1003 (prevSlot = 1-page 1002 -> activeVirtualPageIndex = 1).
        val openedIllus = buffer.openChapter(cid = 1003, layoutConfig = config)
        val illusRev = openedIllus.coordinateRevision
        assertEquals(1003, openedIllus.activeChapter?.cid)
        assertEquals(1, openedIllus.prevPageCount)
        assertEquals(1, openedIllus.activeVirtualPageIndex)

        // Step 1 page backward (`virtualPageIndex = 0`) into 1002.
        val shiftedToCh2 = buffer.onVirtualPageSettled(
            virtualPageIndex = 0,
            expectedCoordinateRevision = illusRev,
        )
        assertEquals(1002, shiftedToCh2.activeChapter?.cid)

        // A duplicate/stale settle callback carrying `virtualPageIndex = 0` from `illusRev`
        // MUST be ignored instead of shifting a second time into 1001!
        val afterDuplicateSettle = buffer.onVirtualPageSettled(
            virtualPageIndex = 0,
            expectedCoordinateRevision = illusRev,
        )
        assertEquals(1002, afterDuplicateSettle.activeChapter?.cid)

        buffer.close()
    }

    private fun buildMultiPageChapter(
        volumeTitle: String,
        chapterTitle: String,
        paragraphCount: Int,
    ): String = buildString {
        append("$volumeTitle $chapterTitle\r\n\r\n")
        for (i in 1..paragraphCount) {
            append("　　第${i}段正文：在清冷的晨雾之中，马蹄踏过石板街道发出清脆声响，麦穗与苹果的香气在木桶间弥漫开来。\r\n")
        }
    }
}

private class FakeReaderEngineGateway : WenkuCatalogGateway {
    override val isApiConfigured: Boolean = true
    val remoteChapters = HashMap<Int, String>()
    val requestedCids = ArrayList<Int>()

    override fun resolveCoverUrl(aid: Int): String = ""

    override suspend fun downloadBinaryBytes(url: String): ByteArray? = null

    override suspend fun fetchNovelListPage(
        sortedBy: Wenku8API.NovelSortedBy,
        pageNumber: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<ParsedNovelListPage> = WenkuApiResult.ApiNotConfigured

    override suspend fun searchNovelsByField(
        query: String,
        searchField: OnlineSearchField,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<List<Int>> = WenkuApiResult.Success(emptyList())

    override suspend fun fetchNovelShortInfo(
        aid: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<ParsedNovelListItem> = WenkuApiResult.ApiNotConfigured

    override suspend fun fetchNovelFullMeta(
        aid: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<ParsedNovelFullMeta> = WenkuApiResult.ApiNotConfigured

    override suspend fun fetchNovelFullIntro(
        aid: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<String> = WenkuApiResult.ApiNotConfigured

    override suspend fun fetchNovelVolumeToc(
        aid: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<List<ParsedVolumeToc>> = WenkuApiResult.ApiNotConfigured

    override suspend fun fetchChapterContent(
        aid: Int,
        cid: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<String> {
        requestedCids.add(cid)
        val content = remoteChapters[cid]
        return if (content != null) {
            WenkuApiResult.Success(content)
        } else {
            WenkuApiResult.NetworkError("missing cid $cid")
        }
    }

    override suspend fun fetchCloudBookshelf(
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<List<ParsedCloudBookshelfEntry>> = WenkuApiResult.Success(emptyList())
}
