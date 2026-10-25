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
    primary = Color(0xFF8C4A5E), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E1), onPrimaryContainer = Color(0xFF703346),
    secondary = Color(0xFF74565E), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFED6DE), onSecondaryContainer = Color(0xFF5B3F46),
    tertiary = Color(0xFF946C48), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF946C48), onTertiaryContainer = Color(0xFFFFFFFF),
    background = Color(0xFFFFF8F8), onBackground = Color(0xFF22191B),
    surface = Color(0xFFFFF8F8), onSurface = Color(0xFF22191B), onSurfaceVariant = Color(0xFF514346),
    surfaceContainerLow = Color(0xFFFFF0F2), surfaceContainer = Color(0xFFFBEAEC),
    surfaceContainerHigh = Color(0xFFF5E4E7), surfaceContainerHighest = Color(0xFFEFDFE1),
    outline = Color(0xFF817074), outlineVariant = Color(0xFFD6C2C5), inversePrimary = Color(0xFFFFB1C6),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB1C6), onPrimary = Color(0xFF551D30),
    primaryContainer = Color(0xFF703346), onPrimaryContainer = Color(0xFFFFD9E1),
    secondary = Color(0xFFE3BDC5), onSecondary = Color(0xFF422930),
    secondaryContainer = Color(0xFF5B3F46), onSecondaryContainer = Color(0xFFFFD9E1),
    tertiary = Color(0xFFEEBD93), onTertiary = Color(0xFF472A0B),
    tertiaryContainer = Color(0xFFB38861), onTertiaryContainer = Color(0xFF000000),
    background = Color(0xFF191113), onBackground = Color(0xFFEFDFE1),
    surface = Color(0xFF191113), onSurface = Color(0xFFEFDFE1), onSurfaceVariant = Color(0xFFD6C2C5),
    surfaceContainerLow = Color(0xFF22191B), surfaceContainer = Color(0xFF261D1F),
    surfaceContainerHigh = Color(0xFF31282A), surfaceContainerHighest = Color(0xFF3C3234),
    outline = Color(0xFF9E8C90), outlineVariant = Color(0xFF514346), inversePrimary = Color(0xFF8C4A5E),
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
