package com.fleetflow.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.fleetflow.mobile.data.local.AppDatabase
import com.fleetflow.mobile.data.model.UserResponseDto
import com.fleetflow.mobile.data.repository.AuthRepository
import com.fleetflow.mobile.ui.screens.AguardandoScreen
import com.fleetflow.mobile.ui.screens.LoginScreen
import com.fleetflow.mobile.ui.screens.MovimentacaoRepo
import com.fleetflow.mobile.ui.screens.WelcomeScreen
import com.fleetflow.mobile.ui.theme.FleetFlowTheme
import kotlinx.coroutines.launch

// Antes do login, o MainActivity decide a tela. Depois, quem manda é o FleetFlowApp.
sealed class Tela {
    object Boasvindas : Tela()
    object Login : Tela()
    object Aguardando : Tela()
    object App : Tela()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()



        com.fleetflow.mobile.data.repository.FinanceRepository.init(applicationContext)
        setContent {
            FleetFlowTheme {
                val authRepository = remember { AuthRepository(applicationContext) }
                val scope = rememberCoroutineScope()

                var telaAtual by remember { mutableStateOf<Tela>(Tela.Boasvindas) }
                var usuario by remember { mutableStateOf<UserResponseDto?>(null) }

                fun sair() {
                    scope.launch { authRepository.logout() }
                    MovimentacaoRepo.clear()
                    usuario = null
                    telaAtual = Tela.Login
                }

                LaunchedEffect(Unit) {
                    if (authRepository.isLoggedIn()) {
                        authRepository.getCurrentUser().onSuccess { usuario=it; telaAtual=Tela.App }
                            .onFailure { telaAtual=Tela.Login }
                    }
                }
                when (telaAtual) {
                    is Tela.Boasvindas -> WelcomeScreen(onComecarClick = { telaAtual = Tela.Login })

                    is Tela.Login -> LoginScreen(
                        onAccountPending = {telaAtual=Tela.Aguardando},
                        onUserLoginSuccess = { user ->
                            usuario = user
                            telaAtual = if (user.status == "AguardandoAprovacao") Tela.Aguardando else Tela.App
                        }
                    )

                    is Tela.Aguardando -> AguardandoScreen(onSairClick = { sair() })

                    // A partir daqui, as abas vêm do perfil que o backend mandou.
                    is Tela.App -> usuario?.let { u ->
                        FleetFlowApp(usuario = u, onLogout = { sair() })
                    }
                }
            }
        }
    }
}