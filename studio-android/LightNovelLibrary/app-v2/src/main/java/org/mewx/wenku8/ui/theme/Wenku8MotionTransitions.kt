package org.mewx.wenku8.ui.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.ResizeMode
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.IntOffset

/**
 * Shared-element & screen-transition motion engine for Wenku8 `:app-v2` (Material 3 Expressive).
 *
 * Replaces `v1.31`'s `ActivityOptionsCompat.makeSceneTransitionAnimation` (`novel_cover`,
 * `novel_title`) and `overridePendingTransition(R.anim.fade_in, R.anim.hold)` with physics-driven
 * Compose `SharedTransitionLayout` + `MotionScheme` springs:
 *
 * 1. **Searchbox Morphing**: The persistent search pill on `最新` / `排行` and the local/online
 *    search container on `书架` seamlessly morph their bounds and shape into `UnifiedSearchScreen`'s
 *    `OutlinedTextField` on open and collapse smoothly back on return.
 * 2. **Novel Cover & Title Morphing**: Tapping a novel card in Discovery (`最新` / `排行`),
 *    `书架` (`继续阅读` carousel, Grid, or Detail List), `UnifiedSearchScreen`, or `ReadingFootprintScreen`
 *    morphs the 2:3 book cover and title directly into `NovelDetailScreen`'s editorial hero card,
 *    using unique origin-scoped keys so visible duplicate covers never clash.
 * 3. **Hierarchical Screen & Tab Transitions**: Full-screen sub-destinations (`NovelDetailScreen`,
 *    `UnifiedSearchScreen`, `ReadingFootprintScreen`, `ReviewListScreen`, `ReviewThreadScreen`,
 *    `UnifiedReaderScreen`, `NetworkDiagnosticsScreen`) use M3e `MotionScheme` spatial slide + fade
 *    transitions, while E-Ink mode (`LocalEinkMode.current == true`) automatically snaps in `0ms`
 *    to avoid ghosting on electrophoretic panels.
 */
object Wenku8SharedElementKeys {
    const val SEARCH_BAR_DEFAULT_ORIGIN = "discovery"
    const val SEARCH_BAR_BOOKSHELF_ORIGIN = "bookshelf"
    const val NOVEL_DEFAULT_ORIGIN = "feed"
    const val NOVEL_BOOKSHELF_CAROUSEL_ORIGIN = "shelf_carousel"
    const val NOVEL_BOOKSHELF_CARD_ORIGIN = "shelf_card"
    const val NOVEL_SEARCH_LOCAL_ORIGIN = "search_local"
    const val NOVEL_SEARCH_ONLINE_ORIGIN = "search_online"
    const val NOVEL_FOOTPRINT_HISTORY_ORIGIN = "footprint_history"

    fun searchBarContainerKey(origin: String): String = "shared_search_bar_$origin"
    fun novelCoverKey(origin: String, aid: Int): String = "shared_novel_cover_${origin}_$aid"
    fun novelTitleKey(origin: String, aid: Int): String = "shared_novel_title_${origin}_$aid"
}

