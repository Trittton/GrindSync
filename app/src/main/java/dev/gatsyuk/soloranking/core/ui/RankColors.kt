package dev.gatsyuk.soloranking.core.ui

import androidx.compose.ui.graphics.Color
import dev.gatsyuk.soloranking.core.model.Rank

/**
 * Rank hue scale — one hue per letter family (the −/+ modifiers share it).
 * NEVER use color alone — always pair with the letter label (SPEC §7.7).
 */
fun rankColor(rank: Rank?): Color = when (rank) {
    null -> Color(0xFF3A414C) // UNRANKED — rendered as dimmed E− + "no data" text
    Rank.E_MINUS, Rank.E, Rank.E_PLUS -> Color(0xFF5B6470)
    Rank.D_MINUS, Rank.D, Rank.D_PLUS -> Color(0xFF8A93A1)
    Rank.C_MINUS, Rank.C, Rank.C_PLUS -> Color(0xFF5F93BD)
    Rank.B_MINUS, Rank.B, Rank.B_PLUS -> Color(0xFF4DA3F0)
    Rank.A_MINUS, Rank.A, Rank.A_PLUS -> Color(0xFFE39A3C)
    Rank.S_MINUS, Rank.S, Rank.S_PLUS -> Color(0xFFE0654A)
    Rank.SS_MINUS, Rank.SS, Rank.SS_PLUS -> Color(0xFFD93D6B)
    Rank.SSS_MINUS, Rank.SSS, Rank.SSS_PLUS -> Color(0xFFE8B93C)
}

/** Unranked renders as the floor of the ladder, with explanatory text beside it. */
fun rankLabel(rank: Rank?): String = rank?.label ?: Rank.E_MINUS.label
