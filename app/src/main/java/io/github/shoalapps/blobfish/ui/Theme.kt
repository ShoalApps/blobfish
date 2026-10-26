@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.shoalapps.blobfish.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import io.github.shoalapps.blobfish.data.Settings

// Fallback palette: Blobfish's colour, generated with Material's colour utilities.
// Used on Android 11 and below, or with dynamic colour turned off.
private val Light = lightColorScheme(
    primary = Color(0xFF005DB7), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E3FF), onPrimaryContainer = Color(0xFF00468C),
    secondary = Color(0xFF555F71), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDAE2F9), onSecondaryContainer = Color(0xFF3E4758),
    tertiary = Color(0xFF876B8C), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF876B8C), onTertiaryContainer = Color(0xFFFFFFFF),
    background = Color(0xFFF9F9FF), onBackground = Color(0xFF191C20),
    surface = Color(0xFFF9F9FF), onSurface = Color(0xFF191C20), onSurfaceVariant = Color(0xFF44474E),
    surfaceContainerLow = Color(0xFFF3F3FA), surfaceContainer = Color(0xFFEDEDF4),
    surfaceContainerHigh = Color(0xFFE7E8EE), surfaceContainerHighest = Color(0xFFE2E2E9),
    outline = Color(0xFF72747D), outlineVariant = Color(0xFFC4C6CF), inversePrimary = Color(0xFFA9C7FF),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA9C7FF), onPrimary = Color(0xFF003063),
    primaryContainer = Color(0xFF00468C), onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBDC7DC), onSecondary = Color(0xFF283141),
    secondaryContainer = Color(0xFF40495B), onSecondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFFDCBCE1), onTertiary = Color(0xFF3F2845),
    tertiaryContainer = Color(0xFFA487A9), onTertiaryContainer = Color(0xFF000000),
    background = Color(0xFF111318), onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318), onSurface = Color(0xFFE2E2E9), onSurfaceVariant = Color(0xFFC4C6CF),
    surfaceContainerLow = Color(0xFF191C20), surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF282A2F), surfaceContainerHighest = Color(0xFF33353A),
    outline = Color(0xFF8E9099), outlineVariant = Color(0xFF44474E), inversePrimary = Color(0xFF005DB7),
)

@Composable
fun SocialTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val scheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
