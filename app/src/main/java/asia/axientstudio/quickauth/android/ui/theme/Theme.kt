package asia.axientstudio.quickauth.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkColorScheme = darkColorScheme(
    background         = QA_Background_Dark,
    surface            = QA_Surface_Dark,
    surfaceVariant     = QA_SurfaceVariant_Dark,
    outline            = QA_Border_Dark,
    primary            = QA_Accent,
    onPrimary          = QA_OnAccent,
    primaryContainer   = QA_AccentContainer,
    onPrimaryContainer = QA_OnAccent,
    secondary          = QA_Code_Dark,
    onSecondary        = QA_OnAccent,
    onBackground       = QA_TextPrimary_Dark,
    onSurface          = QA_TextPrimary_Dark,
    onSurfaceVariant   = QA_TextSecondary_Dark,
    error              = QA_Danger,
    onError            = QA_OnAccent,
    scrim              = QA_Background_Dark,
)

private val LightColorScheme = lightColorScheme(
    background         = QA_Background_Light,
    surface            = QA_Surface_Light,
    surfaceVariant     = QA_SurfaceVariant_Light,
    outline            = QA_Border_Light,
    primary            = QA_Accent,
    onPrimary          = QA_OnAccent,
    primaryContainer   = QA_AccentContainer,
    onPrimaryContainer = QA_OnAccent,
    secondary          = QA_Code_Light,
    onSecondary        = QA_OnAccent,
    onBackground       = QA_TextPrimary_Light,
    onSurface          = QA_TextPrimary_Light,
    onSurfaceVariant   = QA_TextSecondary_Light,
    error              = QA_Danger,
    onError            = QA_OnAccent,
    scrim              = QA_Background_Dark,
)

// Typography — minimal customisation; the monospace treatment for TOTP codes
// is applied at the call site (AccountCard) rather than baked into the theme,
// since it applies to one specific Text element rather than an entire role.
val QATypography = Typography(
    // Used for section eyebrows / labels in Settings
    labelSmall = TextStyle(
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.SemiBold,
        fontSize    = 10.sp,
        lineHeight  = 14.sp,
        letterSpacing = 1.2.sp,
    ),
    // Used for account names inside cards
    labelMedium = TextStyle(
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.Medium,
        fontSize    = 13.sp,
        lineHeight  = 18.sp,
        letterSpacing = 0.1.sp,
    ),
    // Used for body text in Settings
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize   = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize   = 12.sp,
        lineHeight = 16.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 15.sp,
        lineHeight = 22.sp,
    ),
)

@Composable
fun QuickAuthTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography  = QATypography,
        content     = content
    )
}
