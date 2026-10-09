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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fleetflow.mobile.data.FinanceiroService
import com.fleetflow.mobile.data.MovimentacaoMensal
import com.fleetflow.mobile.data.PerfilUsuario
import com.fleetflow.mobile.data.ResumoFinanceiro
import com.fleetflow.mobile.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

@Composable
fun HomeScreen(
    perfil: PerfilUsuario,
    onPerfilClick: () -> Unit,
    onUsuariosClick: () -> Unit,
    onCategoriasClick: () -> Unit,
    onFormasPagamentoClick: () -> Unit,
    onMovimentacaoClick: () -> Unit,
    onFinanciamentoClick: () -> Unit
) {
    // Carrega o resumo financeiro com segurança para evitar crash se a lista estiver vazia
    val resumoFinanceiro = remember {
        try {
            FinanceiroService.obterResumo()
        } catch (e: Exception) {
            ResumoFinanceiro(0.0, 0.0, 0.0, 0.0)
        }
    }

    val projecaoMensal = remember {
        try {
            FinanceiroService.obterProjecaoMensal()
        } catch (e: Exception) {
            emptyList()
        }
    }

    val formatoMoeda = remember {
        NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    }

    Scaffold(containerColor = BackgroundTela) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {

            // CABEÇALHO
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(Petroleo)
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    "FleetFlow",
                    color = Branco,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AmbarDourado)
                        .clickable { onPerfilClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (perfil == PerfilUsuario.ADMINISTRADOR) "JP" else "MT",
                        color = Petroleo,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(modifier = Modifier.padding(20.dp)) {

                // SAUDAÇÃO
                Text(
                    text = if (perfil == PerfilUsuario.ADMINISTRADOR) "Olá, Joao Pedro" else "Olá, Motorista",
                    color = TextoPrincipal,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = if (perfil == PerfilUsuario.ADMINISTRADOR) "Administrador - FleetFlow" else "Motorista - FleetFlow",
                    color = TextoSecundario,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                )

                // =====================================================
                // VISÃO DO ADMINISTRADOR
                // =====================================================

                if (perfil == PerfilUsuario.ADMINISTRADOR) {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CardKpi(
                            "SALDO ATUAL",
                            formatoMoeda.format(resumoFinanceiro.saldoAtual),
                            VerdeEsmeralda,
                            null,
                            Modifier.weight(1f)
                        )
                        CardKpi(
                            "A RECEBER",
                            formatoMoeda.format(resumoFinanceiro.totalEntradasPendentes),
                            null,
                            null,
                            Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CardKpi(
                            "A PAGAR",
                            formatoMoeda.format(resumoFinanceiro.totalSaidasPendentes),
                            null,
                            null,
                            Modifier.weight(1f)
                        )
                        CardKpi("EM VIAGEM", "3", null, null, Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // INSIGHT
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
                            "Suas despesas com combustível subiram 12% em relação ao mês passado.",
                            color = TextoPrincipal,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // GRÁFICO
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SuperficieCard)
                            .padding(16.dp)
                    ) {
                        Text("RECEITAS X DESPESAS (PROJEÇÃO)", color = TextoSecundario, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row {
                            LegendaCor(VerdeEsmeralda, "Receita")
                            Spacer(modifier = Modifier.width(16.dp))
                            LegendaCor(Vermelho, "Despesa")
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Conversão para MovimentacaoMensal esperada pelo gráfico
                        GraficoReceitasDespesas(
                            dados = projecaoMensal.map {
                                MovimentacaoMensal(
                                    mesAbreviado = it.mes,
                                    receita = it.entradas,
                                    despesa = it.saidas
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // VER TODAS AS MOVIMENTAÇÕES
                    Button(
                        onClick = { },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AmbarDourado)
                    ) {
                        Text("Ver todas as movimentações", color = Petroleo, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // USUÁRIOS
                    Button(
                        onClick = onUsuariosClick,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Petroleo)
                    ) {
                        Text("Gestão de Usuários", color = Branco, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // CATEGORIAS E FORMAS DE PAGAMENTO
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onCategoriasClick,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AmbarDourado)
                        ) {
                            Text("Categorias", color = Petroleo, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }

                        Button(
                            onClick = onFormasPagamentoClick,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AmbarDourado)
                        ) {
                            Text("Formas Pgto.", color = Petroleo, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // MOVIMENTAÇÃO E FINANCIAMENTO
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onMovimentacaoClick,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Petroleo)
                        ) {
                            Text("Movimentação", color = Branco, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }

                        Button(
                            onClick = onFinanciamentoClick,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Petroleo)
                        ) {
                            Text("Financiamento", color = Branco, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }

                // =====================================================
                // VISÃO DO MOTORISTA
                // =====================================================

                if (perfil == PerfilUsuario.MOTORISTA) {

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SuperficieCard)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Área do Motorista", color = Petroleo, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Você está conectado com acesso restrito ao perfil de motorista.",
                                color = TextoSecundario,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            CardKpi("EM VIAGEM", "3", null, null, Modifier.fillMaxWidth())
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "Os módulos financeiros estão disponíveis apenas para usuários administradores.",
                        color = TextoSecundario,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CardKpi(
    titulo: String,
    valor: String,
    corVariacao: androidx.compose.ui.graphics.Color?,
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
private fun LegendaCor(cor: androidx.compose.ui.graphics.Color, texto: String) {
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