package com.fleetflow.mobile.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = AmbarDourado,
    secondary = Petroleo,
    tertiary = VerdeEsmeralda,
    background = Petroleo,
    surface = Petroleo
)

private val LightColorScheme = lightColorScheme(
    primary = Petroleo,
    secondary = AmbarDourado,
    tertiary = VerdeEsmeralda,
    background = BackgroundTela,
    surface = SuperficieCard,
    onPrimary = Branco,
    onSecondary = Petroleo,
    onBackground = TextoPrincipal,
    onSurface = TextoPrincipal
)

@Composable
fun FleetFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Desligado: o FleetFlow tem cor de marca própria e não deve
    // seguir o papel de parede do usuário (recurso do Android 12+)
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}