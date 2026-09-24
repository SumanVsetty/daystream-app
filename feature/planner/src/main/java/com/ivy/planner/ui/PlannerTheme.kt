package com.ivy.planner.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        shapes = MaterialTheme.shapes,
        typography = typography,
        content = content,
    )
}
