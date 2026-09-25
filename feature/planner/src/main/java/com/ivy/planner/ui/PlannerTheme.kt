package com.ivy.planner.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * Figtree: a round, clean geometric typeface (SIL Open Font License),
 * used across the planner screens.
 */
val Figtree = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold),
    Font(R.font.figtree_extrabold, FontWeight.ExtraBold),
)

private fun TextStyle.figtree() = copy(fontFamily = Figtree)

/** Keeps the app's colours (light/dark) but swaps the typeface to Figtree. */
@Composable
fun PlannerTheme(content: @Composable () -> Unit) {
    val base = MaterialTheme.typography
    val typography = remember(base) {
        Typography(
            displayLarge = base.displayLarge.figtree(),
            displayMedium = base.displayMedium.figtree(),
            displaySmall = base.displaySmall.figtree(),
            headlineLarge = base.headlineLarge.figtree(),
            headlineMedium = base.headlineMedium.figtree(),
            headlineSmall = base.headlineSmall.figtree(),
            titleLarge = base.titleLarge.figtree(),
            titleMedium = base.titleMedium.figtree(),
            titleSmall = base.titleSmall.figtree(),
            bodyLarge = base.bodyLarge.figtree(),
            bodyMedium = base.bodyMedium.figtree(),
            bodySmall = base.bodySmall.figtree(),
            labelLarge = base.labelLarge.figtree(),
            labelMedium = base.labelMedium.figtree(),
            labelSmall = base.labelSmall.figtree(),
        )
    }
    val base0 = MaterialTheme.colorScheme
    val dark = base0.background.luminance() < 0.5f
    val colors = remember(base0, dark) {
        if (dark) {
            // Taskito-like soft black with gentle, low-contrast surfaces
            base0.copy(
                background = Color(0xFF1B1D1B),
                surface = Color(0xFF1B1D1B),
                surfaceVariant = Color(0xFF252925),
                surfaceContainer = Color(0xFF252925),
                surfaceContainerHigh = Color(0xFF2B302B),
                surfaceContainerLow = Color(0xFF202320),
                onBackground = Color(0xFFE4E6E1),
                onSurface = Color(0xFFE4E6E1),
                onSurfaceVariant = Color(0xFFA3A9A1),
                outline = Color(0xFF5A605A),
                outlineVariant = Color(0xFF3A403A),
                primary = PlannerColors.Done,
                onPrimary = PlannerColors.OnDone,
            )
        } else {
            base0.copy(
                background = Color(0xFFF7F6F2),
                surface = Color(0xFFF7F6F2),
                surfaceVariant = Color(0xFFECEBE5),
                primary = Color(0xFF3E8E5B),
                onPrimary = Color.White,
            )
        }
    }
    MaterialTheme(
        colorScheme = colors,
        shapes = MaterialTheme.shapes,
        typography = typography,
        content = content,
    )
}
