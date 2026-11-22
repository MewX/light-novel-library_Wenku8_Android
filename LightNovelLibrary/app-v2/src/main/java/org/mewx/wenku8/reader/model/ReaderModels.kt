package org.mewx.wenku8.reader.model

/**
 * Six official-AndroidX page-turning modes supported by `Wenku8ReaderEngineV2`
 * (`10-next-gen-reader-engine-architecture.md` §5).
 */
enum class ReaderPageTurnMode(
    val displayTitle: String,
    val shortSubtitle: String,
) {
    COVER_SLIDE(
        displayTitle = "覆盖翻页",
        shortSubtitle = "经典单手平移书页与书脊阴影（默认）",
    ),
    SIMULATION_CURL(
        displayTitle = "仿真卷曲",
        shortSubtitle = "纸张贝塞尔卷曲折痕与透印质感",
    ),
    SPRING_SLIDE(
        displayTitle = "平滑滑动",
        shortSubtitle = "M3 Expressive 弹性跟手滑动",
    ),
    CONTINUOUS_SCROLL(
        displayTitle = "上下卷轴",
        shortSubtitle = "条漫流连续滚动与全宽插图卡片",
    ),
    VERTICAL_BUNKOBON(
        displayTitle = "日式竖排",
        shortSubtitle = "从右至左文库本竖排与竖式标点",
    ),
    EINK_INSTANT(
        displayTitle = "墨水屏极速",
        shortSubtitle = "0ms 无动画瞬切与高对比度墨水屏优化",
    ),
}

/**
 * Built-in paper & ink palettes for `UnifiedReaderScreen` (`10-next-gen-reader-engine-architecture.md` §7).
 */
enum class ReaderPaperPreset(
    val displayTitle: String,
    val isDark: Boolean,
    val backgroundArgb: Long,
    val primaryTextArgb: Long,
    val secondaryTextArgb: Long,
) {
    PARCHMENT(
        displayTitle = "羊皮纸",
        isDark = false,
        backgroundArgb = 0xFFE2C7A4L,
        primaryTextArgb = 0xFF241E16L,
        secondaryTextArgb = 0xFF574A3AL,
    ),
    PURE_WHITE(
        displayTitle = "纯白",
        isDark = false,
        backgroundArgb = 0xFFFCFCFBL,
        primaryTextArgb = 0xFF1A1B1EL,
        secondaryTextArgb = 0xFF5E6268L,
    ),
    EYE_CARE_GREEN(
        displayTitle = "护眼绿",
        isDark = false,
        backgroundArgb = 0xFFE3EFE4L,
        primaryTextArgb = 0xFF1B2B1EL,
        secondaryTextArgb = 0xFF4F6653L,
    ),
    CARBON_NIGHT(
        displayTitle = "暗夜黑",
        isDark = true,
        backgroundArgb = 0xFF141615L,
        primaryTextArgb = 0xFFD9DCD8L,
        secondaryTextArgb = 0xFF8B918CL,
    ),
    PURE_AMOLED(
        displayTitle = "纯黑 AMOLED",
        isDark = true,
        backgroundArgb = 0xFF000000L,
        primaryTextArgb = 0xFFCCCCCAL,
        secondaryTextArgb = 0xFF7D807DL,
    ),
}

/**
 * Multi-column book-spread layout preference for paged reading modes (`Issue #204`).
 *
 * - [AUTO]: Dual-column book spread on large screens (tablets & unfolded foldables), single-column
 *   on phones (including regular phone landscape).
 * - [SINGLE]: Always render single-column regardless of screen size.
 * - [DUAL]: Prefer dual-column whenever the viewport can fit two legible columns.
 */
enum class ReaderColumnMode(
    val displayTitle: String,
    val shortSubtitle: String,
) {
    AUTO(
        displayTitle = "自动双栏",
        shortSubtitle = "大屏与展开折叠屏自动双栏，手机横竖屏保持单栏",
    ),
    SINGLE(
        displayTitle = "单栏",
        shortSubtitle = "始终使用单列排版",
    ),
    DUAL(
        displayTitle = "双栏",
        shortSubtitle = "宽屏下始终使用双栏捧书排版",
    ),
}

