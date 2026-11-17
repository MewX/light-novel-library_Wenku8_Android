package org.mewx.wenku8.domain.model

import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.data.remote.api.OnlineSearchField

/**
 * Three discovery feeds hosted under `Tab 2 · 最新` (`PrimaryTabRow`).
 */
enum class LatestFeedCategory(val sortedBy: Wenku8API.NovelSortedBy) {
    /** `最近更新` — default landing feed on cold start. */
    RECENT_UPDATES(Wenku8API.NovelSortedBy.lastUpdate),

    /** `最新入库` — newly cataloged light novels. */
    NEW_ARRIVALS(Wenku8API.NovelSortedBy.postDate),

    /** `完结精品` — completed series catalog. */
    COMPLETED_GEMS(Wenku8API.NovelSortedBy.fullFlag),
}

/**
 * Primary metric selector (`ButtonGroup`) under `Tab 3 · 排行`.
 *
 * Together with [RankingPeriod] and [LatestFeedCategory], covers all 13 `Wenku8API.NovelSortedBy`
 * server sort modes without a horizontally scrolling 12-tab strip.
 */
enum class RankingMetric(val supportsTimePeriod: Boolean) {
    /** `点击榜` (`dayVisit`, `weekVisit`, `monthVisit`, `allVisit`). */
    VISITS(supportsTimePeriod = true),

    /** `推荐榜` (`dayVote`, `weekVote`, `monthVote`, `allVote`). */
    RECOMMENDATIONS(supportsTimePeriod = true),

    /** `总收藏` (`goodNum`). */
    FAVORITES(supportsTimePeriod = false),

    /** `字数榜` (`size`). */
    WORD_COUNT(supportsTimePeriod = false),
}

/**
 * Secondary time-window filter (`FilterChip` row) for [RankingMetric.VISITS] and
 * [RankingMetric.RECOMMENDATIONS]. Defaults to [WEEK] (`周榜`) for fresh weekly discovery.
 */
enum class RankingPeriod {
    WEEK,
    DAY,
    MONTH,
    ALL_TIME;
}

fun resolveRankingSortedBy(metric: RankingMetric, period: RankingPeriod): Wenku8API.NovelSortedBy {
    return when (metric) {
        RankingMetric.VISITS -> when (period) {
            RankingPeriod.DAY -> Wenku8API.NovelSortedBy.dayVisit
            RankingPeriod.WEEK -> Wenku8API.NovelSortedBy.weekVisit
            RankingPeriod.MONTH -> Wenku8API.NovelSortedBy.monthVisit
            RankingPeriod.ALL_TIME -> Wenku8API.NovelSortedBy.allVisit
        }
        RankingMetric.RECOMMENDATIONS -> when (period) {
            RankingPeriod.DAY -> Wenku8API.NovelSortedBy.dayVote
            RankingPeriod.WEEK -> Wenku8API.NovelSortedBy.weekVote
            RankingPeriod.MONTH -> Wenku8API.NovelSortedBy.monthVote
            RankingPeriod.ALL_TIME -> Wenku8API.NovelSortedBy.allVote
        }
        RankingMetric.FAVORITES -> Wenku8API.NovelSortedBy.goodNum
        RankingMetric.WORD_COUNT -> Wenku8API.NovelSortedBy.size
    }
}

/**
 * UI-ready summary model for a novel card across `最新`, `排行`, and `UnifiedSearchScreen`.
 */
data class DiscoveryNovelCardItem(
    val aid: Int,
    val title: String,
    val author: String,
    val bookStatus: String,
    val lastUpdate: String,
    val tags: List<String>,
    val introShort: String,
    val coverUrl: String,
    val inLocalBookshelf: Boolean = false,
    val isHydratingMetadata: Boolean = false,
) {
    /**
     * Completeness score modeled on `v1.31`'s `NovelItemInfoUpdate.populatedFieldCount()` so a
     * sparser endpoint response (e.g. `do=bookinfo` lacking `Tags`) never overwrites richer cached
     * fields for the same `aid`.
     */
    fun populatedFieldScore(): Int {
        var score = 0
        if (title.isNotBlank() && title != aid.toString()) score += 2
        if (author.isNotBlank()) score += 1
        if (bookStatus.isNotBlank()) score += 1
        if (lastUpdate.isNotBlank()) score += 1
        if (introShort.isNotBlank()) score += 1
        if (tags.isNotEmpty()) score += 1
        return score
    }
}

