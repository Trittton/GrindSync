package dev.gatsyuk.grindsync.core.model

enum class SetKind { WARMUP, WORKING, DROPSET }

/** How a routine prefills targets when a workout is started from it. */
enum class TargetMode { LATEST, FIXED }

/**
 * Display-only unit preference. All stored weights are canonical kg;
 * conversion happens exclusively at render time (SPEC §12.1).
 */
enum class WeightUnit { KG, LB }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

const val KG_PER_LB = 0.45359237
