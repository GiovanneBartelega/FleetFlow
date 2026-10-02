package com.fleetflow.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.fleetflow.mobile.ui.theme.*

@Composable
fun PerfilScreen(
    user: UserResponseDto? = null,
    onSairClick: () -> Unit
) {
    val nomeExibicao = user?.name ?: "Nome do Usuário"
    val iniciais = nomeExibicao.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")
    val perfilTexto = if (user?.role == "ADMINISTRATOR") "Administrador" else user?.role ?: "Administrador"

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
                Text(iniciais.ifEmpty { "JP" }, color = Petroleo, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(nomeExibicao, color = TextoPrincipal, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(perfilTexto, color = TextoSecundario, fontSize = 14.sp)
            if (user?.email != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(user.email, color = TextoSecundario, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.weight(1f))

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
