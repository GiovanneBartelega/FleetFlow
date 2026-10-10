package com.fleetflow.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.fleetflow.mobile.R
import com.fleetflow.mobile.data.model.UserResponseDto
import com.fleetflow.mobile.data.repository.ApiException
import com.fleetflow.mobile.data.repository.AuthRepository
import com.fleetflow.mobile.ui.theme.*
import kotlinx.coroutines.launch

// Usuários de teste inseridos no banco (um por perfil) para validar o app sem
// depender de uma conta Google real. Exigem DEV_LOGIN=true na API (uso local).
private data class PerfilDeTeste(val rotulo: String, val email: String)

private val PERFIS_DE_TESTE = listOf(
    PerfilDeTeste("Administrador", "admin.teste@fleetflow.com"),
    PerfilDeTeste("Financeiro", "financeiro.teste@fleetflow.com"),
    PerfilDeTeste("Gestor de Frota", "gestorfrota.teste@fleetflow.com"),
    PerfilDeTeste("Operador Motorista", "motorista.teste@fleetflow.com"),
)

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit = {},
    onUserLoginSuccess: (UserResponseDto) -> Unit = { onLoginSuccess() },
    onAccountPending: () -> Unit = {},
    onAccountBlocked: () -> Unit = {}
) {
    var carregando by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var mostrarPerfisDeTeste by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val authRepository = remember { AuthRepository(context) }
    val coroutineScope = rememberCoroutineScope()

    fun tratarResultado(result: Result<UserResponseDto>) {
        if (result.isSuccess) {
            onUserLoginSuccess(result.getOrThrow())
            return
        }
        val erro = result.exceptionOrNull()
        if (erro is ApiException) {
            when (erro.code) {
                "ACCOUNT_PENDING" -> { onAccountPending(); return }
                "ACCOUNT_BLOCKED", "ACCOUNT_DISABLED" -> { onAccountBlocked(); return }
            }
        }
        errorMessage = erro?.message ?: "Falha no login."
    }

    fun performRealGoogleLogin() {
        coroutineScope.launch {
            carregando = true
            errorMessage = null
            try {
                val credentialManager = CredentialManager.create(context)
                val webClientId = context.getString(R.string.default_web_client_id)

                val googleIdOption = GetGoogleIdOption.Builder()
                    .setServerClientId(webClientId)
                    .setFilterByAuthorizedAccounts(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(context, request)
                val credential = result.credential

                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken
                    tratarResultado(authRepository.loginWithGoogle(idToken))
                } else {
                    errorMessage = "Tipo de credencial inválido."
                }
            } catch (ex: NoCredentialException) {
                errorMessage = "Nenhuma conta Google encontrada no dispositivo. Adicione uma conta Google nas Configurações do Android ou use um perfil de teste abaixo."
            } catch (ex: GetCredentialCancellationException) {
                errorMessage = "Login com Google cancelado pelo usuário."
            } catch (ex: GetCredentialException) {
                errorMessage = "Login Google falhou: ${ex.message}"
            } catch (ex: Exception) {
                errorMessage = "Erro inesperado: ${ex.message}"
            } finally {
                carregando = false
            }
        }
    }

    fun performDevLogin(email: String) {
        coroutineScope.launch {
            carregando = true
            errorMessage = null
            tratarResultado(authRepository.loginDev(email))
            carregando = false
        }
    }

    Scaffold(containerColor = BackgroundTela) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "FleetFlow",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Petroleo,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Bem-vindo de volta",
                fontSize = 16.sp,
                color = TextoSecundario,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(2.dp, Petroleo),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(enabled = !carregando) {
                            performRealGoogleLogin()
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("G", color = AzulGoogle, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Entrar com Google", color = Petroleo, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = { mostrarPerfisDeTeste = !mostrarPerfisDeTeste },
                enabled = !carregando
            ) {
                Text(
                    text = "Modo Desenvolvedor / Emulador (perfis de teste)",
                    fontSize = 12.sp,
                    color = AzulGoogle
                )
            }

            if (mostrarPerfisDeTeste) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PERFIS_DE_TESTE.forEach { perfil ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, CinzaSuave),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable(enabled = !carregando) { performDevLogin(perfil.email) },
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Entrar como ${perfil.rotulo}",
                                    color = Petroleo,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            if (carregando) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator(color = AmbarDourado)
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorMessage!!,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Ao entrar, você concorda com os termos de uso",
                fontSize = 12.sp,
                color = PlaceholderCor,
                textAlign = TextAlign.Center
            )
        }
    }
}
