package com.fleetflow.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
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
                var telaAtual by remember { mutableStateOf<Tela>(Tela.Boasvindas) }
                var usuarioAtual by remember { mutableStateOf<UserResponseDto?>(null) }
                var permissoesAtuais by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
                val context = LocalContext.current
                val authRepository = remember { AuthRepository(context) }
                val coroutineScope = rememberCoroutineScope()

                fun aoLogar(usuario: UserResponseDto) {
                    usuarioAtual = usuario
                    coroutineScope.launch {
                        permissoesAtuais = authRepository.getStoredPermissoes()
                    }
                    telaAtual = Tela.Home
                }

                fun sair() {
                    coroutineScope.launch { authRepository.logout() }
                    usuarioAtual = null
                    permissoesAtuais = emptyMap()
                    telaAtual = Tela.Login
                }

                when (telaAtual) {
                    is Tela.Boasvindas -> WelcomeScreen(onComecarClick = { telaAtual = Tela.Login })
                    is Tela.Login -> LoginScreen(
                        onUserLoginSuccess = { usuario -> aoLogar(usuario) },
                        onAccountPending = { telaAtual = Tela.Aguardando },
                        onAccountBlocked = { telaAtual = Tela.AcessoNegado }
                    )
                    is Tela.Aguardando -> AguardandoScreen(onSairClick = { telaAtual = Tela.Login })
                    is Tela.Home -> HomeScreen(
                        usuario = usuarioAtual,
                        onPerfilClick = { telaAtual = Tela.Perfil },
                        onUsuariosClick = { telaAtual = Tela.Usuarios }
                    )
                    is Tela.Perfil -> PerfilScreen(user = usuarioAtual, onSairClick = { sair() })
                    is Tela.Usuarios -> UsuariosScreen(
                        permissoes = permissoesAtuais,
                        onVoltarClick = { telaAtual = Tela.Home }
                    )
                    is Tela.AcessoNegado -> AcessoNegadoScreen(onVoltarClick = { telaAtual = Tela.Home })
                }
            }
        }
    }
}
