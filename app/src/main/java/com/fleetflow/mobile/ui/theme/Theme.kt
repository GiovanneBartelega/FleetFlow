package com.fleetflow.mobile.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = Petroleo,
    onPrimary = Branco,
    secondary = AmbarDourado,
    onSecondary = TextoPrincipal,   // texto sobre laranja é escuro, nunca branco
    tertiary = VerdeEsmeralda,
    onTertiary = Branco,
    error = Vermelho,
    onError = Branco,
    background = BackgroundTela,
    onBackground = TextoPrincipal,
    surface = SuperficieCard,
    onSurface = TextoPrincipal,
    surfaceVariant = CinzaSuave,
    onSurfaceVariant = TextoSecundario,
    outline = Contorno,
)

private val DarkColorScheme = darkColorScheme(
    primary = AzulPrimarioEscuro,
    onPrimary = Branco,
    secondary = AmbarDourado,
    onSecondary = TextoPrincipal,
    tertiary = VerdeEscuro,
    error = ErroEscuro,
    background = FundoEscuro,
    onBackground = TextoEscuro,
    surface = SuperficieEscura,
    onSurface = TextoEscuro,
    surfaceVariant = SuperficieVariantEscura,
    onSurfaceVariant = TextoSecundarioEscuro,
    outline = ContornoEscuro,
)

@Composable
fun FleetFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Desligado: o FleetFlow tem cor de marca própria e não segue o papel de parede (Android 12+)
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