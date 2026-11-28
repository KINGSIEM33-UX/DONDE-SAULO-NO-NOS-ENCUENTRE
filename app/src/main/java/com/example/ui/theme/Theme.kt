package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class ThemeMode {
    SOCIAL_CLARO,
    SOCIAL_OSCURO,
    PERGAMINO,
    MINIMALISTA,
    VIGILIA
}

fun socialThemeForWorld1(themeMode: ThemeMode): ThemeMode = when (themeMode) {
    ThemeMode.SOCIAL_OSCURO, ThemeMode.VIGILIA -> ThemeMode.SOCIAL_OSCURO
    else -> ThemeMode.SOCIAL_CLARO
}

private val SocialLightColorScheme = lightColorScheme(
    primary = SocialPrimaryLight,
    onPrimary = SocialOnPrimaryLight,
    primaryContainer = SocialPrimaryContainerLight,
    onPrimaryContainer = SocialOnPrimaryContainerLight,
    secondary = SocialSecondaryLight,
    onSecondary = SocialOnSecondaryLight,
    secondaryContainer = SocialSecondaryContainerLight,
    onSecondaryContainer = SocialOnSecondaryContainerLight,
    tertiary = SocialTertiaryLight,
    onTertiary = SocialOnTertiaryLight,
    tertiaryContainer = SocialTertiaryContainerLight,
    onTertiaryContainer = SocialOnTertiaryContainerLight,
    background = SocialBackgroundLight,
    onBackground = SocialOnSurfaceLight,
    surface = SocialSurfaceLight,
    onSurface = SocialOnSurfaceLight,
    surfaceVariant = SocialSurfaceVariantLight,
    onSurfaceVariant = SocialOnSurfaceVariantLight,
    outline = SocialOutlineLight,
    outlineVariant = SocialOutlineVariantLight,
    error = StatusCancelledRed,
    onError = Color.White,
    errorContainer = StatusCancelledBg,
    onErrorContainer = Color(0xFF6E1A1A)
)

private val SocialDarkColorScheme = darkColorScheme(
    primary = SocialPrimaryDark,
    onPrimary = SocialOnPrimaryDark,
    primaryContainer = SocialPrimaryContainerDark,
    onPrimaryContainer = SocialOnPrimaryContainerDark,
    secondary = SocialSecondaryDark,
    onSecondary = SocialOnSecondaryDark,
    secondaryContainer = SocialSecondaryContainerDark,
    onSecondaryContainer = SocialOnSecondaryContainerDark,
    tertiary = SocialTertiaryDark,
    onTertiary = SocialOnTertiaryDark,
    tertiaryContainer = SocialTertiaryContainerDark,
    onTertiaryContainer = SocialOnTertiaryContainerDark,
    background = SocialBackgroundDark,
    onBackground = SocialOnSurfaceDark,
    surface = SocialSurfaceDark,
    onSurface = SocialOnSurfaceDark,
    surfaceVariant = SocialSurfaceVariantDark,
    onSurfaceVariant = SocialOnSurfaceVariantDark,
    outline = SocialOutlineDark,
    outlineVariant = SocialOutlineVariantDark,
    error = Color(0xFFE06C6C),
    onError = Color(0xFF1A0707),
    errorContainer = Color(0xFF2D1414),
    onErrorContainer = Color(0xFFF5B8B8)
)

private val PergaminoColorScheme = lightColorScheme(
    primary = PrimaryPergamino,
    onPrimary = OnPrimaryPergamino,
    primaryContainer = SurfaceVariantPergamino,
    onPrimaryContainer = PrimaryPergamino,
    secondary = SecondaryPergamino,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E9D4),
    onSecondaryContainer = Color(0xFF5A3F14),
    tertiary = StatusConfirmedGreen,
    onTertiary = Color.White,
    background = BackgroundPergamino,
    onBackground = OnSurfacePergamino,
    surface = SurfacePergamino,
    onSurface = OnSurfacePergamino,
    surfaceVariant = SurfaceVariantPergamino,
    onSurfaceVariant = OnSurfaceVariantPergamino,
    outline = OutlinePergamino,
    outlineVariant = Color(0xFFE7DFCF)
)

private val MinimalistaColorScheme = lightColorScheme(
    primary = PrimaryMinimalista,
    onPrimary = OnPrimaryMinimalista,
    primaryContainer = SurfaceVariantMinimalista,
    onPrimaryContainer = PrimaryMinimalista,
    secondary = SecondaryMinimalista,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6F2EC),
    onSecondaryContainer = Color(0xFF164A34),
    tertiary = Color(0xFFB84A39),
    onTertiary = Color.White,
    background = BackgroundMinimalista,
    onBackground = OnSurfaceMinimalista,
    surface = SurfaceMinimalista,
    onSurface = OnSurfaceMinimalista,
    surfaceVariant = SurfaceVariantMinimalista,
    onSurfaceVariant = OnSurfaceVariantMinimalista,
    outline = OutlineMinimalista,
    outlineVariant = Color(0xFFECECE8)
)

private val VigiliaColorScheme = darkColorScheme(
    primary = PrimaryVigilia,
    onPrimary = OnPrimaryVigilia,
    primaryContainer = Color(0xFF23201B),
    onPrimaryContainer = PrimaryVigilia,
    secondary = SecondaryVigilia,
    onSecondary = OnPrimaryVigilia,
    secondaryContainer = Color(0xFF1F1D19),
    onSecondaryContainer = Color(0xFFE8D7BA),
    tertiary = SocialTertiaryDark,
    onTertiary = SocialOnTertiaryDark,
    background = BackgroundVigilia,
    onBackground = OnSurfaceVigilia,
    surface = SurfaceVigilia,
    onSurface = OnSurfaceVigilia,
    surfaceVariant = SurfaceVariantVigilia,
    onSurfaceVariant = OnSurfaceVariantVigilia,
    outline = OutlineVigilia,
    outlineVariant = Color(0xFF1E1E22)
)

@Composable
fun MyApplicationTheme(
    themeMode: ThemeMode = ThemeMode.SOCIAL_CLARO,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        ThemeMode.SOCIAL_CLARO -> SocialLightColorScheme
        ThemeMode.SOCIAL_OSCURO -> SocialDarkColorScheme
        ThemeMode.PERGAMINO -> PergaminoColorScheme
        ThemeMode.MINIMALISTA -> MinimalistaColorScheme
        ThemeMode.VIGILIA -> VigiliaColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
