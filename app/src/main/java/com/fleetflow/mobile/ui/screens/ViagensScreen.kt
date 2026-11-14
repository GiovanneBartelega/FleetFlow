package com.fleetflow.mobile.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fleetflow.mobile.data.model.ViagemDto
import com.fleetflow.mobile.data.repository.FinanceRepository
import com.fleetflow.mobile.data.repository.ViagensRepository
import kotlinx.coroutines.launch

@Composable
fun ViagensScreen(somenteLeitura: Boolean = false) {

    var busca by remember { mutableStateOf("") }
    var filtro by remember { mutableStateOf("Todas") }
    var erro by remember { mutableStateOf<String?>(null) }
    var viagemSelecionada by remember { mutableStateOf<ViagemDto?>(null) }

    LaunchedEffect(Unit) {
        try {
            ViagensRepository.carregar()
        } catch (e: Exception) {
            erro = e.message ?: "Não foi possível carregar as viagens."
        }
    }

    BackHandler(enabled = viagemSelecionada != null) {
        viagemSelecionada = null
    }

    val viagens = ViagensRepository.viagens

    if (viagemSelecionada != null) {
        DetalheViagemScreen(
            viagem = viagemSelecionada!!,
            onVoltar = { viagemSelecionada = null }
        )
        return
    }

    val lista = viagens.filter { viagem ->

        val termo = busca.trim()

        val correspondeBusca =
            termo.isEmpty() ||
                    viagem.id.contains(termo, true) ||
                    viagem.veiculo.placa.contains(termo, true) ||
                    viagem.motorista.nome.contains(termo, true) ||
                    viagem.rota.origem.contains(termo, true) ||
                    viagem.rota.destino.contains(termo, true)

        val correspondeFiltro = when (filtro) {
            "Em andamento" -> viagem.status == "Aberta"
            "Concluídas" -> viagem.status == "Encerrada"
            else -> true
        }

        correspondeBusca && correspondeFiltro
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {

        Text(
            text = "Viagens",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IndicadorViagem(
                "Em andamento",
                viagens.count { it.status == "Aberta" },
                Modifier.weight(1f)
            )

            IndicadorViagem(
                "Concluídas",
                viagens.count { it.status == "Encerrada" },
                Modifier.weight(1f)
            )

            IndicadorViagem(
                "Total",
                viagens.size,
                Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = busca,
            onValueChange = { busca = it },
            label = { Text("Buscar viagem, placa ou motorista") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("Todas", "Em andamento", "Concluídas").forEach { opcao ->

                FilterChip(
                    selected = filtro == opcao,
                    onClick = { filtro = opcao },
                    label = { Text(opcao, maxLines = 1) }
                )
            }
        }

        erro?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        if (ViagensRepository.carregando) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )
        }

        if (!ViagensRepository.carregando && lista.isEmpty()) {
            Text(
                "Nenhuma viagem encontrada.",
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(
                top = 12.dp,
                bottom = 24.dp
            )
        ) {
            items(lista, key = { it.id }) { viagem ->

                CartaoViagemApi(
                    viagem = viagem,
                    onClick = {
                        viagemSelecionada = viagem
                    }
                )
            }
        }
    }
}

// ============================================================
// INDICADORES
// ============================================================

@Composable
private fun IndicadorViagem(
    titulo: String,
    quantidade: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(78.dp)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                quantidade.toString(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                titulo,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

// ============================================================
// CARTÃO DA VIAGEM
// ============================================================

@Composable
private fun CartaoViagemApi(
    viagem: ViagemDto,
    onClick: () -> Unit
) {
    val encerrada = viagem.status == "Encerrada"

    val cor = if (encerrada) {
        Color(0xFF2E7D32)
    } else {
        Color(0xFF1565C0)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            Modifier.padding(12.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Viagem ${viagem.id.take(8)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                Surface(
                    color = cor.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Text(
                        if (encerrada) "Encerrada" else "Em andamento",
                        color = cor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(
                            horizontal = 8.dp,
                            vertical = 5.dp
                        )
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "${viagem.veiculo.marca} ${viagem.veiculo.modelo}",
                fontWeight = FontWeight.SemiBold
            )

            Text("Placa: ${viagem.veiculo.placa}")

            Text(
                "${viagem.rota.origem} → ${viagem.rota.destino}",
                modifier = Modifier.padding(top = 4.dp)
            )

            Text(
                "Motorista: ${viagem.motorista.nome}",
                modifier = Modifier.padding(top = 4.dp)
            )

            Text(
                "Hodômetro inicial: ${viagem.hodometroInicial}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )

            viagem.hodometroFinal?.let {
                Text(
                    "Hodômetro final: $it",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text(
                "Toque para ver os detalhes",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

// ============================================================
// DETALHES DA VIAGEM
// ============================================================

@Composable
private fun DetalheViagemScreen(
    viagem: ViagemDto,
    onVoltar: () -> Unit
) {
    var movimentacoes by remember(viagem.id) {
        mutableStateOf<List<Movimentacao>>(emptyList())
    }

    var carregandoFinanceiro by remember {
        mutableStateOf(true)
    }

    var erroFinanceiro by remember {
        mutableStateOf<String?>(null)
    }

    val scope = rememberCoroutineScope()

    LaunchedEffect(viagem.id) {
        carregandoFinanceiro = true
        erroFinanceiro = null

        runCatching {
            FinanceRepository.movimentacoesDaViagem(viagem.id)
        }.onSuccess {
            movimentacoes = it
        }.onFailure {
            erroFinanceiro = it.message ?: "Erro ao carregar movimentações."
        }

        carregandoFinanceiro = false
    }

    val entradas = movimentacoes
        .filter { it.tipo == Tipo.ENTRADA }
        .sumOf { it.valorCentavos }

    val saidas = movimentacoes
        .filter { it.tipo == Tipo.SAIDA }
        .sumOf { it.valorCentavos }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onVoltar) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Voltar"
                )
            }

            Text(
                "Detalhes da viagem",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            "Identificação",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LinhaDetalhe("Código", viagem.id.take(8))
                LinhaDetalhe("Status", viagem.status)
                LinhaDetalhe("Data de abertura", viagem.dataAbertura)
                LinhaDetalhe(
                    "Data de encerramento",
                    viagem.dataFechamento ?: "Em andamento"
                )
                LinhaDetalhe(
                    "Observação",
                    viagem.observacao ?: "Nenhuma"
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "Rota",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LinhaDetalhe("Nome", viagem.rota.nome)
                LinhaDetalhe("Origem", viagem.rota.origem)
                LinhaDetalhe("Destino", viagem.rota.destino)
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "Veículo e motorista",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LinhaDetalhe(
                    "Veículo",
                    "${viagem.veiculo.marca} ${viagem.veiculo.modelo}"
                )
                LinhaDetalhe("Placa", viagem.veiculo.placa)
                LinhaDetalhe("Motorista", viagem.motorista.nome)
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "Hodômetro",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LinhaDetalhe(
                    "Inicial",
                    viagem.hodometroInicial.toString()
                )
                LinhaDetalhe(
                    "Final",
                    viagem.hodometroFinal?.toString() ?: "Não informado"
                )

                if (viagem.hodometroFinal != null) {
                    LinhaDetalhe(
                        "Distância percorrida",
                        "${viagem.hodometroFinal - viagem.hodometroInicial} km"
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        HorizontalDivider()

        Spacer(Modifier.height(16.dp))

        Text(
            "Financeiro da viagem",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(12.dp))

        if (carregandoFinanceiro) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        }

        erroFinanceiro?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Receitas")
                    Text(
                        centsToBRL(entradas),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Despesas")
                    Text(
                        centsToBRL(saidas),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            "Resultado: ${centsToBRL(entradas - saidas)}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(12.dp))

        if (!carregandoFinanceiro && movimentacoes.isEmpty() && erroFinanceiro == null) {
            Text("Nenhuma movimentação vinculada a esta viagem.")
        }

        movimentacoes.forEach { movimento ->

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(
                    Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        movimento.descricao,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        "${movimento.categoria} • ${movimento.data}",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        (if (movimento.tipo == Tipo.ENTRADA) "+" else "-") +
                                centsToBRL(movimento.valorCentavos),
                        color = if (movimento.tipo == Tipo.ENTRADA) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        if (movimento.status == StatusMov.PAGO) "Pago/Recebido" else "Pendente",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ============================================================
// LINHA DE DETALHE
// ============================================================

@Composable
private fun LinhaDetalhe(
    titulo: String,
    valor: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            titulo,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.width(12.dp))

        Text(
            valor,
            fontWeight = FontWeight.Medium
        )
    }
}