/**
 * Carries the active [SharedTransitionScope] and [AnimatedVisibilityScope] down the composition
 * tree so leaf cards (`DiscoveryNovelCard`, `BookshelfGridItemCard`, `NovelDetailScreen`,
 * `PersistentSearchEntryPill`, `UnifiedSearchScreen`) remain clean and previews/unit tests work
 * seamlessly even without an enclosing `SharedTransitionLayout`.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
data class Wenku8SharedTransitionContext(
    val sharedTransitionScope: SharedTransitionScope,
    val animatedVisibilityScope: AnimatedVisibilityScope,
)

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalWenku8SharedTransition = compositionLocalOf<Wenku8SharedTransitionContext?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ProvideWenku8SharedTransition(
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope,
    content: @Composable () -> Unit,
) {
    val contextValue = sharedTransitionScope?.let { scope ->
        Wenku8SharedTransitionContext(
            sharedTransitionScope = scope,
            animatedVisibilityScope = animatedVisibilityScope,
        )
    }
    CompositionLocalProvider(
        LocalWenku8SharedTransition provides contextValue,
        content = content,
    )
}

/**
 * Applies `sharedBounds` morphing to the Searchbox pill / input container when inside an active
 * [Wenku8SharedTransitionContext] and not in E-Ink mode.
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Modifier.wenku8SharedSearchBarBounds(
    origin: String,
    clipShape: Shape,
    enabled: Boolean = true,
): Modifier {
    if (!enabled || LocalEinkMode.current) return this
    val sharedCtx = LocalWenku8SharedTransition.current ?: return this
    val boundsTransformSpec = rememberExpressiveBoundsSpec()
    return with(sharedCtx.sharedTransitionScope) {
        this@wenku8SharedSearchBarBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(
                key = Wenku8SharedElementKeys.searchBarContainerKey(origin),
            ),
            animatedVisibilityScope = sharedCtx.animatedVisibilityScope,
            boundsTransform = { _, _ -> boundsTransformSpec },
            enter = fadeIn(animationSpec = tween(durationMillis = 180)),
            exit = fadeOut(animationSpec = tween(durationMillis = 150)),
            resizeMode = ResizeMode.RemeasureToBounds,
            clipInOverlayDuringTransition = OverlayClip(clipShape),
        )
    }
}

/**
 * Applies `sharedElement` geometry morphing to a 2:3 Light Novel cover image container between
 * feed/shelf/search/history cards and `NovelDetailScreen`'s hero cover.
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Modifier.wenku8SharedNovelCover(
    origin: String,
    aid: Int,
    clipShape: Shape,
    enabled: Boolean = true,
): Modifier {
    if (!enabled || aid <= 0 || LocalEinkMode.current) return this
    val sharedCtx = LocalWenku8SharedTransition.current ?: return this
    val boundsTransformSpec = rememberExpressiveBoundsSpec()
    return with(sharedCtx.sharedTransitionScope) {
        this@wenku8SharedNovelCover.sharedElement(
            sharedContentState = rememberSharedContentState(
                key = Wenku8SharedElementKeys.novelCoverKey(origin, aid),
            ),
            animatedVisibilityScope = sharedCtx.animatedVisibilityScope,
            boundsTransform = { _, _ -> boundsTransformSpec },
            clipInOverlayDuringTransition = OverlayClip(clipShape),
        )
    }
}

/**
 * Applies `sharedBounds` morphing (`scaleToBounds` + crossfade) to a novel's title between list
 * cards and `NovelDetailScreen`'s hero title.
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Modifier.wenku8SharedNovelTitle(
    origin: String,
    aid: Int,
    enabled: Boolean = true,
): Modifier {
    if (!enabled || aid <= 0 || LocalEinkMode.current) return this
    val sharedCtx = LocalWenku8SharedTransition.current ?: return this
    val boundsTransformSpec = rememberExpressiveBoundsSpec()
    return with(sharedCtx.sharedTransitionScope) {
        this@wenku8SharedNovelTitle.sharedBounds(
            sharedContentState = rememberSharedContentState(
                key = Wenku8SharedElementKeys.novelTitleKey(origin, aid),
            ),
            animatedVisibilityScope = sharedCtx.animatedVisibilityScope,
            boundsTransform = { _, _ -> boundsTransformSpec },
            resizeMode = ResizeMode.scaleToBounds(),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun rememberExpressiveBoundsSpec(): FiniteAnimationSpec<Rect> {
    if (LocalEinkMode.current) return snap()
    // Material 3 Expressive spatial spring with zero-bounce precision for crisp cover & searchbox morphs.
    return spring(
        dampingRatio = 0.88f,
        stiffness = 380f,
    )
}

/**
 * Material 3 Expressive screen-level transition helper used by `AdaptiveMainScaffold` for both:
 * - Top-level tab switches (`书架 · 最新 · 排行 · 我的`) — directional subtle slide + fade.
 * - Hierarchical sub-screen pushes & pops (`NovelDetail`, `UnifiedSearch`, `Reviews`, `Reader`,
 *   `ReadingFootprint`, `NetworkDiagnostics`) — M3e forward/backward shared-axis X/Z motion.
 */
object Wenku8ScreenTransitions {

    fun topLevelTabTransform(
        initialOrdinal: Int,
        targetOrdinal: Int,
        isEinkMode: Boolean,
    ): ContentTransform {
        if (isEinkMode || initialOrdinal == targetOrdinal) {
            return EnterTransition.None togetherWith ExitTransition.None
        }
        val forward = targetOrdinal > initialOrdinal
        val offsetSign = if (forward) 1 else -1
        val slideSpec = spring<IntOffset>(dampingRatio = 0.92f, stiffness = 520f)
        return (
            fadeIn(animationSpec = tween(durationMillis = 190)) +
                androidx.compose.animation.slideInHorizontally(
                    animationSpec = slideSpec,
                    initialOffsetX = { fullWidth -> (fullWidth * 0.06f * offsetSign).toInt() },
                )
            ) togetherWith (
            fadeOut(animationSpec = tween(durationMillis = 130)) +
                androidx.compose.animation.slideOutHorizontally(
                    animationSpec = slideSpec,
                    targetOffsetX = { fullWidth -> -(fullWidth * 0.04f * offsetSign).toInt() },
                )
            )
    }

