package org.mewx.wenku8.ui.components

import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import org.mewx.wenku8.ui.theme.LocalEinkMode
import org.mewx.wenku8.ui.theme.einkHairlineBorder

/**
 * Wraps an action control (such as a top-bar button, split-button dropdown trigger, or floating
 * toolbar icon) in a Material 3 [TooltipBox] + [PlainTooltip] so long-pressing or hovering shows
 * a concise action label — matching and improving upon classic Android ActionBar tooltips.
 */
@Composable
fun ActionTooltipBox(
    tooltipText: String,
    modifier: Modifier = Modifier,
    anchorPosition: TooltipAnchorPosition = TooltipAnchorPosition.Below,
    content: @Composable () -> Unit,
) {
    if (tooltipText.isBlank()) {
        content()
        return
    }

    val tooltipState = rememberTooltipState()
    val hapticFeedback = LocalHapticFeedback.current
    val isEinkMode = LocalEinkMode.current
    var wasVisible by remember { mutableStateOf(false) }

    LaunchedEffect(tooltipState.isVisible) {
        if (tooltipState.isVisible && !wasVisible && !isEinkMode) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        wasVisible = tooltipState.isVisible
    }

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            positioning = anchorPosition,
        ),
        tooltip = {
            PlainTooltip(
                modifier = Modifier.einkHairlineBorder(TooltipDefaults.plainTooltipContainerShape),
            ) {
                Text(text = tooltipText)
            }
        },
        state = tooltipState,
        modifier = modifier,
    ) {
        content()
    }
}

/**
 * Drop-in [IconButton] wrapper that displays [tooltipText] in a [PlainTooltip] on long-press
 * without firing [onClick], while keeping [modifier] (such as `testTag` and sizing) directly on
 * the underlying [IconButton] so semantics and enabled states remain intact.
 */
@Composable
fun TooltipIconButton(
    tooltipText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    anchorPosition: TooltipAnchorPosition = TooltipAnchorPosition.Below,
    content: @Composable () -> Unit,
) {
    ActionTooltipBox(
        tooltipText = tooltipText,
        anchorPosition = anchorPosition,
    ) {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            content = content,
        )
    }
}
