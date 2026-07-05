package online.k73.bmwlauncher.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/** Full tile press feedback: scale 0.94 + haptic pulse + amber border 1→2dp + fill surface→surfaceHi. [focused] forces the amber-2dp highlighted look. */
@Composable
fun Modifier.pressable(shape: Shape, focused: Boolean = false, onClick: () -> Unit): Modifier {
    val c = LocalLauncherColors.current
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, animationSpec = if (pressed) tween(90) else spring(0.6f, 500f), label = "pressScale")
    val fill = if (pressed || focused) c.surfaceHi else c.tile
    val borderW = if (focused) 2.dp else if (pressed) 2.dp else 1.dp
    val borderC = if (focused || pressed) c.accent else c.hairline
    return this
        .scale(scale)
        .clip(shape)
        .background(fill, shape)
        .border(borderW, borderC, shape)
        .clickable(interactionSource = interaction, indication = null) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        }
}

/** Lighter feedback for icon-only controls: 0.94 press scale + haptic pulse, no fill/border. */
@Composable
fun Modifier.pressScale(onClick: () -> Unit): Modifier {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, animationSpec = if (pressed) tween(90) else spring(0.6f, 500f), label = "pressScaleIcon")
    return this.scale(scale).clickable(interactionSource = interaction, indication = null) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onClick()
    }
}