    fun <S> AnimatedContentTransitionScope<S>.hierarchicalScreenTransform(
        initialDepth: Int,
        targetDepth: Int,
        isEinkMode: Boolean,
        isSearchTransition: Boolean = false,
        isReaderTransition: Boolean = false,
    ): ContentTransform {
        if (isEinkMode) {
            return EnterTransition.None togetherWith ExitTransition.None
        }

        val pushingDeeper = targetDepth >= initialDepth
        val spatialIntOffsetSpec = spring<IntOffset>(dampingRatio = 0.9f, stiffness = 420f)
        val spatialScaleSpec = spring<Float>(dampingRatio = 0.9f, stiffness = 420f)

        if (isSearchTransition) {
            // Search studio expands seamlessly out of the shared search pill with subtle elevation fade.
            return if (pushingDeeper) {
                (
                    fadeIn(animationSpec = tween(durationMillis = 220)) +
                        scaleIn(initialScale = 0.985f, animationSpec = spatialScaleSpec)
                    ).togetherWith(
                    fadeOut(animationSpec = tween(durationMillis = 160)) +
                        scaleOut(targetScale = 0.985f, animationSpec = spatialScaleSpec),
                ).apply {
                    targetContentZIndex = targetDepth.toFloat()
                }
            } else {
                (
                    fadeIn(animationSpec = tween(durationMillis = 180)) +
                        scaleIn(initialScale = 0.985f, animationSpec = spatialScaleSpec)
                    ).togetherWith(
                    fadeOut(animationSpec = tween(durationMillis = 180)) +
                        scaleOut(targetScale = 0.985f, animationSpec = spatialScaleSpec),
                ).apply {
                    targetContentZIndex = targetDepth.toFloat()
                }
            }
        }

        if (isReaderTransition) {
            // Immersive reader opens like opening a book (clean container scale + crossfade, matching
            // and improving upon v1.31's R.anim.fade_in / R.anim.fade_out).
            return if (pushingDeeper) {
                (
                    fadeIn(animationSpec = tween(durationMillis = 240)) +
                        scaleIn(initialScale = 0.96f, animationSpec = spatialScaleSpec)
                    ).togetherWith(
                    fadeOut(animationSpec = tween(durationMillis = 180)) +
                        scaleOut(targetScale = 0.98f, animationSpec = spatialScaleSpec),
                ).apply {
                    targetContentZIndex = targetDepth.toFloat()
                }
            } else {
                (
                    fadeIn(animationSpec = tween(durationMillis = 200)) +
                        scaleIn(initialScale = 0.98f, animationSpec = spatialScaleSpec)
                    ).togetherWith(
                    fadeOut(animationSpec = tween(durationMillis = 200)) +
                        scaleOut(targetScale = 0.96f, animationSpec = spatialScaleSpec),
                ).apply {
                    targetContentZIndex = targetDepth.toFloat()
                }
            }
        }

        // Standard hierarchical push/pop (NovelDetail, Reviews, Footprint, NetworkDiagnostics):
        // Shared-Axis X slide + subtle depth scale so shared-element covers glide naturally on top.
        return if (pushingDeeper) {
            (
                fadeIn(animationSpec = tween(durationMillis = 220)) +
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = spatialIntOffsetSpec,
                        initialOffset = { fullWidth -> (fullWidth * 0.14f).toInt() },
                    )
                ).togetherWith(
                fadeOut(animationSpec = tween(durationMillis = 180)) +
                    scaleOut(targetScale = 0.965f, animationSpec = spatialScaleSpec),
            ).apply {
                targetContentZIndex = targetDepth.toFloat()
            }
        } else {
            (
                fadeIn(animationSpec = tween(durationMillis = 200)) +
                    scaleIn(initialScale = 0.965f, animationSpec = spatialScaleSpec)
                ).togetherWith(
                fadeOut(animationSpec = tween(durationMillis = 200)) +
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = spatialIntOffsetSpec,
                        targetOffset = { fullWidth -> (fullWidth * 0.14f).toInt() },
                    ),
            ).apply {
                targetContentZIndex = targetDepth.toFloat()
            }
        }
    }
}
