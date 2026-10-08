package com.fleetflow.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fleetflow.mobile.data.MockDataService
import com.fleetflow.mobile.ui.theme.*

@Composable
fun HomeScreen(onPerfilClick: () -> Unit, onUsuariosClick: () -> Unit) {
    Scaffold(containerColor = BackgroundTela) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Topo
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Petroleo)
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("FleetFlow", color = AmbarDourado, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Painel Operacional", color = Branco, fontSize = 13.sp)
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AmbarDourado)
                        .clickable { onPerfilClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("JP", color = Petroleo, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {

                // Card Resumo Principal
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SuperficieCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Resumo da Frota", color = TextoSecundario, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ItemResumo("Veículos", "24", "22 ativos")
                            ItemResumo("Motoristas", "18", "16 em rota")
                            ItemResumo("Alertas", "3", "2 urgentes")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Seção Ações Rápidas
                Text("Ações Rápidas", color = TextoPrincipal, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BotaoAcaoRapida(
                        titulo = "Gestão de\nUsuários",
                        icone = "👥",
                        modifier = Modifier.weight(1f),
                        onClick = onUsuariosClick
                    )
                    BotaoAcaoRapida(
                        titulo = "Meu\nPerfil",
                        icone = "👤",
                        modifier = Modifier.weight(1f),
                        onClick = onPerfilClick
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Card Movimentação Financeira (Gráfico)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SuperficieCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Movimentação Financeira", color = TextoPrincipal, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Semestral", color = TextoSecundario, fontSize = 11.sp)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        GraficoReceitasDespesas(dados = MockDataService.obterMovimentacoesMensais())

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            LegendaGrafico(cor = VerdeEsmeralda, texto = "Receita")
                            Spacer(modifier = Modifier.width(24.dp))
                            LegendaGrafico(cor = Vermelho, texto = "Despesa")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemResumo(titulo: String, valor: String, subtexto: String) {
    Column {
        Text(titulo, color = TextoSecundario, fontSize = 11.sp)
        Text(valor, color = Petroleo, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(subtexto, color = TextoSecundario, fontSize = 10.sp)
    }
}

@Composable
private fun BotaoAcaoRapida(
    titulo: String,
    icone: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SuperficieCard),
        border = BorderStroke(1.dp, CinzaSuave)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CinzaSuave),
                contentAlignment = Alignment.Center
            ) {
                Text(icone, fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(titulo, color = TextoPrincipal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun LegendaGrafico(cor: androidx.compose.ui.graphics.Color, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(cor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(texto, color = TextoSecundario, fontSize = 11.sp)
    }
}