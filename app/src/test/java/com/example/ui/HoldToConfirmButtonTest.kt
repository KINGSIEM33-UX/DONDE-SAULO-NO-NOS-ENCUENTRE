package com.example.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HoldToConfirmButtonTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun accessibleActivation_requiresPreparationThenConfirmation() {
        var completions = 0
        val label = "Mantener para eliminar salida"
        val doneLabel = "Eliminando…"

        composeTestRule.setContent {
            MyApplicationTheme {
                HoldToConfirmButton(
                    label = label,
                    doneLabel = doneLabel,
                    onHoldComplete = { completions++ },
                    holdTimeMs = 10_000
                )
            }
        }

        val button = composeTestRule.onNode(hasText(label))
        button.assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.Role,
                Role.Button
            )
        )
        button.assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "Mantén pulsado para confirmar o pulsa dos veces: primero para preparar y después para confirmar."
            )
        )

        button.performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        assertEquals(0, completions)
        button.assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "Listo para confirmar; pulsa de nuevo para confirmar la acción."
            )
        )

        button.performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        assertEquals(1, completions)
        composeTestRule.onNode(hasText(doneLabel)).assertTextEquals(doneLabel)
    }
}
