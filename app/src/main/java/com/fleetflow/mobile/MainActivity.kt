package com.fleetflow.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.fleetflow.mobile.data.model.UserResponseDto
import com.fleetflow.mobile.data.repository.AuthRepository
import com.fleetflow.mobile.ui.screens.AcessoNegadoScreen
import com.fleetflow.mobile.ui.screens.AguardandoScreen
import com.fleetflow.mobile.ui.screens.HomeScreen
import com.fleetflow.mobile.ui.screens.LoginScreen
import com.fleetflow.mobile.ui.screens.PerfilScreen
import com.fleetflow.mobile.ui.screens.UsuariosScreen
import com.fleetflow.mobile.ui.screens.WelcomeScreen
import com.fleetflow.mobile.ui.theme.FleetFlowTheme
import kotlinx.coroutines.launch

sealed class Tela {
    object Boasvindas : Tela()
    object Login : Tela()
    object Aguardando : Tela()
    object Home : Tela()
    object Perfil : Tela()
    object Usuarios : Tela()
    object AcessoNegado : Tela()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FleetFlowTheme {
                val context = applicationContext
                val authRepository = remember { AuthRepository(context) }
                val coroutineScope = rememberCoroutineScope()
                var currentUser by remember { mutableStateOf<UserResponseDto?>(null) }
                var telaAtual by remember { mutableStateOf<Tela>(Tela.Boasvindas) }

                LaunchedEffect(Unit) {
                    if (authRepository.isLoggedIn()) {
                        val result = authRepository.getCurrentUser()
                        if (result.isSuccess) {
                            currentUser = result.getOrThrow()
                        }
                    }
                }

                fun handleLogout() {
                    coroutineScope.launch {
                        authRepository.logout()
                        currentUser = null
                        telaAtual = Tela.Login
                    }
                }

                when (telaAtual) {
                    is Tela.Boasvindas -> WelcomeScreen(onComecarClick = { telaAtual = Tela.Login })
                    is Tela.Login -> LoginScreen(
                        onLoginSuccess = { telaAtual = Tela.Home },
                        onUserLoginSuccess = { user ->
                            currentUser = user
                            telaAtual = Tela.Home
                        }
                    )
                    is Tela.Aguardando -> AguardandoScreen(onSairClick = { handleLogout() })
                    is Tela.Home -> HomeScreen(
                        user = currentUser,
                        onPerfilClick = { telaAtual = Tela.Perfil },
                        onUsuariosClick = { telaAtual = Tela.Usuarios }
                    )
                    is Tela.Perfil -> PerfilScreen(
                        user = currentUser,
                        onSairClick = { handleLogout() }
                    )
                    is Tela.Usuarios -> UsuariosScreen(
                        user = currentUser,
                        onVoltarClick = { telaAtual = Tela.Home }
                    )
                    is Tela.AcessoNegado -> AcessoNegadoScreen(onVoltarClick = { telaAtual = Tela.Home })
                }
            }
        }
    }
}
