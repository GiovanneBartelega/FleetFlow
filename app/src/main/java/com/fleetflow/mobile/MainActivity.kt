package com.fleetflow.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.fleetflow.mobile.data.auth.TokenManager
import com.fleetflow.mobile.data.local.AppDatabase
import com.fleetflow.mobile.data.model.UserResponseDto
import com.fleetflow.mobile.data.repository.AuthRepository
import com.fleetflow.mobile.ui.screens.AcessoNegadoScreen
import com.fleetflow.mobile.ui.screens.AguardandoScreen
import com.fleetflow.mobile.ui.screens.LoginScreen
import com.fleetflow.mobile.ui.screens.MovimentacaoRepo
import com.fleetflow.mobile.ui.screens.WelcomeScreen
import com.fleetflow.mobile.ui.theme.FleetFlowTheme
import kotlinx.coroutines.launch
import java.io.IOException

// Antes do login, o MainActivity decide a tela. Depois, quem manda é o FleetFlowApp.
sealed class Tela {
    object Carregando : Tela()      // conferindo se já existe uma sessão salva
    object Boasvindas : Tela()
    object Login : Tela()
    object Aguardando : Tela()      // conta criada, esperando aprovação
    object ContaInativa : Tela()    // conta desativada
    object App : Tela()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Liga o banco local. Chamar de novo (ao girar a tela) não faz nada.
        MovimentacaoRepo.init(AppDatabase.get(applicationContext))

        setContent {
            FleetFlowTheme {
                val authRepository = remember { AuthRepository(applicationContext) }
                val tokenManager = remember { TokenManager(applicationContext) }
                val scope = rememberCoroutineScope()

                var telaAtual by remember { mutableStateOf<Tela>(Tela.Carregando) }
                var usuario by remember { mutableStateOf<UserResponseDto?>(null) }

                // Decide a tela pelo status que o backend mandou e guarda o usuário,
                // para o app conseguir abrir mesmo sem internet.
                fun entrar(user: UserResponseDto) {
                    usuario = user
                    scope.launch { tokenManager.saveUsuario(user) }
                    telaAtual = when (user.status) {
                        "ACTIVE" -> Tela.App
                        "PENDING" -> Tela.Aguardando
                        else -> Tela.ContaInativa
                    }
                }

                // Sair: avisa o backend, apaga o token e os lançamentos deste aparelho.
                fun sair() {
                    scope.launch { authRepository.logout() }
                    MovimentacaoRepo.limpar()
                    usuario = null
                    telaAtual = Tela.Login
                }

                // SESSÃO PERSISTENTE (US15). Roda uma vez, quando o app abre.
                LaunchedEffect(Unit) {
                    // Nunca entrou (ou saiu da conta): começa pelas boas-vindas.
                    if (!authRepository.isLoggedIn()) {
                        telaAtual = Tela.Boasvindas
                        return@LaunchedEffect
                    }

                    // Tem token salvo: pergunta ao backend quem é o usuário.
                    // Se o token de 15 minutos venceu, o TokenAuthenticator renova sozinho
                    // com o refresh token (que vale 7 dias) e repete o pedido.
                    val resultado = authRepository.getCurrentUser()
                    val erro = resultado.exceptionOrNull()
                    when {
                        resultado.isSuccess -> entrar(resultado.getOrThrow())

                        // Sem internet ou backend desligado: entra com os dados do último login.
                        erro is IOException -> {
                            val salvo = tokenManager.getUsuarioSalvo()
                            if (salvo != null) entrar(salvo) else telaAtual = Tela.Login
                        }

                        // O backend recusou a sessão (por exemplo, ele reiniciou e esqueceu
                        // os usuários): limpa tudo e pede o login de novo.
                        else -> sair()
                    }
                }

                when (telaAtual) {
                    is Tela.Carregando -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }

                    is Tela.Boasvindas -> WelcomeScreen(onComecarClick = { telaAtual = Tela.Login })

                    is Tela.Login -> LoginScreen(onUserLoginSuccess = { user -> entrar(user) })

                    is Tela.Aguardando -> AguardandoScreen(onSairClick = { sair() })

                    is Tela.ContaInativa -> AcessoNegadoScreen(onVoltarClick = { sair() })

                    // A partir daqui, as abas vêm do perfil que o backend mandou.
                    is Tela.App -> usuario?.let { u ->
                        FleetFlowApp(usuario = u, onLogout = { sair() })
                    }
                }
            }
        }
    }
}