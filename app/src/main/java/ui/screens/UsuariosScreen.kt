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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fleetflow.mobile.data.MockDataService
import com.fleetflow.mobile.data.Usuario
import com.fleetflow.mobile.data.obterUsuarios
import com.fleetflow.mobile.ui.theme.*

@Composable
fun UsuariosScreen(onVoltarClick: () -> Unit) {
    var usuarios by remember { mutableStateOf(MockDataService.obterUsuarios()) }

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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(usuarios) { usuario ->
                CardUsuario(
                    usuario = usuario,
                    onAprovarClick = {
                        usuarios = usuarios.map {
                            if (it.id == usuario.id) it.copy(status = "Aprovado") else it
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun CardUsuario(usuario: Usuario, onAprovarClick: () -> Unit) {
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
                    usuario.nome.take(1),
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
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(usuario.perfil, color = CinzaBadge, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.weight(1f))

            if (usuario.status == "Pendente") {
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
    val cor = if (status == "Aprovado") VerdeSuave else FundoInsight
    val corTexto = if (status == "Aprovado") VerdeEsmeralda else AmbarAlerta

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(cor)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(status, color = corTexto, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}