package com.fleetflow.mobile.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fleetflow.mobile.R
import com.fleetflow.mobile.ui.theme.*

@Composable
fun WelcomeScreen(onComecarClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Petroleo)
    ) {

        // Foto de fundo, mostrada inteira (sem cortar), centralizada.
        // O espaço que sobra nas bordas fica na cor Petroleo, não em branco.
        Image(
            painter = painterResource(id = R.drawable.caminhao_azul),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.81f)
                .align(Alignment.TopCenter)
        )

        // Véu mais leve no topo/meio, mais forte embaixo
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Petroleo.copy(alpha = 0.35f),
                            Petroleo.copy(alpha = 0.10f),
                            Petroleo.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp)
        ) {

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_icone),
                    contentDescription = "Logo do FleetFlow",
                    modifier = Modifier.size(74.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "FleetFlow",
                    color = AmbarDourado,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    "GESTÃO DE FROTA",
                    color = Branco,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Mais controle para sua frota,\nmais eficiência para o seu negócio.",
                color = Branco,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Petroleo.copy(alpha = 0.88f))
                    .padding(20.dp)
            ) {
                val beneficios = listOf(
                    "📍" to "Acompanhamento em tempo real da sua frota",
                    "🔧" to "Controle de manutenção e revisões",
                    "📈" to "Mais eficiência e redução de custos"
                )

                beneficios.forEachIndexed { index, (icone, texto) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AmbarDourado),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(icone, fontSize = 20.sp)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(texto, color = Branco, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (index != beneficios.lastIndex) {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onComecarClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AmbarDourado)
                ) {
                    Text("Comece Agora  →", color = Petroleo, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}