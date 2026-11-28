package com.example.ui

import com.example.ui.theme.ThemeMode
import com.example.ui.theme.socialThemeForWorld1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ThemeAndTruthOrDareFallbackTest {
    @Test
    fun convertsVigiliaToSocialDarkWhenReturningToWorld1() {
        assertEquals(ThemeMode.SOCIAL_OSCURO, socialThemeForWorld1(ThemeMode.VIGILIA))
    }

    @Test
    fun preservesSocialThemeConversions() {
        assertEquals(ThemeMode.SOCIAL_OSCURO, socialThemeForWorld1(ThemeMode.SOCIAL_OSCURO))
        assertEquals(ThemeMode.SOCIAL_CLARO, socialThemeForWorld1(ThemeMode.SOCIAL_CLARO))
    }

    @Test
    fun convertsOtherWorld2ThemesToSocialLight() {
        assertEquals(ThemeMode.SOCIAL_CLARO, socialThemeForWorld1(ThemeMode.PERGAMINO))
        assertEquals(ThemeMode.SOCIAL_CLARO, socialThemeForWorld1(ThemeMode.MINIMALISTA))
    }

    @Test
    fun fallbackUsesLocalChallengeWithoutClaimingGeminiGeneratedIt() {
        val fallback = localTruthOrDareFallback(listOf("Haz una voltereta"))

        assertEquals("🔥 RETO:\n\nHaz una voltereta", fallback)
        assertFalse(fallback.contains("IA"))
    }
}
