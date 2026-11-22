package com.snapfree.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.composed
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage

/** Rounded thumbnail with a neutral placeholder while loading or when the image fails. */
@Composable
fun Thumbnail(url: String?, modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.small) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { ShimmerBox(Modifier.fillMaxSize()) },
                error = { PlaceholderIcon() },
            )
        } else {
            PlaceholderIcon()
        }
    }
}

@Composable
private fun PlaceholderIcon() {
    Icon(
        Icons.Outlined.Image,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
    )
}

/** Pulsing placeholder used for loading states. */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.small) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "shimmerAlpha",
    )
    Box(
        modifier
            .clip(shape)
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** Rounded container used to group related rows. */
@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(content = content)
    }
}

/** Round icon badge shown at the start of rows. */
@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "empty")
    val scale by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "emptyScale",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.size(20.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.size(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Small rounded pill used for metadata such as duration or source. */
@Composable
fun MetaPill(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Selectable row with a radio-like check used in option lists. */
@Composable
fun SelectableRow(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val container = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val animatedContainer by androidx.compose.animation.animateColorAsState(container, label = "rowColor")
    val animatedContent by androidx.compose.animation.animateColorAsState(contentColor, label = "rowContent")
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = animatedContainer,
        contentColor = animatedContent,
    ) {
        Row(Modifier.padding(contentPadding), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

@Composable
fun HSpace(width: Int) = Spacer(Modifier.width(width.dp))

/**
 * Fades and lifts [content] into place once, [delayMillis] after it first appears.
 * Giving consecutive blocks growing delays makes them arrive one after another.
 */
@Composable
fun Reveal(delayMillis: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val progress = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delayMillis.toLong())
        progress.animateTo(
            1f,
            androidx.compose.animation.core.spring(dampingRatio = 0.82f, stiffness = androidx.compose.animation.core.Spring.StiffnessLow),
        )
    }
    Box(
        modifier.graphicsLayer {
            val p = progress.value
            alpha = p.coerceIn(0f, 1f)
            translationY = (1f - p) * 28.dp.toPx()
            val scale = 0.97f + 0.03f * p
            scaleX = scale
            scaleY = scale
        }
    ) { content() }
}

/** Shrinks the element slightly while [interactionSource] is pressed, for a tactile feel. */
fun Modifier.pressScale(interactionSource: androidx.compose.foundation.interaction.InteractionSource): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 0.96f else 1f,
        androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium),
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
