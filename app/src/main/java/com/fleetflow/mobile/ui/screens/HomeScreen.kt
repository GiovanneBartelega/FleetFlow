package com.fleetflow.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fleetflow.mobile.data.model.*
import com.fleetflow.mobile.nomeDoPerfil
import com.fleetflow.mobile.data.repository.FinanceRepository
import com.fleetflow.mobile.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    usuario: UserResponseDto,
    podeUsuarios: Boolean,
    onMovimentacoes: () -> Unit,
    onPerfilClick: () -> Unit,
    onUsuariosClick: () -> Unit
) {
    var error by remember { mutableStateOf<String?>(null) }

    var periodoSelecionado by remember {
        mutableStateOf("7D")
    }

    LaunchedEffect(Unit) {
        runCatching {
            FinanceRepository.refreshSaldo()
        }.onFailure {
            error = if (it is java.io.IOException) {
                "Sem conexão com o servidor. Os valores serão atualizados quando a conexão voltar."
            } else {
                it.message ?: "Não foi possível atualizar os valores."
            }
        }
    }

    LaunchedEffect(periodoSelecionado) {
        FinanceRepository.carregarHistoricoDashboard(periodoSelecionado)
    }

    val saldo = FinanceRepository.saldo
    val historico = FinanceRepository.historicoDashboard
    val carregando = FinanceRepository.carregandoDashboard
    val semConexao = FinanceRepository.semConexaoDashboard
    val erroGrafico = FinanceRepository.erroDashboard
    val salvoEm = FinanceRepository.salvoEmDashboard
    val kpis = FinanceRepository.kpisDashboard
    val despesasPorCategoria = FinanceRepository.despesasCategoriaDashboard
    val rankings = FinanceRepository.rankingsDashboard
    val previsao = FinanceRepository.previsaoDashboard
    val alertasSaldo = FinanceRepository.alertasSaldoDashboard

    fun brl(valor: String?): String {
        return "R$ " + (valor ?: "0.00").replace('.', ',')
    }
    fun brl(valor: Double?): String = "R$ " + String.format(Locale("pt", "BR"), "%.2f", valor ?: 0.0)

    val dataAtualizacao = salvoEm?.let {
        SimpleDateFormat(
            "dd/MM/yyyy HH:mm",
            Locale("pt", "BR")
        ).format(Date(it))
    }

    Scaffold(
        containerColor = BackgroundTela
    ) { padding ->

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
                        .clickable {
                            onPerfilClick()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        usuario.nome
                            .split(" ")
                            .take(2)
                            .mapNotNull { it.firstOrNull() }
                            .joinToString("")
                            .uppercase(),
                        color = Petroleo,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Column(
                modifier = Modifier.padding(20.dp)
            ) {

                // SAUDAÇÃO

                Text(
                    "Olá, ${usuario.nome.trim().substringBefore(" ")}",
                    color = TextoPrincipal,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "${nomeDoPerfil(usuario.perfil.nome)} · FleetFlow",
                    color = TextoSecundario,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(
                        top = 4.dp,
                        bottom = 20.dp
                    )
                )

                // CARDS FINANCEIROS

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    CardKpi(
                        "SALDO ATUAL",
                        brl(saldo?.saldoAtual),
                        VerdeEsmeralda,
                        null,
                        Modifier.weight(1f)
                    )

                    CardKpi(
                        "RESULTADO DO PERÍODO",
                        brl(kpis?.resultado),
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
                        "RECEITAS DO PERÍODO",
                        brl(kpis?.receitas),
                        null,
                        null,
                        Modifier.weight(1f)
                    )

                    CardKpi(
                        "DESPESAS DO PERÍODO",
                        brl(kpis?.despesas),
                        null,
                        null,
                        Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                IndicadoresDetalhados(
                    despesas = despesasPorCategoria,
                    rankings = rankings,
                    previsao = previsao,
                    alertas = alertasSaldo,
                    brl = ::brl
                )

                Spacer(modifier = Modifier.height(16.dp))

                // INSIGHT

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(FundoInsight)
                        .border(
                            1.dp,
                            AmbarDourado,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp)
                ) {

                    Text(
                        "INSIGHT",
                        color = AmbarDourado,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        error ?: "O saldo atual considera receitas e despesas pagas. O previsto inclui lançamentos pendentes.",
                        color = TextoPrincipal,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // GRÁFICO FINANCEIRO

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SuperficieCard)
                        .padding(16.dp)
                ) {

                    Text(
                        "RECEITAS X DESPESAS",
                        color = TextoSecundario,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // SELETOR DE PERÍODO

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        PeriodoChip(
                            texto = "7 dias",
                            selecionado = periodoSelecionado == "7D",
                            modifier = Modifier.weight(1f)
                        ) {
                            periodoSelecionado = "7D"
                        }

                        PeriodoChip(
                            texto = "30 dias",
                            selecionado = periodoSelecionado == "30D",
                            modifier = Modifier.weight(1f)
                        ) {
                            periodoSelecionado = "30D"
                        }

                        PeriodoChip(
                            texto = "6 meses",
                            selecionado = periodoSelecionado == "6M",
                            modifier = Modifier.weight(1f)
                        ) {
                            periodoSelecionado = "6M"
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // LEGENDA

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        LegendaCor(
                            VerdeEsmeralda,
                            "Receita"
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        LegendaCor(
                            Vermelho,
                            "Despesa"
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // CARREGAMENTO

                    if (carregando && historico.isEmpty()) {

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {

                            CircularProgressIndicator(
                                color = AmbarDourado
                            )
                        }
                    } else if (historico.isNotEmpty()) {

                        GraficoReceitasDespesas(
                            dados = historico
                        )

                    } else {

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                            contentAlignment = Alignment.Center
                        ) {

                            Text(
                                if (carregando) {
                                    "Carregando histórico..."
                                } else {
                                    "Nenhuma movimentação paga encontrada neste período."
                                },
                                color = TextoSecundario,
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (carregando && historico.isNotEmpty()) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = AmbarDourado
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // SITUAÇÃO DA CONEXÃO

                    if (semConexao) {

                        Text(
                            "Sem conexão. Exibindo os últimos dados disponíveis.",
                            color = Vermelho,
                            fontSize = 11.sp
                        )
                    } else if (!erroGrafico.isNullOrBlank()) {

                        Text(
                            "Não foi possível atualizar o gráfico: $erroGrafico",
                            color = Vermelho,
                            fontSize = 11.sp
                        )
                    }

                    if (dataAtualizacao != null) {

                        Text(
                            "Última atualização: $dataAtualizacao",
                            color = TextoSecundario,
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // FINANCEIRO

                Button(
                    onClick = onMovimentacoes,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmbarDourado
                    )
                ) {

                    Text(
                        "Ver todas as movimentações",
                        color = Petroleo,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // GESTÃO DE USUÁRIOS

                if (podeUsuarios) {

                    Button(
                        onClick = onUsuariosClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Petroleo
                        )
                    ) {

                        Text(
                            "Gestão de Usuários",
                            color = Branco,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun PeriodoChip(
    texto: String,
    selecionado: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {

    FilterChip(
        selected = selecionado,
        onClick = onClick,
        label = {
            Text(
                texto,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AmbarDourado,
            selectedLabelColor = Petroleo,
            containerColor = BackgroundTela,
            labelColor = TextoSecundario
        )
    )
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

        Text(
            titulo,
            color = TextoSecundario,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            valor,
            color = TextoPrincipal,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        if (variacao != null && corVariacao != null) {

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                variacao,
                color = corVariacao,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun LegendaCor(
    cor: Color,
    texto: String
) {

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(cor)
        )

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            texto,
            color = TextoSecundario,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun IndicadoresDetalhados(
    despesas: List<DespesaCategoriaDto>,
    rankings: RankingsDto?,
    previsao: PrevisaoCaixaDto?,
    alertas: List<AlertaSaldoDto>,
    brl: (Double?) -> String
) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SuperficieCard).padding(16.dp)
    ) {
        Text("INDICADORES", color = TextoSecundario, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text("Despesas por categoria", color = TextoPrincipal, fontWeight = FontWeight.SemiBold)
        if (despesas.isEmpty()) Text("Sem despesas pagas no período.", color = TextoSecundario, fontSize = 12.sp)
        despesas.take(3).forEach { item ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.categoria, color = TextoSecundario, fontSize = 12.sp)
                Text("${brl(item.valor)} (${String.format(Locale("pt", "BR"), "%.0f", item.percentual)}%)", color = TextoPrincipal, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Veículos mais rentáveis", color = TextoPrincipal, fontWeight = FontWeight.SemiBold)
        if (rankings?.maisRentaveis.isNullOrEmpty()) Text("Sem veículos com lançamentos no período.", color = TextoSecundario, fontSize = 12.sp)
        rankings?.maisRentaveis?.take(3)?.forEach { item ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.placa, color = TextoSecundario, fontSize = 12.sp)
                Text(brl(item.resultado), color = VerdeEsmeralda, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(12.dp))
        val ultimoSaldo = previsao?.previsao?.lastOrNull()?.saldoProjetado
        Text("Previsão de caixa em 12 meses: ${brl(ultimoSaldo)}", color = TextoPrincipal, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        alertas.firstOrNull()?.let { alerta ->
            Text(alerta.mensagem, color = Vermelho, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
