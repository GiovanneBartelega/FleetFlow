package com.fleetflow.mobile.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fleetflow.mobile.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var carregando by remember { mutableStateOf(false) }

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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .border(width = 2.dp, color = Petroleo, shape = RoundedCornerShape(12.dp))
                    .clickable(enabled = !carregando) { carregando = true },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("G", color = AzulGoogle, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Entrar com Google", color = Petroleo, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }

            if (carregando) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator(color = AmbarDourado)
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

    LaunchedEffect(carregando) {
        if (carregando) {
            delay(600)
            onLoginSuccess()
        }
    }
}
