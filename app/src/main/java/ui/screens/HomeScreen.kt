package com.fleetflow.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fleetflow.mobile.data.MockDataService
import com.fleetflow.mobile.data.model.UserResponseDto
import com.fleetflow.mobile.ui.theme.*

@Composable
fun HomeScreen(
    user: UserResponseDto? = null,
    onPerfilClick: () -> Unit,
    onUsuariosClick: () -> Unit
) {
    val nomeExibicao = user?.name ?: "Joao Pedro"
    val iniciais = nomeExibicao.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")
    val perfilTexto = if (user?.role == "ADMINISTRATOR") "Administrador" else user?.role ?: "Administrador"

    Scaffold(containerColor = BackgroundTela) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(Petroleo)
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("FleetFlow", color = Branco, fontSize = 18.sp, fontWeight = FontWeight.Bold)

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AmbarDourado)
                        .clickable { onPerfilClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(iniciais.ifEmpty { "JP" }, color = Petroleo, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Column(modifier = Modifier.padding(20.dp)) {

                Text("Ola, $nomeExibicao", color = TextoPrincipal, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "$perfilTexto - FleetFlow",
                    color = TextoSecundario,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CardKpi("SALDO ATUAL", "R$ 32.850", VerdeEsmeralda, "+8,4%", Modifier.weight(1f))
                    CardKpi("A RECEBER", "R$ 12.400", null, null, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CardKpi("A PAGAR", "R$ 8.150", null, null, Modifier.weight(1f))
                    CardKpi("EM VIAGEM", "3", null, null, Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(FundoInsight)
                        .border(1.dp, AmbarDourado, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Text("INSIGHT", color = AmbarDourado, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Suas despesas com combustivel subiram 12% em relacao ao mes passado.",
                        color = TextoPrincipal,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SuperficieCard)
                        .padding(16.dp)
                ) {
                    Text("RECEITAS X DESPESAS", color = TextoSecundario, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row {
                        LegendaCor(VerdeEsmeralda, "Receita")
                        Spacer(modifier = Modifier.width(16.dp))
                        LegendaCor(Vermelho, "Despesa")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    GraficoReceitasDespesas(dados = MockDataService.obterMovimentacoesMensais())
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AmbarDourado)
                ) {
                    Text("Ver todas as movimentacoes", color = Petroleo, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onUsuariosClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Petroleo)
                ) {
                    Text("Gestao de Usuarios", color = Branco, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun CardKpi(
    titulo: String,
    valor: String,
    corVariacao: Color?,
    variacao: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SuperficieCard)
            .padding(14.dp)
    ) {
        Text(titulo, color = TextoSecundario, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(valor, color = TextoPrincipal, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (variacao != null && corVariacao != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(variacao, color = corVariacao, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun LegendaCor(cor: Color, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(cor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(texto, color = TextoSecundario, fontSize = 12.sp)
    }
}
