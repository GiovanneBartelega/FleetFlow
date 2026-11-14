package com.fleetflow.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fleetflow.mobile.data.model.UserResponseDto
import com.fleetflow.mobile.nomeDoPerfil
import com.fleetflow.mobile.ui.theme.*

@Composable
fun PerfilScreen(
    user: UserResponseDto? = null,
    // Configurações (especificação): pendências de sincronização ficam aqui, antes de Sair.
    pendentes: Int = 0,
    onPendenciasClick: (() -> Unit)? = null,
    onSairClick: () -> Unit
) {
    val nomeExibicao = user?.nome?.trim().orEmpty()
    val iniciais = nomeExibicao.split(" ").filter { it.isNotBlank() }.take(2)
        .mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")
    // "GestorFrota" vira "Gestor de Frota". Antes, sem usuário, aparecia "Administrador"
    // de exemplo.
    val perfilTexto = user?.perfil?.nome?.let { nomeDoPerfil(it) }.orEmpty()

    Scaffold(containerColor = BackgroundTela) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(AmbarDourado),
                contentAlignment = Alignment.Center
            ) {
                Text(iniciais.ifEmpty { "FF" }, color = Petroleo, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(nomeExibicao, color = TextoPrincipal, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(perfilTexto, color = TextoSecundario, fontSize = 14.sp)
            if (user?.email != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(user.email, color = TextoSecundario, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.weight(1f))

            if (onPendenciasClick != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SuperficieCard)
                        .clickable { onPendenciasClick() }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (pendentes > 0) Icons.Rounded.CloudUpload else Icons.Rounded.CloudDone,
                        contentDescription = null,
                        tint = Petroleo
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pendências de sincronização", color = TextoPrincipal, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(
                            when (pendentes) {
                                0 -> "Tudo sincronizado"
                                1 -> "1 alteração esperando para subir"
                                else -> "$pendentes alterações esperando para subir"
                            },
                            color = TextoSecundario,
                            fontSize = 13.sp
                        )
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = TextoSecundario)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Vermelho)
                    .clickable { onSairClick() },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sair da conta", color = Branco, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}