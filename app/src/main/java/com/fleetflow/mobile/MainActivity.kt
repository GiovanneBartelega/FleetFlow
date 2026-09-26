package com.fleetflow.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.fleetflow.mobile.ui.screens.AguardandoScreen
import com.fleetflow.mobile.ui.screens.HomeScreen
import com.fleetflow.mobile.ui.screens.LoginScreen
import com.fleetflow.mobile.ui.screens.PerfilScreen
import com.fleetflow.mobile.ui.screens.WelcomeScreen
import com.fleetflow.mobile.ui.theme.FleetFlowTheme

sealed class Tela {
    object Boasvindas : Tela()
    object Login : Tela()
    object Aguardando : Tela()
    object Home : Tela()
    object Perfil : Tela()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FleetFlowTheme {
                var telaAtual by remember { mutableStateOf<Tela>(Tela.Boasvindas) }

                when (telaAtual) {
                    is Tela.Boasvindas -> WelcomeScreen(onComecarClick = { telaAtual = Tela.Login })
                    is Tela.Login -> LoginScreen(onLoginSuccess = { telaAtual = Tela.Home })
                    is Tela.Aguardando -> AguardandoScreen(onSairClick = { telaAtual = Tela.Login })
                    is Tela.Home -> HomeScreen(onPerfilClick = { telaAtual = Tela.Perfil })
                    is Tela.Perfil -> PerfilScreen(onSairClick = { telaAtual = Tela.Login })
                }
            }
        }
    }
}