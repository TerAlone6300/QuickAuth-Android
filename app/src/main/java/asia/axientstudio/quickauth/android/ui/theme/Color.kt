package asia.axientstudio.quickauth.android.ui.theme

import androidx.compose.ui.graphics.Color

// ── Dark palette ───────────────────────────────────────────────────────────
// Deep navy-black backgrounds — not pure #000, which reads harsh on OLED and
// does not carry the "precision instrument" feel we are going for.
val QA_Background_Dark    = Color(0xFF0C0E14)
val QA_Surface_Dark       = Color(0xFF131620)
val QA_SurfaceVariant_Dark = Color(0xFF1A1D28)
val QA_Border_Dark        = Color(0xFF232638)
val QA_TextPrimary_Dark   = Color(0xFFE8EAF2)
val QA_TextSecondary_Dark = Color(0xFF565A7A)
// Indigo accent — sits between developer-purple and corporate-blue, the right
// register for a security tool; avoids the cliché acid-green hacker palette.
val QA_Accent             = Color(0xFF5B6CED)
val QA_AccentContainer    = Color(0xFF3D4599)
// Soft lavender for the TOTP code itself — visually distinct from normal text
// without adding a contrasting hue that would fight the overall palette.
val QA_Code_Dark          = Color(0xFF818CF8)
val QA_TimerTrack_Dark    = Color(0xFF1E2135)
val QA_Danger             = Color(0xFFE5484D)
val QA_OnAccent           = Color(0xFFFFFFFF)

// ── Light palette ──────────────────────────────────────────────────────────
val QA_Background_Light    = Color(0xFFF5F6FA)
val QA_Surface_Light       = Color(0xFFFFFFFF)
val QA_SurfaceVariant_Light = Color(0xFFECEEF5)
val QA_Border_Light        = Color(0xFFD4D7E5)
val QA_TextPrimary_Light   = Color(0xFF0C0E14)
val QA_TextSecondary_Light = Color(0xFF565A7A)
val QA_Code_Light          = Color(0xFF4A5BE8)
val QA_TimerTrack_Light    = Color(0xFFD4D7E5)
