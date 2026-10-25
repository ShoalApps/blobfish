@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.tintloom.social.ui

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
import io.github.tintloom.social.data.Settings

// Fallback palette: Tintloom mauve (#7D5260), generated with Material's colour utilities.
// Used on Android 11 and below, or with dynamic colour turned off.
private val Light = lightColorScheme(
    primary = Color(0xFF8B4A61), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E3), onPrimaryContainer = Color(0xFF6F334A),
    secondary = Color(0xFF74565F), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9E3), onSecondaryContainer = Color(0xFF5A3F48),
    tertiary = Color(0xFF7D5635), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCC2), onTertiaryContainer = Color(0xFF623F20),
    background = Color(0xFFFFF8F8), onBackground = Color(0xFF22191C),
    surface = Color(0xFFFFF8F8), onSurface = Color(0xFF22191C), onSurfaceVariant = Color(0xFF514347),
    surfaceContainerLow = Color(0xFFFFF0F2), surfaceContainer = Color(0xFFFAEAED),
    surfaceContainerHigh = Color(0xFFF4E4E7), surfaceContainerHighest = Color(0xFFEFDFE2),
    outline = Color(0xFF837377), outlineVariant = Color(0xFFD5C2C6), inversePrimary = Color(0xFFFFB0CA),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB0CA), onPrimary = Color(0xFF541D33),
    primaryContainer = Color(0xFF6F334A), onPrimaryContainer = Color(0xFFFFD9E3),
    secondary = Color(0xFFE2BDC7), onSecondary = Color(0xFF422931),
    secondaryContainer = Color(0xFF5A3F48), onSecondaryContainer = Color(0xFFFFD9E3),
    tertiary = Color(0xFFEFBC94), onTertiary = Color(0xFF48290C),
    tertiaryContainer = Color(0xFF623F20), onTertiaryContainer = Color(0xFFFFDCC2),
    background = Color(0xFF191114), onBackground = Color(0xFFEFDFE2),
    surface = Color(0xFF191114), onSurface = Color(0xFFEFDFE2), onSurfaceVariant = Color(0xFFD5C2C6),
    surfaceContainerLow = Color(0xFF22191C), surfaceContainer = Color(0xFF261D20),
    surfaceContainerHigh = Color(0xFF31282A), surfaceContainerHighest = Color(0xFF3C3235),
    outline = Color(0xFF9E8C90), outlineVariant = Color(0xFF514347), inversePrimary = Color(0xFF8B4A61),
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
