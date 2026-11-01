package com.lechixy.kick.theme

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

import com.lechixy.kick.util.findActivity

val KickDarkColorScheme: ColorScheme = darkColorScheme(
    primary = KickVoltGreen,
    onPrimary = KickBlack,
    primaryContainer = Color(0xFF1E5C00),
    onPrimaryContainer = Color(0xFFB8FF94),
    inversePrimary = Color(0xFF2D7A00),

    secondary = KickIcedOutCyan,
    onSecondary = KickBlack,
    secondaryContainer = Color(0xFF004F50),
    onSecondaryContainer = Color(0xFF9CF8F8),

    tertiary = Color(0xFFE2C85A),
    onTertiary = Color(0xFF211B00),
    tertiaryContainer = KickBowelBrown,
    onTertiaryContainer = Color(0xFFF2E3A0),

    background = Color(0xFF0A0C08),
    onBackground = Color(0xFFE3E8D8),
    surface = Color(0xFF0A0C08),
    onSurface = Color(0xFFE3E8D8),
    surfaceVariant = Color(0xFF2A2E22),
    onSurfaceVariant = Color(0xFFC5CBB8),
    surfaceTint = KickVoltGreen,
    inverseSurface = Color(0xFFE3E8D8),
    inverseOnSurface = Color(0xFF2E3228),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    outline = Color(0xFF8E947F),
    outlineVariant = Color(0xFF44483A),
    scrim = KickBlack,

    surfaceBright = Color(0xFF2F3329),
    surfaceDim = Color(0xFF0A0C08),
    surfaceContainerLowest = Color(0xFF050703),
    surfaceContainerLow = Color(0xFF12150E),
    surfaceContainer = Color(0xFF171A12),
    surfaceContainerHigh = Color(0xFF21251B),
    surfaceContainerHighest = Color(0xFF2C3025),

    primaryFixed = KickVoltGreen,
    primaryFixedDim = Color(0xFF45D612),
    onPrimaryFixed = Color(0xFF0B2900),
    onPrimaryFixedVariant = Color(0xFF1E5C00),
    secondaryFixed = KickIcedOutCyan,
    secondaryFixedDim = Color(0xFF00DCDC),
    onSecondaryFixed = Color(0xFF002020),
    onSecondaryFixedVariant = Color(0xFF004F50),
    tertiaryFixed = Color(0xFFEFE1A3),
    tertiaryFixedDim = Color(0xFFD3C589),
    onTertiaryFixed = Color(0xFF211B00),
    onTertiaryFixedVariant = KickBowelBrown,
)

val KickLightColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF2D7A00),
    onPrimary = KickWhite,
    primaryContainer = KickVoltGreen,
    onPrimaryContainer = Color(0xFF0B2900),
    inversePrimary = KickVoltGreen,

    secondary = Color(0xFF006A6A),
    onSecondary = KickWhite,
    secondaryContainer = KickIcedOutCyan,
    onSecondaryContainer = Color(0xFF002020),

    tertiary = KickBowelBrown,
    onTertiary = KickWhite,
    tertiaryContainer = Color(0xFFEFE1A3),
    onTertiaryContainer = Color(0xFF211B00),

    background = Color(0xFFF9FBF2),
    onBackground = Color(0xFF12150E),
    surface = Color(0xFFF9FBF2),
    onSurface = Color(0xFF12150E),
    surfaceVariant = Color(0xFFDDE5CF),
    onSurfaceVariant = Color(0xFF43483A),
    surfaceTint = Color(0xFF2D7A00),
    inverseSurface = Color(0xFF2E3228),
    inverseOnSurface = Color(0xFFF0F3E6),

    error = Color(0xFFBA1A1A),
    onError = KickWhite,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    outline = Color(0xFF73796A),
    outlineVariant = Color(0xFFC3C9B6),
    scrim = KickBlack,

    surfaceBright = Color(0xFFF9FBF2),
    surfaceDim = Color(0xFFD9DBD1),
    surfaceContainerLowest = KickWhite,
    surfaceContainerLow = Color(0xFFF3F6EA),
    surfaceContainer = Color(0xFFEDF0E4),
    surfaceContainerHigh = Color(0xFFE7EADE),
    surfaceContainerHighest = Color(0xFFE1E5D8),

    primaryFixed = KickVoltGreen,
    primaryFixedDim = Color(0xFF45D612),
    onPrimaryFixed = Color(0xFF0B2900),
    onPrimaryFixedVariant = Color(0xFF1E5C00),
    secondaryFixed = KickIcedOutCyan,
    secondaryFixedDim = Color(0xFF00DCDC),
    onSecondaryFixed = Color(0xFF002020),
    onSecondaryFixedVariant = Color(0xFF004F50),
    tertiaryFixed = Color(0xFFEFE1A3),
    tertiaryFixedDim = Color(0xFFD3C589),
    onTertiaryFixed = Color(0xFF211B00),
    onTertiaryFixedVariant = KickBowelBrown,
)

enum class Theme {
    SYSTEM,
    LIGHT,
    DARK,
    AMOLED
}

@Composable
fun KickTheme(
    theme: Theme,
    dynamicColor: Boolean,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Is dynamic color
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current

            if (theme == Theme.SYSTEM){
                if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                when (theme) {
                    Theme.LIGHT -> dynamicLightColorScheme(context)
                    Theme.DARK -> dynamicDarkColorScheme(context)
                    Theme.AMOLED -> dynamicDarkColorScheme(context).copy(
                        background = Color.Black,
                        surface = Color.Black,
                        surfaceContainer = Color.Black,
                        // TODO: add other colors
                    )
                }
            }
        }

        // Is not dynamic color
        theme == Theme.SYSTEM -> {
            if (isSystemInDarkTheme()) KickDarkColorScheme else KickLightColorScheme
        }
        theme == Theme.DARK -> KickDarkColorScheme
        theme == Theme.AMOLED -> KickDarkColorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainer = Color.Black,
            // TODO: add other colors
        )
        else -> KickLightColorScheme
    }

    val activity = LocalContext.current.findActivity() as? ComponentActivity
    val darkBars = colorScheme.surface.luminance() < 0.5f
    SideEffect {
        val transparent = android.graphics.Color.TRANSPARENT
        activity?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(transparent, transparent) { darkBars },
            navigationBarStyle = SystemBarStyle.auto(transparent, transparent) { darkBars }
        )
        activity?.window?.isNavigationBarContrastEnforced = false
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}