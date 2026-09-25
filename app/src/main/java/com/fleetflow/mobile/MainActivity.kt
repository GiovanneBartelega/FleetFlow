package com.fleetflow.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.fleetflow.mobile.ui.screens.LoginScreen
import com.fleetflow.mobile.ui.screens.WelcomeScreen
import com.fleetflow.mobile.ui.theme.FleetFlowTheme

sealed class Tela {
    object Boasvindas : Tela()
    object Login : Tela()
    object Home : Tela()
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
                    is Tela.Home -> Surface(modifier = Modifier.fillMaxSize()) {
                        Text("Home (em construção)", modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}