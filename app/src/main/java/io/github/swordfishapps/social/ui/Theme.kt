@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.swordfishapps.social.ui

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
import io.github.swordfishapps.social.data.Settings

// Fallback palette: Swordfish blue (#1F5F99), generated with Material's colour utilities.
// Used on Android 11 and below, or with dynamic colour turned off.
private val Light = lightColorScheme(
    primary = Color(0xFF36618E), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD2E4FF), onPrimaryContainer = Color(0xFF1A4975),
    secondary = Color(0xFF535F70), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD7E3F8), onSecondaryContainer = Color(0xFF3B4858),
    tertiary = Color(0xFF826D8F), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF3DAFF), onTertiaryContainer = Color(0xFF523F5F),
    background = Color(0xFFF8F9FF), onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FF), onSurface = Color(0xFF191C20), onSurfaceVariant = Color(0xFF43474E),
    surfaceContainerLow = Color(0xFFF2F3FA), surfaceContainer = Color(0xFFECEEF4),
    surfaceContainerHigh = Color(0xFFE6E8EE), surfaceContainerHighest = Color(0xFFE1E2E8),
    outline = Color(0xFF70757C), outlineVariant = Color(0xFFC3C6CF), inversePrimary = Color(0xFFA1CAFD),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA1CAFD), onPrimary = Color(0xFF003259),
    primaryContainer = Color(0xFF1A4975), onPrimaryContainer = Color(0xFFD2E4FF),
    secondary = Color(0xFFBBC7DB), onSecondary = Color(0xFF253140),
    secondaryContainer = Color(0xFF3B4858), onSecondaryContainer = Color(0xFFD7E3F8),
    tertiary = Color(0xFFD7BEE4), onTertiary = Color(0xFF3B2947),
    tertiaryContainer = Color(0xFF523F5F), onTertiaryContainer = Color(0xFFF3DAFF),
    background = Color(0xFF111418), onBackground = Color(0xFFE1E2E8),
    surface = Color(0xFF111418), onSurface = Color(0xFFE1E2E8), onSurfaceVariant = Color(0xFFC3C6CF),
    surfaceContainerLow = Color(0xFF191C20), surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF272A2F), surfaceContainerHighest = Color(0xFF32353A),
    outline = Color(0xFF8D9199), outlineVariant = Color(0xFF43474E), inversePrimary = Color(0xFF36618E),
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
