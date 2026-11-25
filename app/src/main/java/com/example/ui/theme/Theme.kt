package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

enum class ThemeMode {
    SOCIAL_CLARO,
    SOCIAL_OSCURO,
    PERGAMINO,
    MINIMALISTA,
    VIGILIA
}

private val SocialLightColorScheme = lightColorScheme(
    primary = SocialPrimaryLight,
    onPrimary = SocialOnPrimaryLight,
    secondary = SocialSecondaryLight,
    tertiary = SocialTertiaryLight,
    background = SocialBackgroundLight,
    surface = SocialSurfaceLight,
    onBackground = SocialOnSurfaceLight,
    onSurface = SocialOnSurfaceLight,
    outline = SocialOutlineLight
)

private val SocialDarkColorScheme = darkColorScheme(
    primary = SocialPrimaryDark,
    onPrimary = SocialOnPrimaryDark,
    secondary = SocialSecondaryDark,
    tertiary = SocialTertiaryDark,
    background = SocialBackgroundDark,
    surface = SocialSurfaceDark,
    onBackground = SocialOnSurfaceDark,
    onSurface = SocialOnSurfaceDark,
    outline = SocialOutlineDark
)

private val PergaminoColorScheme = lightColorScheme(
    primary = PrimaryPergamino,
    onPrimary = OnPrimaryPergamino,
    secondary = SecondaryPergamino,
    background = BackgroundPergamino,
    surface = SurfacePergamino,
    onBackground = OnSurfacePergamino,
    onSurface = OnSurfacePergamino,
    outline = OutlinePergamino
)

private val MinimalistaColorScheme = lightColorScheme(
    primary = PrimaryMinimalista,
    onPrimary = OnPrimaryMinimalista,
    secondary = SecondaryMinimalista,
    background = BackgroundMinimalista,
    surface = SurfaceMinimalista,
    onBackground = OnSurfaceMinimalista,
    onSurface = OnSurfaceMinimalista,
    outline = OutlineMinimalista
)

private val VigiliaColorScheme = darkColorScheme(
    primary = PrimaryVigilia,
    onPrimary = OnPrimaryVigilia,
    secondary = SecondaryVigilia,
    background = BackgroundVigilia,
    surface = SurfaceVigilia,
    onBackground = OnSurfaceVigilia,
    onSurface = OnSurfaceVigilia,
    outline = OutlineVigilia
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