/**
 * Unifies Wenku8's inconsistent server-side `BookStatus` labels across endpoints:
 * `action=novellist` returns `"已完结"` / `"已完結"`, whereas `do=meta` and `do=bookinfo` return
 * `"已完成"`. Normalizing to `"已完结"` (SC) / `"已完結"` (TC) keeps novel lists, search cards,
 * bookshelf filters, and `NovelDetailScreen` completely consistent.
 */
fun normalizeBookStatusText(
    rawStatus: String,
    preferTraditionalChinese: Boolean = false,
): String {
    val trimmed = rawStatus.trim()
    if (trimmed.isEmpty()) return ""
    return when (trimmed) {
        "已完成", "已完结", "完结", "已完結", "完結" -> {
            if (preferTraditionalChinese) "已完結" else "已完结"
        }
        "连载中", "連載中" -> {
            if (preferTraditionalChinese) "連載中" else "连载中"
        }
        else -> trimmed
    }
}

enum class DiscoveryFeedErrorKind {
    API_NOT_CONFIGURED,
    NETWORK_UNREACHABLE,
    SERVER_ERROR,
    PARSE_FAILURE,
}

/**
 * Immutable snapshot of a paginated `NovelSortedBy` feed.
 *
 * Preserves already-loaded pages on pagination errors and only advances [loadedPage] once a page
 * succeeds (preventing skipped pages on transient errors).
 */
data class PaginatedNovelFeedState(
    val sortedBy: Wenku8API.NovelSortedBy,
    val items: List<DiscoveryNovelCardItem> = emptyList(),
    val loadedPage: Int = 0,
    val totalPages: Int = 0,
    val isInitialLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isAppendingNextPage: Boolean = false,
    val errorKind: DiscoveryFeedErrorKind? = null,
    val errorServerCode: Int? = null,
) {
    val canLoadMorePages: Boolean
        get() = loadedPage > 0 && loadedPage < totalPages && !isInitialLoading && !isRefreshing && !isAppendingNextPage
}

/**
 * Saved scroll anchor for an individual `NovelSortedBy` feed (`周榜`, `日榜`, `月榜`, `总榜`, etc.)
 * so switching between rankings preserves each list's own scroll progress without cross-anchoring
 * overlapping novel `aid`s.
 */
data class FeedScrollPosition(
    val firstVisibleItemIndex: Int = 0,
    val firstVisibleItemScrollOffset: Int = 0,
)

/**
 * Search mode selected in `UnifiedSearchScreen`.
 */
enum class UnifiedSearchMode {
    /** `按小说标题` (`action=search&searchtype=articlename`). */
    NOVEL_TITLE,

    /** `按作者名称` (`action=search&searchtype=author`). */
    AUTHOR_NAME,

    /** `AID 编号直达` (`do=meta` direct lookup — zero `5s` search cooldown!). */
    DIRECT_AID;

    fun toOnlineSearchFieldOrNull(): OnlineSearchField? = when (this) {
        NOVEL_TITLE -> OnlineSearchField.NOVEL_TITLE
        AUTHOR_NAME -> OnlineSearchField.AUTHOR_NAME
        DIRECT_AID -> null
    }
}

/**
 * Observable state of the online search engine, including the auto-queued `5s` server cooldown.
 */
data class OnlineSearchExecutionState(
    val submittedQuery: String = "",
    val mode: UnifiedSearchMode = UnifiedSearchMode.NOVEL_TITLE,
    val isQueuedForCooldown: Boolean = false,
    val remainingCooldownMillis: Long = 0L,
    val totalCooldownMillis: Long = 5_000L,
    val isSearchingOnline: Boolean = false,
    val results: List<DiscoveryNovelCardItem> = emptyList(),
    val hasCompletedSearch: Boolean = false,
    val errorKind: DiscoveryFeedErrorKind? = null,
    val errorServerCode: Int? = null,
)
