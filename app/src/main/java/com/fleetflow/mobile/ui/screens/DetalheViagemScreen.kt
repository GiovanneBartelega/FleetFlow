package com.fleetflow.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fleetflow.mobile.data.local.ViagemEntity
import com.fleetflow.mobile.data.repository.FalhaPainel
import com.fleetflow.mobile.data.repository.FinanceRepository

// Detalhe da viagem com os lançamentos vinculados a ela (Douglas, 14-15/11). Na junção de
// 18/11: datas, placa e hodômetro no mesmo formato da lista, e a falha de rede em português.
@Composable
fun DetalheViagemScreen(
    viagem: ViagemEntity,
    onVoltar: () -> Unit
) {
    var movimentacoes by remember(viagem.id) {
        mutableStateOf<List<Movimentacao>>(emptyList())
    }

    var carregandoFinanceiro by remember {
        mutableStateOf(false)
    }

    var erroFinanceiro by remember {
        mutableStateOf<String?>(null)
    }

    var mensagemOffline by remember {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(viagem.idServidor) {
        movimentacoes = emptyList()
        erroFinanceiro = null
        mensagemOffline = null

        val servidorId = viagem.idServidor

        if (servidorId.isNullOrBlank()) {
            mensagemOffline =
                "Os dados financeiros estarão disponíveis após a sincronização da viagem."
            carregandoFinanceiro = false
            return@LaunchedEffect
        }

        carregandoFinanceiro = true

        runCatching {
            FinanceRepository.movimentacoesDaViagem(servidorId)
        }.onSuccess {
            movimentacoes = it
        }.onFailure {
            // Sem rede, o OkHttp diz "Failed to connect to /10.0.2.2:3000".
            erroFinanceiro = FalhaPainel.de(it).mensagem
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

        Spacer(Modifier.height(16.dp))

        SecaoDetalheViagem("Identificação") {
            LinhaDetalheViagem("Código", viagem.id.take(8))
            LinhaDetalheViagem("Status", viagem.status)
            LinhaDetalheViagem("Data de abertura", dataHoraViagem(viagem.dataAbertura))
            LinhaDetalheViagem(
                "Data de encerramento",
                viagem.dataFechamento?.let { dataHoraViagem(it) } ?: "Em andamento"
            )
            LinhaDetalheViagem(
                "Observação",
                viagem.observacao?.takeIf { it.isNotBlank() } ?: "Nenhuma"
            )
        }

        Spacer(Modifier.height(16.dp))

        SecaoDetalheViagem("Rota") {
            LinhaDetalheViagem("Nome", viagem.rotaNome)
            LinhaDetalheViagem("Origem", viagem.rotaOrigem)
            LinhaDetalheViagem("Destino", viagem.rotaDestino)
        }

        Spacer(Modifier.height(16.dp))

        SecaoDetalheViagem("Veículo e motorista") {
            LinhaDetalheViagem(
                "Veículo",
                "${viagem.veiculoMarca} ${viagem.veiculoModelo}"
            )
            LinhaDetalheViagem("Placa", placaViagem(viagem.veiculoPlaca))
            LinhaDetalheViagem("Motorista", viagem.motoristaNome)
        }

        Spacer(Modifier.height(16.dp))

        SecaoDetalheViagem("Hodômetro") {
            LinhaDetalheViagem(
                "Inicial",
                "${kmViagem(viagem.hodometroInicial)} km"
            )

            LinhaDetalheViagem(
                "Final",
                viagem.hodometroFinal?.let { "${kmViagem(it)} km" } ?: "Não informado"
            )

            viagem.hodometroFinal?.let { final ->
                LinhaDetalheViagem(
                    "Distância percorrida",
                    "${kmViagem(final - viagem.hodometroInicial)} km"
                )
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

        mensagemOffline?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        erroFinanceiro?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
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

        if (
            !carregandoFinanceiro &&
            movimentacoes.isEmpty() &&
            erroFinanceiro == null &&
            mensagemOffline == null
        ) {
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
                        if (movimento.status == StatusMov.PAGO) {
                            "Pago/Recebido"
                        } else {
                            "Pendente"
                        },
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SecaoDetalheViagem(
    titulo: String,
    conteudo: @Composable () -> Unit
) {
    Column {
        Text(
            titulo,
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
                conteudo()
            }
        }
    }
}

@Composable
private fun LinhaDetalheViagem(
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

        Spacer(Modifier.weight(1f))

        Text(
            valor,
            fontWeight = FontWeight.Medium
        )
    }
}