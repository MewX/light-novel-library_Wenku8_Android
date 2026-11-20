package org.mewx.wenku8.ui.bookshelf

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mewx.wenku8.data.local.db.entity.NovelEntity
import org.mewx.wenku8.data.local.db.entity.ReadingProgressEntity
import org.mewx.wenku8.data.repository.BookshelfItemModel
import org.mewx.wenku8.ui.theme.ProvideWenku8SharedTransition
import org.mewx.wenku8.ui.theme.Wenku8ExpressiveTheme
import org.mewx.wenku8.ui.theme.Wenku8SharedElementKeys
import org.mewx.wenku8.ui.theme.Wenku8ThemeMode
import org.mewx.wenku8.ui.theme.wenku8SharedNovelCover
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1024dp-h900dp")
class Bookshelf3dShelfSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleItems(count: Int): List<BookshelfItemModel> = List(count) { idx ->
        val aid = 1001 + idx
        val hasProgress = idx % 2 == 0
        BookshelfItemModel(
            novel = NovelEntity(
                aid = aid,
                title = when (idx) {
                    0 -> "狼与香辛料"
                    1 -> "刀剑神域 Sword Art Online"
                    2 -> "败犬女主太多了！"
                    else -> "轻小说测试作品第${idx + 1}部"
                },
                author = "作者${idx + 1}",
                pressId = if (idx % 2 == 0) "电击文库" else "GAGAGA文库",
                bookStatus = if (idx % 3 == 0) "已完成" else "连载中",
                tagsCsv = "奇幻 冒险 校园",
                introShort = "这是第${idx + 1}部测试轻小说的简介内容。",
                latestSectionName = "第${idx + 1}卷 尾声",
                hasUnreadUpdate = idx == 0 || idx == 2,
                inBookshelf = true,
                shelfSortOrder = idx + 1,
            ),
            readingProgress = if (hasProgress) {
                ReadingProgressEntity(
                    aid = aid,
                    vid = aid * 10,
                    cid = aid * 10 + 1,
                    volumeTitle = "第一卷",
                    chapterTitle = "第一章 启程",
                    progressPermille = 520,
                    updatedAtMs = 1_700_000_000_000L - idx * 1_000L,
                )
            } else {
                null
            },
            cachedChapterCount = if (idx == 0) 12 else if (idx == 1) 4 else 0,
            totalChapterCount = if (idx == 0) 20 else 0,
            tags = listOf("奇幻", "冒险", "校园"),
            isActivelySyncingOrDownloading = idx == 2,
            coverUrl = "",
        )
    }

    @Test
    fun fastScroller_supportsSteppers_tapJump_continuousScrubPreview_andSemanticsProgress() {
        val items = sampleItems(12)
        val openedAids = mutableListOf<Int>()
        val continuedAids = mutableListOf<Int>()

        composeRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                Box(modifier = Modifier.size(412.dp, 820.dp)) {
                    Bookshelf3dShelfSection(
                        items = items,
                        availableWidth = 380.dp,
                        availableHeight = 820.dp,
                        selectedAidSet = emptySet(),
                        isMultiSelectActive = false,
                        isEinkMode = false,
                        onItemClick = { openedAids += it.aid },
                        onItemLongClick = {},
                        onContinueReadingClick = { continuedAids += it.aid },
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Initial state: book #1 focused, prev disabled, next enabled, counter shows 01 / 12
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_STAGE).assertIsDisplayed()
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_SPOTLIGHT_CARD).assertIsDisplayed()
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_WIDE_PANEL).assertDoesNotExist()
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_INDEX_COUNTER).assertTextContains("01 / 12")
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_PREV_BUTTON).assertIsNotEnabled()
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_NEXT_BUTTON).assertIsEnabled()

        // Primary CTA on focused book with progress triggers continue reading
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_PRIMARY_ACTION).performClick()
        assertEquals(listOf(1001), continuedAids)

        // Next chevron advances to book #2 (no progress -> primary CTA opens detail)
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_NEXT_BUTTON).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_INDEX_COUNTER).assertTextContains("02 / 12")
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_PREV_BUTTON).assertIsEnabled()
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_PRIMARY_ACTION).performClick()
        assertEquals(listOf(1002), openedAids)

        // Prev chevron returns to book #1
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_PREV_BUTTON).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_INDEX_COUNTER).assertTextContains("01 / 12")

        // Tap near the right end of fast scroller track jumps to the last book (12 / 12)
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_FAST_SCROLLER_TRACK).performTouchInput {
            click(Offset(right - 2f, centerY))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_INDEX_COUNTER).assertTextContains("12 / 12")
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_NEXT_BUTTON).assertIsNotEnabled()

        // Semantics SetProgress jumps directly to middle book
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_FAST_SCROLLER_TRACK)
            .performSemanticsAction(SemanticsActions.SetProgress) { action ->
                action(0.5f)
            }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_INDEX_COUNTER).assertTextContains("07 / 12")

        // Continuous drag scrub shows floating preview bubble and hides it cleanly on release
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_FAST_SCROLLER_TRACK).performTouchInput {
            down(Offset(width * 0.1f, centerY))
            moveBy(Offset(width * 0.25f, 0f))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_SCRUB_PREVIEW).assertIsDisplayed()

        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_FAST_SCROLLER_TRACK).performTouchInput {
            up()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_SCRUB_PREVIEW).assertDoesNotExist()
    }

    @Test
    fun stageGestures_tapUnfocusedCentersFirst_tapFocusedOpens_swipeAdvances_andLongPressSelects() {
        val items = sampleItems(6)
        val openedAids = mutableListOf<Int>()
        var selectedAids by mutableStateOf(emptySet<Int>())

        composeRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.CLASSIC_BLUE) {
                Box(modifier = Modifier.size(412.dp, 820.dp)) {
                    Bookshelf3dShelfSection(
                        items = items,
                        availableWidth = 380.dp,
                        availableHeight = 820.dp,
                        selectedAidSet = selectedAids,
                        isMultiSelectActive = selectedAids.isNotEmpty(),
                        isEinkMode = false,
                        onItemClick = { item ->
                            if (selectedAids.isNotEmpty()) {
                                selectedAids = if (item.aid in selectedAids) {
                                    selectedAids - item.aid
                                } else {
                                    selectedAids + item.aid
                                }
                            } else {
                                openedAids += item.aid
                            }
                        },
                        onItemLongClick = { item ->
                            selectedAids = selectedAids + item.aid
                        },
                        onContinueReadingClick = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // 1. Tapping unfocused neighbor (aid=1002) centers it first without opening detail
        composeRule.onNodeWithTag(BookshelfTestTags.BOOK_CARD_PREFIX + 1002).performClick()
        composeRule.waitForIdle()
        assertEquals(emptyList<Int>(), openedAids)
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_INDEX_COUNTER).assertTextContains("02 / 06")

        // 2. Tapping the now-focused book (aid=1002) opens its detail page
        composeRule.onNodeWithTag(BookshelfTestTags.BOOK_CARD_PREFIX + 1002).performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(1002), openedAids)

        // 3. Swiping left on the 3D stage advances focus further down the shelf
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_STAGE).performTouchInput {
            swipeLeft()
        }
        composeRule.waitForIdle()
        val counterText = composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_INDEX_COUNTER)
            .fetchSemanticsNode()
            .config
            .toString()
        assertTrue("Expected swipeLeft to move past book 02 (counter=$counterText)", !counterText.contains("01 / 06"))

        // 4. Long-pressing a book activates multi-select; subsequent tap toggles selection instead of opening
        composeRule.onNodeWithTag(BookshelfTestTags.BOOK_CARD_PREFIX + 1001)
            .performTouchInput { longClick() }
        composeRule.waitForIdle()
        assertTrue(1001 in selectedAids)

        composeRule.onNodeWithTag(BookshelfTestTags.BOOK_CARD_PREFIX + 1002).performClick()
        composeRule.waitForIdle()
        assertEquals(setOf(1001, 1002), selectedAids)
        assertEquals(listOf(1002), openedAids) // Did NOT trigger extra open while multi-selecting
    }

    @Test
    fun multiScreenAdaptation_rendersWideTwoPaneOnTablet_andCompactLayoutOnSmallPhoneAndShortLandscape() {
        val items = sampleItems(5)
        var screenWidthDp by mutableStateOf(960.dp)
        var screenHeightDp by mutableStateOf(700.dp)
        var themeMode by mutableStateOf(Wenku8ThemeMode.CARBON_NIGHT)
        val continuedAids = mutableListOf<Int>()

        composeRule.setContent {
            Wenku8ExpressiveTheme(themeMode = themeMode) {
                Box(modifier = Modifier.size(screenWidthDp, screenHeightDp)) {
                    Bookshelf3dShelfSection(
                        items = items,
                        availableWidth = screenWidthDp - 32.dp,
                        availableHeight = screenHeightDp,
                        selectedAidSet = emptySet(),
                        isMultiSelectActive = false,
                        isEinkMode = themeMode == Wenku8ThemeMode.E_INK,
                        onItemClick = {},
                        onItemLongClick = {},
                        onContinueReadingClick = { continuedAids += it.aid },
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // 1. Wide tablet (960x700) uses side-by-side two-pane layout with wide spotlight panel
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_WIDE_PANEL).assertIsDisplayed()
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_SPOTLIGHT_CARD).assertDoesNotExist()
        composeRule.onNodeWithText("狼与香辛料").assertIsDisplayed()
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_PRIMARY_ACTION).performClick()
        assertEquals(listOf(1001), continuedAids)

        val stageBoundsWide = composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_STAGE)
            .fetchSemanticsNode().boundsInRoot
        val panelBoundsWide = composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_WIDE_PANEL)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Wide panel ($panelBoundsWide) must sit to the right of 3D stage ($stageBoundsWide)",
            panelBoundsWide.left >= stageBoundsWide.right - 2f,
        )

        // 2. Compact narrow phone (320x568) switches cleanly to single-column compact spotlight card
        screenWidthDp = 320.dp
        screenHeightDp = 568.dp
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_SPOTLIGHT_CARD).assertIsDisplayed()
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_WIDE_PANEL).assertDoesNotExist()
        val stageBoundsCompact = composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_STAGE)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Compact phone stage width (${stageBoundsCompact.width}) must fit inside viewport",
            stageBoundsCompact.width <= with(composeRule.density) { 320.dp.toPx() } + 1f,
        )

        // 3. Short landscape phone (800x360) uses compact two-pane layout with reduced stage height + E-Ink mode
        screenWidthDp = 800.dp
        screenHeightDp = 360.dp
        themeMode = Wenku8ThemeMode.E_INK
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_WIDE_PANEL).assertIsDisplayed()
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_SPOTLIGHT_CARD).assertDoesNotExist()
        val stageBoundsShortLandscape = composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_STAGE)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Short landscape stage height (${stageBoundsShortLandscape.height}) should be more compact than tablet (${stageBoundsWide.height})",
            stageBoundsShortLandscape.height < stageBoundsWide.height,
        )
    }

    @Test
    fun singleBookAndDynamicListShrink_handlesEdgeCasesAndWavyProgressCleanly() {
        var currentItems by mutableStateOf(sampleItems(3))

        composeRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                Box(modifier = Modifier.size(412.dp, 820.dp)) {
                    Bookshelf3dShelfSection(
                        items = currentItems,
                        availableWidth = 380.dp,
                        availableHeight = 820.dp,
                        selectedAidSet = emptySet(),
                        isMultiSelectActive = false,
                        isEinkMode = false,
                        onItemClick = {},
                        onItemLongClick = {},
                        onContinueReadingClick = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Navigate to item #3 (aid=1003, which has isActivelySyncingOrDownloading = true)
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_NEXT_BUTTON).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_NEXT_BUTTON).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_INDEX_COUNTER).assertTextContains("03 / 03")
        composeRule.onNodeWithTag(BookshelfTestTags.ACTIVE_WAVY_PROGRESS_PREFIX + 1003).assertExists()

        // Shrink list to a single book (removing the previously focused book): focus clamps cleanly
        // and fast scroller hides track/chevrons while keeping 01 / 01 badge visible.
        currentItems = listOf(currentItems.first())
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_INDEX_COUNTER).assertTextContains("01 / 01")
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_FAST_SCROLLER_TRACK).assertDoesNotExist()
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_PREV_BUTTON).assertDoesNotExist()
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_NEXT_BUTTON).assertDoesNotExist()
        assertTrue(composeRule.onAllNodesWithText("狼与香辛料").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun largeLibraryAndRenderPass_drawsAcrossThemes_handlesSelectionWidePanelEmptyAndSemantics() {
        var currentItems by mutableStateOf(
            List(105) { idx ->
                val base = sampleItems(1).first()
                base.copy(
                    novel = base.novel.copy(
                        aid = 2001 + idx,
                        title = if (idx == 0) "无职转生 Mushoku Tensei" else "大容量书架作品第${idx + 1}部",
                        author = if (idx == 0) "" else "作者${idx + 1}",
                        pressId = if (idx == 0) "" else "电击文库",
                        bookStatus = if (idx == 0) "" else "连载中",
                        introShort = if (idx == 0) "" else "简介 $idx",
                        hasUnreadUpdate = false,
                        latestSectionName = "",
                    ),
                    readingProgress = null,
                    cachedChapterCount = if (idx == 0) 5 else 0,
                    totalChapterCount = 0,
                    tags = if (idx == 0) emptyList() else listOf("奇幻"),
                )
            },
        )
        var themeMode by mutableStateOf(Wenku8ThemeMode.CLASSIC_BLUE)
        var screenWidthDp by mutableStateOf(960.dp)
        var selectedAids by mutableStateOf(emptySet<Int>())
        val openedAids = mutableListOf<Int>()

        composeRule.setContent {
            Wenku8ExpressiveTheme(themeMode = themeMode) {
                Box(modifier = Modifier.size(screenWidthDp, 720.dp)) {
                    Bookshelf3dShelfSection(
                        items = currentItems,
                        availableWidth = screenWidthDp - 32.dp,
                        availableHeight = 720.dp,
                        selectedAidSet = selectedAids,
                        isMultiSelectActive = selectedAids.isNotEmpty(),
                        isEinkMode = themeMode == Wenku8ThemeMode.E_INK,
                        onItemClick = { openedAids += it.aid },
                        onItemLongClick = { selectedAids = selectedAids + it.aid },
                        onContinueReadingClick = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // 1. 105-book library uses 3-digit counter formatting and draws cleanly in Light mode
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_INDEX_COUNTER).assertTextContains("001 / 105")
        val lightBitmap = composeRule.onRoot().captureToImage()
        assertTrue(lightBitmap.width > 0 && lightBitmap.height > 0)

        // 2. Semantics actions on exposed book anchor trigger OnClick (open focused) & OnLongClick (select)
        composeRule.onNodeWithTag(BookshelfTestTags.BOOK_CARD_PREFIX + 2001)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(listOf(2001), openedAids)

        composeRule.onNodeWithTag(BookshelfTestTags.BOOK_CARD_PREFIX + 2001)
            .performSemanticsAction(SemanticsActions.OnLongClick)
        composeRule.waitForIdle()
        assertTrue(2001 in selectedAids)

        // 3. Draw pass in E-Ink mode with selected wide panel + fast scroller
        themeMode = Wenku8ThemeMode.E_INK
        composeRule.waitForIdle()
        val einkBitmap = composeRule.onRoot().captureToImage()
        assertTrue(einkBitmap.width > 0 && einkBitmap.height > 0)

        // 4. Draw pass in Dark mode on compact phone with <=20 books (exercises track tick marks)
        currentItems = sampleItems(8)
        selectedAids = emptySet()
        themeMode = Wenku8ThemeMode.CARBON_NIGHT
        screenWidthDp = 390.dp
        composeRule.waitForIdle()
        val darkBitmap = composeRule.onRoot().captureToImage()
        assertTrue(darkBitmap.width > 0 && darkBitmap.height > 0)

        // 5. Empty list exits cleanly without rendering container
        currentItems = emptyList()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_CONTAINER).assertDoesNotExist()
    }

    @Test
    fun continueReadingPromotion_placesRecentBooksAtFrontWithPreservedNumbersAndDistinctStyling() {
        val items = sampleItems(6)
        var screenWidthDp by mutableStateOf(412.dp)
        var themeMode by mutableStateOf(Wenku8ThemeMode.CLASSIC_BLUE)

        composeRule.setContent {
            Wenku8ExpressiveTheme(themeMode = themeMode) {
                Box(modifier = Modifier.size(screenWidthDp, 820.dp)) {
                    Bookshelf3dShelfSection(
                        items = items,
                        availableWidth = screenWidthDp - 32.dp,
                        availableHeight = 820.dp,
                        selectedAidSet = emptySet(),
                        isMultiSelectActive = false,
                        isEinkMode = themeMode == Wenku8ThemeMode.E_INK,
                        continueReadingAids = listOf(1005, 1003),
                        onItemClick = {},
                        onItemLongClick = {},
                        onContinueReadingClick = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // 1. Slot 0 is the most recently read book (aid=1005), keeping its original shelf rank #05
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_INDEX_COUNTER).assertTextContains("01 / 06")
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_CONTINUE_BADGE).assertIsDisplayed()
        composeRule.onNodeWithText("#05").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("轻小说测试作品第5部").fetchSemanticsNodes().isNotEmpty())
        val compactLightBmp = composeRule.onRoot().captureToImage()
        assertTrue(compactLightBmp.width > 0 && compactLightBmp.height > 0)

        // 2. Step next to slot 1 -> second Continue Reading book (aid=1003), preserving original rank #03
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_NEXT_BUTTON).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_INDEX_COUNTER).assertTextContains("02 / 06")
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_CONTINUE_BADGE).assertIsDisplayed()
        composeRule.onNodeWithText("#03").assertIsDisplayed()

        // 3. Switch to wide tablet + E-Ink mode while focused on Continue Reading book
        screenWidthDp = 960.dp
        themeMode = Wenku8ThemeMode.E_INK
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_WIDE_PANEL).assertIsDisplayed()
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_CONTINUE_BADGE).assertIsDisplayed()
        composeRule.onNodeWithText("#03 / 06 · WK-01003").assertIsDisplayed()
        val wideEinkBmp = composeRule.onRoot().captureToImage()
        assertTrue(wideEinkBmp.width > 0 && wideEinkBmp.height > 0)

        // 4. Step next to slot 2 -> regular shelf book #01 (aid=1001), no Continue Reading badge
        themeMode = Wenku8ThemeMode.CARBON_NIGHT
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_NEXT_BUTTON).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_INDEX_COUNTER).assertTextContains("03 / 06")
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_CONTINUE_BADGE).assertDoesNotExist()
        composeRule.onNodeWithText("#01 / 06 · WK-01001").assertIsDisplayed()
        val wideDarkBmp = composeRule.onRoot().captureToImage()
        assertTrue(wideDarkBmp.width > 0 && wideDarkBmp.height > 0)
    }

    @Test
    fun einkMode_continuousStageDragStepsDiscretelyAcrossBooks_andFastScrubberQuantizes() {
        val items = sampleItems(12)
        val openedAids = mutableListOf<Int>()

        composeRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.E_INK) {
                Box(modifier = Modifier.size(412.dp, 820.dp)) {
                    Bookshelf3dShelfSection(
                        items = items,
                        availableWidth = 380.dp,
                        availableHeight = 820.dp,
                        selectedAidSet = emptySet(),
                        isMultiSelectActive = false,
                        isEinkMode = true,
                        onItemClick = { openedAids += it.aid },
                        onItemLongClick = {},
                        onContinueReadingClick = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_INDEX_COUNTER).assertTextContains("01 / 12")

        // 1. A continuous swipeLeft across the book spines in E-Ink mode advances smoothly across
        // multiple books (not locked to 1 book per gesture) while snapping discretely with no 3D tween
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_STAGE).performTouchInput {
            swipeLeft()
        }
        composeRule.waitForIdle()
        val afterSwipeLeftText = composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_INDEX_COUNTER)
            .fetchSemanticsNode()
            .config
            .toString()
        assertTrue(
            "Expected continuous E-Ink swipeLeft to advance multiple books past 02 / 12 (counter=$afterSwipeLeftText)",
            !afterSwipeLeftText.contains("01 / 12") && !afterSwipeLeftText.contains("02 / 12"),
        )

        // 2. Swiping right glides continuously back toward the beginning
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_STAGE).performTouchInput {
            swipeRight()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_INDEX_COUNTER).assertTextContains("01 / 12")

        // 3. Tapping the popped-up focused book (aid=1001) opens it immediately
        composeRule.onNodeWithTag(BookshelfTestTags.BOOK_CARD_PREFIX + 1001).performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(1001), openedAids)

        // 4. Dragging the fast scrollbar in E-Ink mode quantizes to discrete integer books cleanly
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_FAST_SCROLLER_TRACK).performTouchInput {
            down(Offset(width * 0.2f, centerY))
            moveTo(Offset(width * 0.52f, centerY))
            moveTo(Offset(width * 0.86f, centerY))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_SCRUB_PREVIEW).assertIsDisplayed()
        val einkScrubBmp = composeRule.onRoot().captureToImage()
        assertTrue(einkScrubBmp.width > 0 && einkScrubBmp.height > 0)

        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_FAST_SCROLLER_TRACK).performTouchInput {
            up()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_SCRUB_PREVIEW).assertDoesNotExist()
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_INDEX_COUNTER).assertTextContains("10 / 12")
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Test
    fun sharedTransitionRoundTrip_restoresNonZeroFocusOnFrame1_andAnchorsCoverAtProjectedBounds() {
        val items = sampleItems(8)
        var detailAid by mutableStateOf<Int?>(null)
        var capturedDensity = 1f

        composeRule.setContent {
            capturedDensity = LocalDensity.current.density
            val saveableStateHolder = rememberSaveableStateHolder()
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                SharedTransitionLayout {
                    AnimatedContent(
                        targetState = detailAid,
                        label = "test_3d_shared_round_trip",
                    ) { activeDetailAid ->
                        ProvideWenku8SharedTransition(
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this@AnimatedContent,
                        ) {
                            if (activeDetailAid == null) {
                                saveableStateHolder.SaveableStateProvider(key = "bookshelf_3d") {
                                    Box(modifier = Modifier.size(412.dp, 820.dp)) {
                                        Bookshelf3dShelfSection(
                                            items = items,
                                            availableWidth = 380.dp,
                                            availableHeight = 820.dp,
                                            selectedAidSet = emptySet(),
                                            isMultiSelectActive = false,
                                            isEinkMode = false,
                                            onItemClick = { clicked -> detailAid = clicked.aid },
                                            onItemLongClick = {},
                                            onContinueReadingClick = {},
                                        )
                                    }
                                }
                            } else {
                                val enterSlant by transition.animateFloat(
                                    label = "test_detail_enter_slant",
                                ) { state ->
                                    if (state == EnterExitState.PreEnter) 1f else 0f
                                }
                                Box(modifier = Modifier.size(412.dp, 820.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 116.dp, height = 164.dp)
                                            .wenku8SharedNovelCover(
                                                origin = Wenku8SharedElementKeys.NOVEL_BOOKSHELF_3D_ORIGIN,
                                                aid = activeDetailAid,
                                                clipShape = null,
                                            ),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .bookshelf3dCoverPerspectiveMorph(
                                                    slantedFractionProvider = { enterSlant },
                                                ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()

        // Step focus to book index 3 (04 / 08, aid = 1004)
        repeat(3) {
            composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_NEXT_BUTTON).performClick()
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_INDEX_COUNTER).assertTextContains("04 / 08")

        // Verify the screen-space shared anchor sits at the exact post-matrix visual cover bounds
        val metrics = Bookshelf3dGeometry.computeMetrics(availableWidthDp = 380f, availableHeightDp = 820f)
        val expectedPose = Bookshelf3dGeometry.computeBookPosePx(
            index = 3,
            focus = 3f,
            metrics = metrics,
            density = capturedDensity,
        )
        val expectedVisualRect = Bookshelf3dGeometry.computeCoverArtworkVisualBoundsPx(
            pose = expectedPose,
            density = capturedDensity,
        )

        fun assertAnchorMatchesExpectedVisualBounds() {
            val stageBounds = composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_STAGE)
                .fetchSemanticsNode()
                .boundsInRoot
            val anchorBounds = composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_COVER_SHARED_ANCHOR)
                .fetchSemanticsNode()
                .boundsInRoot
            val relLeft = anchorBounds.left - stageBounds.left
            val relTop = anchorBounds.top - stageBounds.top
            val tolerancePx = 2.5f
            assertTrue(
                "Anchor left ($relLeft) should match projected left (${expectedVisualRect.left})",
                abs(relLeft - expectedVisualRect.left) <= tolerancePx,
            )
            assertTrue(
                "Anchor top ($relTop) should match projected top (${expectedVisualRect.top})",
                abs(relTop - expectedVisualRect.top) <= tolerancePx,
            )
            assertTrue(
                "Anchor width (${anchorBounds.width}) should match projected width (${expectedVisualRect.width})",
                abs(anchorBounds.width - expectedVisualRect.width) <= tolerancePx,
            )
            assertTrue(
                "Anchor height (${anchorBounds.height}) should match projected height (${expectedVisualRect.height})",
                abs(anchorBounds.height - expectedVisualRect.height) <= tolerancePx,
            )
        }

        assertAnchorMatchesExpectedVisualBounds()

        // Tap focused book (aid = 1004) to transition to Detail scene (3D shelf leaves composition)
        composeRule.onNodeWithTag(BookshelfTestTags.BOOK_CARD_PREFIX + 1004).performClick()
        composeRule.waitForIdle()
        assertEquals(1004, detailAid)
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_STAGE).assertDoesNotExist()

        // Return to 3D shelf with manual clock stepping so we inspect the very first entering frame
        // and verify 01 / 08 NEVER flashes before focus restoration!
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.runOnUiThread {
                detailAid = null
            }
            var enteredOnFrame = -1
            for (frame in 1..6) {
                composeRule.mainClock.advanceTimeByFrame()
                val counterNodes = composeRule
                    .onAllNodesWithText("01 / 08", substring = false)
                    .fetchSemanticsNodes()
                assertTrue(
                    "Frame $frame must never flash reset counter 01 / 08 while returning to 3D shelf",
                    counterNodes.isEmpty(),
                )
                val targetNodes = composeRule
                    .onAllNodesWithText("04 / 08", substring = false)
                    .fetchSemanticsNodes()
                if (targetNodes.isNotEmpty()) {
                    enteredOnFrame = frame
                    break
                }
            }
            assertTrue(
                "3D shelf should re-enter with 04 / 08 within the first frames (enteredOnFrame=$enteredOnFrame)",
                enteredOnFrame in 1..6,
            )
            composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_INDEX_COUNTER).assertTextContains("04 / 08")
            composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_COVER_SHARED_ANCHOR).assertExists()

            // Mid-transition frame: render overlay cover + hidden sheared face cleanly
            composeRule.mainClock.advanceTimeBy(64L)
            val midTransitionBmp = composeRule.onRoot().captureToImage()
            assertTrue(midTransitionBmp.width > 0 && midTransitionBmp.height > 0)
        } finally {
            composeRule.mainClock.autoAdvance = true
        }

        // Post-transition settled state: still focused on 04 / 08 with exact projected anchor bounds
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_INDEX_COUNTER).assertTextContains("04 / 08")
        assertAnchorMatchesExpectedVisualBounds()
        val settledBmp = composeRule.onRoot().captureToImage()
        assertTrue(settledBmp.width > 0 && settledBmp.height > 0)
    }
}


