package dev.gatsyuk.grindsync.core.ui

import androidx.compose.ui.graphics.Color
import dev.gatsyuk.grindsync.core.model.Rank

/**
 * Rank hue scale (from nav-schematic.html). NEVER use color alone —
 * always pair with the letter label (colorblind safety, SPEC §7.7).
 */
fun rankColor(rank: Rank?): Color = when (rank) {
    Rank.S -> Color(0xFFE0654A)
    Rank.A -> Color(0xFFE39A3C)
    Rank.B -> Color(0xFF4DA3F0)
    Rank.C -> Color(0xFF5F93BD)
    Rank.D -> Color(0xFF8A93A1)
    Rank.E -> Color(0xFF5B6470)
    null -> Color(0xFF3A414C) // UNRANKED — "not trained", distinct from E
}

fun rankLabel(rank: Rank?): String = rank?.name ?: "–"
