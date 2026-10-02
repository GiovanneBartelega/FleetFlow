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
import com.fleetflow.mobile.data.model.UserResponseDto
import com.fleetflow.mobile.data.repository.AuthRepository
import com.fleetflow.mobile.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun UsuariosScreen(
    user: UserResponseDto?,
    onVoltarClick: () -> Unit
) {
    val context = LocalContext.current
    val authRepository = remember { AuthRepository(context) }
    val authorizationManager = remember(user) { AuthorizationManager(user?.role) }
    val coroutineScope = rememberCoroutineScope()

    var usuarios by remember { mutableStateOf<List<UserResponseDto>>(emptyList()) }
    var carregando by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedUserForRoleChange by remember { mutableStateOf<UserResponseDto?>(null) }
    var pendingRoleChange by remember { mutableStateOf<Pair<UserResponseDto, String>?>(null) }

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

    // Dialog for Role Selection (Admin only - double guarded)
    if (selectedUserForRoleChange != null && authorizationManager.canChangeUserRole()) {
        AlertDialog(
            onDismissRequest = { selectedUserForRoleChange = null },
            title = { Text("Alterar Perfil (Role)") },
            text = {
                Column {
                    Text("Selecione o novo perfil para ${selectedUserForRoleChange!!.name}:")
                    Spacer(modifier = Modifier.height(8.dp))
                    val roles = listOf("ADMINISTRATOR", "FLEET_MANAGER", "FINANCIAL", "DRIVER")
                    roles.forEach { role ->
                        TextButton(
                            onClick = {
                                val targetUser = selectedUserForRoleChange!!
                                selectedUserForRoleChange = null
                                pendingRoleChange = Pair(targetUser, role)
                            }
                        ) {
                            Text(role, fontWeight = FontWeight.Bold, color = Petroleo)
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

    // Second Dialog: Confirmation for Role Change (Admin only - double guarded)
    if (pendingRoleChange != null && authorizationManager.canChangeUserRole()) {
        val (targetUser, newRole) = pendingRoleChange!!
        AlertDialog(
            onDismissRequest = { pendingRoleChange = null },
            title = { Text("Alterar perfil?") },
            text = {
                Text("Você está alterando o perfil de ${targetUser.name} para $newRole. Essa alteração modificará as permissões desse usuário.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingRoleChange = null
                        if (authorizationManager.canChangeUserRole()) {
                            coroutineScope.launch {
                                val res = authRepository.updateUserRole(targetUser.id, newRole)
                                if (res.isSuccess) {
                                    carregarUsuarios()
                                } else {
                                    errorMessage = res.exceptionOrNull()?.message
                                }
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
                    usuario.name.take(1).uppercase(),
                    color = Petroleo,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(usuario.name, color = TextoPrincipal, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
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
                    text = "${usuario.role} ${if (canChangeRole) "✎" else ""}",
                    color = CinzaBadge,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (usuario.status == "PENDING" && canApprove) {
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
    val isAtivo = status == "ACTIVE"
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
