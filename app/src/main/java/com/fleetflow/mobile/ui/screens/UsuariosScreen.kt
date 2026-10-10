package com.fleetflow.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fleetflow.mobile.data.auth.AuthorizationManager
import com.fleetflow.mobile.data.model.PerfilDto
import com.fleetflow.mobile.data.model.UserResponseDto
import com.fleetflow.mobile.data.repository.AuthRepository
import com.fleetflow.mobile.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun UsuariosScreen(
    permissoes: Map<String, String?> = emptyMap(),
    onVoltarClick: () -> Unit
) {
    val context = LocalContext.current
    val authRepository = remember { AuthRepository(context) }
    val authorizationManager = remember(permissoes) { AuthorizationManager(permissoes) }
    val coroutineScope = rememberCoroutineScope()

    var usuarios by remember { mutableStateOf<List<UserResponseDto>>(emptyList()) }
    var perfis by remember { mutableStateOf<List<PerfilDto>>(emptyList()) }
    var carregando by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedUserForRoleChange by remember { mutableStateOf<UserResponseDto?>(null) }
    var pendingRoleChange by remember { mutableStateOf<Pair<UserResponseDto, PerfilDto>?>(null) }

    fun carregarUsuarios() {
        coroutineScope.launch {
            carregando = true
            errorMessage = null
            val result = authRepository.getUsers()
            if (result.isSuccess) {
                usuarios = result.getOrDefault(emptyList())
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Erro ao carregar usuários."
            }
            if (authorizationManager.canChangeUserRole()) {
                authRepository.getPerfis().onSuccess { perfis = it }
            }
            carregando = false
        }
    }

    LaunchedEffect(Unit) {
        if (authorizationManager.canManageUsers()) {
            carregarUsuarios()
        } else {
            carregando = false
            errorMessage = "Você não possui permissão para gerenciar ou visualizar usuários."
        }
    }

    Scaffold(
        containerColor = BackgroundTela,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Petroleo)
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "←",
                    color = Branco,
                    fontSize = 22.sp,
                    modifier = Modifier.clickable { onVoltarClick() }
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text("Gestão de Usuários", color = Branco, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            when {
                carregando -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = AmbarDourado
                    )
                }
                errorMessage != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { carregarUsuarios() },
                            colors = ButtonDefaults.buttonColors(containerColor = Petroleo)
                        ) {
                            Text("Tentar Novamente", color = Branco)
                        }
                    }
                }
                usuarios.isEmpty() -> {
                    Text(
                        text = "Nenhum usuário encontrado.",
                        modifier = Modifier.align(Alignment.Center),
                        color = TextoSecundario,
                        fontSize = 14.sp
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(usuarios) { usuario ->
                            CardUsuario(
                                usuario = usuario,
                                canApprove = authorizationManager.canApproveUsers(),
                                canChangeRole = authorizationManager.canChangeUserRole(),
                                onAprovarClick = {
                                    if (authorizationManager.canApproveUsers()) {
                                        coroutineScope.launch {
                                            val res = authRepository.approveUser(usuario.id)
                                            if (res.isSuccess) {
                                                carregarUsuarios()
                                            } else {
                                                errorMessage = res.exceptionOrNull()?.message
                                            }
                                        }
                                    }
                                },
                                onRoleClick = {
                                    if (authorizationManager.canChangeUserRole()) {
                                        selectedUserForRoleChange = usuario
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialog de seleção de perfil (só quem pode editar usuários)
    if (selectedUserForRoleChange != null && authorizationManager.canChangeUserRole()) {
        AlertDialog(
            onDismissRequest = { selectedUserForRoleChange = null },
            title = { Text("Alterar perfil") },
            text = {
                Column {
                    Text("Selecione o novo perfil para ${selectedUserForRoleChange!!.nome}:")
                    Spacer(modifier = Modifier.height(8.dp))
                    perfis.forEach { perfil ->
                        TextButton(
                            onClick = {
                                val targetUser = selectedUserForRoleChange!!
                                selectedUserForRoleChange = null
                                pendingRoleChange = Pair(targetUser, perfil)
                            }
                        ) {
                            Text(perfil.nome, fontWeight = FontWeight.Bold, color = Petroleo)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedUserForRoleChange = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Segundo dialog: confirmação da troca de perfil
    if (pendingRoleChange != null && authorizationManager.canChangeUserRole()) {
        val (targetUser, novoPerfil) = pendingRoleChange!!
        AlertDialog(
            onDismissRequest = { pendingRoleChange = null },
            title = { Text("Alterar perfil?") },
            text = {
                Text("Você está alterando o perfil de ${targetUser.nome} para ${novoPerfil.nome}. Essa alteração modificará as permissões desse usuário.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingRoleChange = null
                        coroutineScope.launch {
                            val res = authRepository.updateUserPerfil(targetUser.id, novoPerfil.id)
                            if (res.isSuccess) {
                                carregarUsuarios()
                            } else {
                                errorMessage = res.exceptionOrNull()?.message
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Petroleo)
                ) {
                    Text("Confirmar", color = Branco)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRoleChange = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun CardUsuario(
    usuario: UserResponseDto,
    canApprove: Boolean,
    canChangeRole: Boolean,
    onAprovarClick: () -> Unit,
    onRoleClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SuperficieCard)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(AmbarDourado),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    usuario.nome.take(1).uppercase(),
                    color = Petroleo,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(usuario.nome, color = TextoPrincipal, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(usuario.email, color = TextoSecundario, fontSize = 12.sp)
            }

            BadgeStatus(usuario.status)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(CinzaSuave)
                    .clickable(enabled = canChangeRole) {
                        if (canChangeRole) {
                            onRoleClick()
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${usuario.perfil.nome} ${if (canChangeRole) "✎" else ""}",
                    color = CinzaBadge,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (usuario.status == "AguardandoAprovacao" && canApprove) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(VerdeEsmeralda)
                        .clickable { onAprovarClick() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("Aprovar", color = Branco, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun BadgeStatus(status: String) {
    val isAtivo = status == "Ativo"
    val cor = if (isAtivo) VerdeSuave else FundoInsight
    val corTexto = if (isAtivo) VerdeEsmeralda else AmbarAlerta

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(cor)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(status, color = corTexto, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
