package com.simpletools.area.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onFirst
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simpletools.area.core.settings.ThemeMode
import com.simpletools.area.feature.calculator.CalculatorScreen
import com.simpletools.area.feature.home.HomeScreen
import com.simpletools.area.ui.theme.AreaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Écrans sans carte, rendus à la taille d'un Galaxy A14 (360×800 dp, xhdpi ≈ 2× ). Écran Compose, locale par défaut = fr. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "fr-w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenSmokeTest {
    @get:Rule val rule = androidx.compose.ui.test.junit4.createAndroidComposeRule<androidx.activity.ComponentActivity>()

    /** Rendu direct de la fenêtre dans un PNG (inspection visuelle ; best effort, n'influe pas sur le résultat du test). */
    private fun shot(name: String) {
        runCatching {
            rule.waitForIdle()
            val v = rule.activity.window.decorView
            val bmp = android.graphics.Bitmap.createBitmap(720, 1600, android.graphics.Bitmap.Config.ARGB_8888)
            v.measure(android.view.View.MeasureSpec.makeMeasureSpec(720, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(1600, android.view.View.MeasureSpec.EXACTLY))
            v.layout(0, 0, 720, 1600)
            v.draw(android.graphics.Canvas(bmp))
            File("build/screens/$name.png").also { it.parentFile?.mkdirs() }.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun homeShowsFiveEntryPointsAndDisclaimer() {
        var clicked = ""
        rule.setContent { AreaTheme(ThemeMode.LIGHT) { HomeScreen({ clicked = "map" }, { clicked = "gps" }, { clicked = "calc" }, { clicked = "dist" }, { clicked = "lib" }, { clicked = "set" }) } }
        for (t in listOf("Mesurer sur carte", "Mesurer avec GPS", "Calcul manuel", "Mesurer une distance", "Mes mesures")) rule.onNodeWithText(t).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Mesure indicative. Ne remplace pas un relevé effectué par un géomètre-expert.").performScrollTo().assertIsDisplayed()
        shot("home-light")
        rule.onNodeWithText("Mesurer avec GPS").performClick()
        org.junit.Assert.assertEquals("gps", clicked)
    }

    @Test fun homeDark() {
        rule.setContent { AreaTheme(ThemeMode.DARK) { HomeScreen({}, {}, {}, {}, {}, {}) } }
        shot("home-dark")
    }

    @Test fun calculatorComputesRectangleInUi() {
        rule.setContent { AreaTheme(ThemeMode.LIGHT) { CalculatorScreen({}) } }
        rule.onNodeWithText("Saisissez les dimensions pour obtenir la surface.").assertIsDisplayed()
        rule.onNodeWithText("Longueur (m)").performScrollTo().performTextInput("125")
        rule.onNodeWithText("Largeur (m)").performScrollTo().performTextInput("100")
        rule.onNodeWithText("Surface").performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText("1,25 ha", substring = true).onFirst().performScrollTo().assertIsDisplayed()
        shot("calculator")
    }
}
