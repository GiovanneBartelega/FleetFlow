package com.fleetflow.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fleetflow.mobile.data.model.UserResponseDto
import com.fleetflow.mobile.data.repository.ApiException
import com.fleetflow.mobile.data.repository.AuthRepository
import com.fleetflow.mobile.data.repository.FinanceRepository
import com.fleetflow.mobile.data.repository.FrotaRepository
import com.fleetflow.mobile.ui.screens.AcessoNegadoScreen
import com.fleetflow.mobile.ui.screens.AguardandoScreen
import com.fleetflow.mobile.ui.screens.LoginScreen
import com.fleetflow.mobile.ui.screens.MovimentacaoRepo
import com.fleetflow.mobile.ui.screens.WelcomeScreen
import com.fleetflow.mobile.ui.theme.BackgroundTela
import com.fleetflow.mobile.ui.theme.FleetFlowTheme
import com.fleetflow.mobile.ui.theme.Petroleo
import kotlinx.coroutines.launch
import java.io.IOException

// Antes do login, o MainActivity decide a tela. Depois, quem manda é o FleetFlowApp.
sealed class Tela {
    object Carregando : Tela()      // conferindo se já existe uma sessão salva
    object Boasvindas : Tela()
    object Login : Tela()
    object Aguardando : Tela()
    object ContaBloqueada : Tela()  // conta bloqueada ou desativada pelo administrador
    object App : Tela()
}

// Para onde vai um usuário conforme o status que a API devolve.
private fun telaPara(user: UserResponseDto): Tela = when (user.status) {
    "Ativo" -> Tela.App
    "AguardandoAprovacao" -> Tela.Aguardando
    else -> Tela.ContaBloqueada
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        FinanceRepository.init(applicationContext)
        FrotaRepository.init(applicationContext)
        setContent {
            FleetFlowTheme {
                val authRepository = remember { AuthRepository(applicationContext) }
                val scope = rememberCoroutineScope()

                // Começa conferindo a sessão. Antes começava nas boas-vindas e trocava de
                // tela quando a resposta chegava, e a tela de boas-vindas "piscava".
                var telaAtual by remember { mutableStateOf<Tela>(Tela.Carregando) }
                var usuario by remember { mutableStateOf<UserResponseDto?>(null) }

                fun entrar(user: UserResponseDto) {
                    usuario = user
                    telaAtual = telaPara(user)
                }

                fun sair() {
                    scope.launch {
                        authRepository.logout()
                        FrotaRepository.limpar()
                    }
                    MovimentacaoRepo.clear()
                    FrotaRepository.clear()
                    usuario = null
                    telaAtual = Tela.Login
                }

                // Sessão que sobrevive a fechar o app (US15).
                LaunchedEffect(Unit) {
                    if (!authRepository.isLoggedIn()) {
                        telaAtual = Tela.Boasvindas
                        return@LaunchedEffect
                    }
                    val resultado = authRepository.getCurrentUser()
                    val user = resultado.getOrNull()
                    val erro = resultado.exceptionOrNull()
                    telaAtual = when {
                        user != null -> { usuario = user; telaPara(user) }
                        // Sem conexão: entra com o usuário salvo no último acesso, para o
                        // motorista conseguir usar o app sem sinal.
                        erro is IOException -> {
                            val salvo = authRepository.getUsuarioSalvo()
                            if (salvo != null && salvo.status == "Ativo") { usuario = salvo; Tela.App } else Tela.Login
                        }
                        erro is ApiException && erro.code == "ACCOUNT_PENDING" -> Tela.Aguardando
                        erro is ApiException && (erro.code == "ACCOUNT_BLOCKED" || erro.code == "ACCOUNT_DISABLED") -> Tela.ContaBloqueada
                        else -> Tela.Login   // sessão expirada ou inválida
                    }
                }

                when (telaAtual) {
                    is Tela.Carregando -> TelaCarregando()

                    is Tela.Boasvindas -> WelcomeScreen(onComecarClick = { telaAtual = Tela.Login })

                    is Tela.Login -> LoginScreen(
                        onAccountPending = { telaAtual = Tela.Aguardando },
                        onAccountBlocked = { telaAtual = Tela.ContaBloqueada },
                        onUserLoginSuccess = { user -> entrar(user) }
                    )

                    is Tela.Aguardando -> AguardandoScreen(onSairClick = { sair() })

                    is Tela.ContaBloqueada -> AcessoNegadoScreen(
                        titulo = "Conta bloqueada",
                        mensagem = "Sua conta foi bloqueada ou desativada. Fale com o administrador do FleetFlow.",
                        textoBotao = "Sair",
                        onVoltarClick = { sair() }
                    )

                    // A partir daqui, as abas vêm do perfil que o backend mandou.
                    is Tela.App -> usuario?.let { u ->
                        FleetFlowApp(usuario = u, onLogout = { sair() })
                    }
                }
            }
        }
    }
}

@Composable
private fun TelaCarregando() {
    Column(
        modifier = Modifier.fillMaxSize().background(BackgroundTela),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("FleetFlow", color = Petroleo, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        CircularProgressIndicator(color = Petroleo)
    }
}
