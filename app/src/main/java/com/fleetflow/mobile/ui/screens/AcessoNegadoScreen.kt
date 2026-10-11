package com.fleetflow.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fleetflow.mobile.ui.theme.*

@Composable
fun AcessoNegadoScreen(onVoltarClick: () -> Unit) {
    Scaffold(containerColor = BackgroundTela) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🚫", fontSize = 56.sp)

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Acesso negado",
                color = TextoPrincipal,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Seu perfil não tem permissão para acessar essa área do FleetFlow.",
                color = TextoSecundario,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier
                    .background(Petroleo, RoundedCornerShape(12.dp))
                    .clickable { onVoltarClick() }
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text("Voltar", color = Branco, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}