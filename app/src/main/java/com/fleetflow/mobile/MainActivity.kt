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
                val authRepository = remember { AuthRepository(applicationContext) }
                val scope = rememberCoroutineScope()

                var telaAtual by remember { mutableStateOf<Tela>(Tela.Boasvindas) }

                // Usuário devolvido pelo backend no login. null = ninguém logado.
                // Antes ele era descartado; agora fica guardado e chega nas telas que precisam.
                var usuario by remember { mutableStateOf<UserResponseDto?>(null) }

                // Logout de verdade: revoga o token no backend e apaga do aparelho.
                fun sair() {
                    scope.launch { authRepository.logout() }
                    usuario = null
                    telaAtual = Tela.Login
                }

                when (telaAtual) {
                    is Tela.Boasvindas -> WelcomeScreen(onComecarClick = { telaAtual = Tela.Login })

                    is Tela.Login -> LoginScreen(
                        onUserLoginSuccess = { user ->
                            usuario = user
                            // Usuário novo nasce PENDING no backend: vai para a espera, não para a Home.
                            telaAtual = if (user.status == "PENDING") Tela.Aguardando else Tela.Home
                        }
                    )

                    is Tela.Aguardando -> AguardandoScreen(onSairClick = { sair() })

                    is Tela.Home -> HomeScreen(
                        onPerfilClick = { telaAtual = Tela.Perfil },
                        onUsuariosClick = { telaAtual = Tela.Usuarios }
                    )

                    is Tela.Perfil -> PerfilScreen(user = usuario, onSairClick = { sair() })

                    // Agora chama a tela da Bruna, ligada na API, passando o usuário logado.
                    is Tela.Usuarios -> UsuariosScreen(user = usuario, onVoltarClick = { telaAtual = Tela.Home })

                    is Tela.AcessoNegado -> AcessoNegadoScreen(onVoltarClick = { telaAtual = Tela.Home })
                }
            }
        }
    }
}