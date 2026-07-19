package dev.gatsyuk.soloranking.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gatsyuk.soloranking.core.model.Rank
import dev.gatsyuk.soloranking.core.ui.theme.LocalSystemColors

/**
 * The "System window" design language (Solo Leveling direction): panels with a
 * blue→violet gradient hairline, a faint top glow on hero surfaces, cut-corner
 * rank glyphs, and gradient progress. These are the only building blocks —
 * screens compose them instead of styling raw Cards.
 */

/** Panel with gradient hairline border; [glow] adds the hero top-glow. */
@Composable
fun SystemPanel(
    modifier: Modifier = Modifier,
    glow: Boolean = false,
    contentPadding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sys = LocalSystemColors.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(sys.panelFill)
            .then(
                if (glow && sys.glowAlpha > 0f) {
                    Modifier.drawBehind {
                        drawRect(
                            Brush.radialGradient(
                                colors = listOf(
                                    sys.accent.copy(alpha = sys.glowAlpha),
                                    Color.Transparent,
                                ),
                                center = Offset(size.width / 2f, 0f),
                                radius = (size.width * 0.9f).coerceAtLeast(1f),
                            ),
                        )
                    }
                } else {
                    Modifier
                },
            )
            .border(
                1.dp,
                Brush.verticalGradient(listOf(sys.panelBorderTop, sys.panelBorderBottom)),
                shape,
            )
            .padding(contentPadding),
        content = content,
    )
}

/** Accent overline label used inside panels ("OVERALL RANK", "ALL TIME"…). */
@Composable
fun PanelLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
        color = LocalSystemColors.current.accent,
        modifier = modifier,
    )
}

/** Section header between panels: gradient tick + spaced uppercase label. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    val sys = LocalSystemColors.current
    Row(
        modifier.padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Brush.verticalGradient(listOf(sys.accent, sys.accentAlt))),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Rank glyph: cut-corner frame in the rank hue with a soft glow. `rank = null`
 * renders a neutral "—" with NO rank color — unranked must never read as a
 * rank (SPEC §7.7).
 */
@Composable
fun RankBadge(rank: Rank?, size: Dp, modifier: Modifier = Modifier) {
    val sys = LocalSystemColors.current
    val color = if (rank != null) rankColor(rank) else MaterialTheme.colorScheme.outline
    val shape = CutCornerShape(size * 0.22f)
    Box(
        modifier = modifier
            .size(size)
            .then(
                if (rank != null && sys.glowAlpha > 0f) {
                    Modifier.drawBehind {
                        drawCircle(
                            Brush.radialGradient(
                                listOf(color.copy(alpha = 0.35f), Color.Transparent),
                            ),
                            radius = this.size.minDimension * 0.75f,
                        )
                    }
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(color.copy(alpha = if (rank != null) 0.16f else 0.06f))
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(color.copy(alpha = 0.9f), color.copy(alpha = 0.25f)),
                ),
                shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val label = rank?.label ?: "—"
        Text(
            label,
            color = if (rank != null) color else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Black,
            fontSize = when {
                label.length > 3 -> (size.value * 0.24f).sp
                label.length > 2 -> (size.value * 0.30f).sp
                else -> (size.value * 0.42f).sp
            },
            letterSpacing = 1.sp,
        )
    }
}

/** Animated XP/progress bar with the signature blue→violet gradient fill. */
@Composable
fun GlowProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
) {
    val sys = LocalSystemColors.current
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(700),
        label = "progress",
    )
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (animated > 0f) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated.coerceAtLeast(0.02f))
                    .clip(RoundedCornerShape(height / 2))
                    .background(Brush.horizontalGradient(listOf(sys.accent, sys.accentAlt))),
            )
        }
    }
}

/** Big tabular-numeral value over a spaced uppercase caption. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Black,
                fontFeatureSettings = "tnum",
            ),
            color = valueColor,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
