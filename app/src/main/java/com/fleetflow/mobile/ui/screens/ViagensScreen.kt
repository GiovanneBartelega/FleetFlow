//kotlin
package com.fleetflow.mobile.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID
import com.fleetflow.mobile.ui.screens.*
data class Viagem(
    val id: String,
    val codigo: String,
    val veiculo: String,
    val placa: String,
    val motorista: String,
    val origem: String,
    val destino: String,
    val dataSaida: String,
    val status: String
)

private val STATUS_VIAGEM = listOf(
    "Agendada",
    "Em andamento",
    "Concluída",
    "Cancelada"
)

object ViagemRepo {
    val itens = mutableStateListOf(
        Viagem(
            "1", "VG-2026-001", "Volvo FH 540", "ABC1D23",
            "Carlos Oliveira", "Varginha - MG", "Belo Horizonte - MG",
            "20/10/2026", "Em andamento"
        ),
        Viagem(
            "2", "VG-2026-002", "Mercedes-Benz Sprinter", "FGH4I56",
            "Marcos Santos", "Lavras - MG", "São Paulo - SP",
            "21/10/2026", "Agendada"
        ),
        Viagem(
            "3", "VG-2026-003", "Scania R450", "JKL7M89",
            "João Ferreira", "Varginha - MG", "Campinas - SP",
            "18/10/2026", "Concluída"
        )
    )

    fun salvar(viagem: Viagem) {
        val indice = itens.indexOfFirst { it.id == viagem.id }
        if (indice >= 0) {
            itens[indice] = viagem
        } else {
            itens.add(0, viagem)
        }
    }

    fun remover(id: String) {
        itens.removeAll { it.id == id }
    }
}

private fun corStatusViagem(status: String): Color = when (status) {
    "Agendada" -> Color(0xFFEF8F00)
    "Em andamento" -> Color(0xFF1565C0)
    "Concluída" -> Color(0xFF2E7D32)
    else -> Color(0xFFC62828)
}

@Composable
fun ViagensScreen(somenteLeitura: Boolean = false) {
    var busca by remember { mutableStateOf("") }
    var filtro by remember { mutableStateOf("Todas") }
    var criando by remember { mutableStateOf(false) }
    var editando by remember { mutableStateOf<Viagem?>(null) }
    var excluindo by remember { mutableStateOf<Viagem?>(null) }

    val lista = ViagemRepo.itens.filter { viagem ->
        val termo = busca.trim()
        val correspondeBusca = termo.isEmpty() ||
                viagem.codigo.contains(termo, true) ||
                viagem.placa.contains(termo, true) ||
                viagem.motorista.contains(termo, true) ||
                viagem.origem.contains(termo, true) ||
                viagem.destino.contains(termo, true)

        val correspondeFiltro =
            filtro == "Todas" || viagem.status == filtro

        correspondeBusca && correspondeFiltro
    }

    Box(Modifier.fillMaxSize()) {
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
                    titulo = "Em andamento",
                    quantidade = ViagemRepo.itens.count {
                        it.status == "Em andamento"
                    },
                    modifier = Modifier.weight(1f)
                )
                IndicadorViagem(
                    titulo = "Agendadas",
                    quantidade = ViagemRepo.itens.count {
                        it.status == "Agendada"
                    },
                    modifier = Modifier.weight(1f)
                )
                IndicadorViagem(
                    titulo = "Concluídas",
                    quantidade = ViagemRepo.itens.count {
                        it.status == "Concluída"
                    },
                    modifier = Modifier.weight(1f)
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
                listOf(
                    "Todas", "Agendada", "Em andamento",
                    "Concluída", "Cancelada"
                ).forEach { opcao ->
                    androidx.compose.material3.FilterChip(
                        selected = filtro == opcao,
                        onClick = { filtro = opcao },
                        label = {
                            Text(
                                text = opcao,
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        modifier = Modifier
                    )
                }
            }


            if (lista.isEmpty()) {
                Text(
                    "Nenhuma viagem encontrada.",
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
            ) {
                items(lista, key = { it.id }) { viagem ->
                    CartaoViagem(
                        viagem = viagem,
                        somenteLeitura = somenteLeitura,
                        onEditar = { editando = viagem },
                        onExcluir = { excluindo = viagem }
                    )
                }
            }
        }

        if (!somenteLeitura) {
            FloatingActionButton(
                onClick = { criando = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Nova viagem")
            }
        }
    }

    if (criando) {
        FormularioViagem(
            inicial = null,
            onSalvar = {
                ViagemRepo.salvar(it)
                criando = false
            },
            onCancelar = { criando = false }
        )
    }

    editando?.let { viagem ->
        FormularioViagem(
            inicial = viagem,
            onSalvar = {
                ViagemRepo.salvar(it)
                editando = null
            },
            onCancelar = { editando = null }
        )
    }

    excluindo?.let { viagem ->
        AlertDialog(
            onDismissRequest = { excluindo = null },
            title = { Text("Excluir viagem?") },
            text = {
                Text("A viagem ${viagem.codigo} será removida.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        ViagemRepo.remover(viagem.id)
                        excluindo = null
                    }
                ) {
                    Text("Excluir")
                }
            },
            dismissButton = {
                TextButton(onClick = { excluindo = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

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
                text = quantidade.toString(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = titulo,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun CartaoViagem(
    viagem: Viagem,
    somenteLeitura: Boolean,
    onEditar: () -> Unit,
    onExcluir: () -> Unit
) {
    val cor = corStatusViagem(viagem.status)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    viagem.codigo,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                Surface(
                    color = cor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        viagem.status,
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
                "${viagem.veiculo} · ${viagem.placa}",
                fontWeight = FontWeight.SemiBold
            )

            Text(
                "${viagem.origem} → ${viagem.destino}",
                modifier = Modifier.padding(top = 4.dp)
            )

            Text(
                "Motorista: ${viagem.motorista}",
                modifier = Modifier.padding(top = 4.dp)
            )

            Text(
                "Saída: ${viagem.dataSaida}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (!somenteLeitura) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onEditar) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Editar viagem"
                        )
                    }

                    IconButton(onClick = onExcluir) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Excluir viagem"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CampoViagem(
    rotulo: String,
    valor: String,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onChange,
        label = { Text(rotulo) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun FormularioViagem(
    inicial: Viagem?,
    onSalvar: (Viagem) -> Unit,
    onCancelar: () -> Unit
) {
    var codigo by remember { mutableStateOf(inicial?.codigo ?: "") }
    var veiculo by remember { mutableStateOf(inicial?.veiculo ?: "") }
    var placa by remember { mutableStateOf(inicial?.placa ?: "") }
    var motorista by remember { mutableStateOf(inicial?.motorista ?: "") }
    var origem by remember { mutableStateOf(inicial?.origem ?: "") }
    var destino by remember { mutableStateOf(inicial?.destino ?: "") }
    var dataSaida by remember {
        mutableStateOf(inicial?.dataSaida ?: "")
    }
    var status by remember {
        mutableStateOf(inicial?.status ?: "Agendada")
    }
    var erro by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = {
            Text(if (inicial == null) "Nova viagem" else "Editar viagem")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CampoViagem("Código da viagem", codigo) { codigo = it }
                CampoViagem("Veículo", veiculo) { veiculo = it }
                CampoViagem("Placa", placa) {
                    placa = it.uppercase().filter(Char::isLetterOrDigit).take(7)
                }
                CampoViagem("Motorista", motorista) { motorista = it }
                CampoViagem("Origem", origem) { origem = it }
                CampoViagem("Destino", destino) { destino = it }
                CampoViagem("Data de saída", dataSaida) { dataSaida = it }

                Text(
                    "Status",
                    fontWeight = FontWeight.Bold
                )

                STATUS_VIAGEM.forEach { opcao ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { status = opcao },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = status == opcao,
                            onClick = { status = opcao }
                        )
                        Text(opcao)
                    }
                }

                erro?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    erro = when {
                        codigo.isBlank() -> "Informe o código da viagem."
                        veiculo.isBlank() -> "Informe o veículo."
                        placa.length != 7 -> "Informe uma placa válida."
                        motorista.isBlank() -> "Informe o motorista."
                        origem.isBlank() -> "Informe a origem."
                        destino.isBlank() -> "Informe o destino."
                        origem.equals(destino, ignoreCase = true) ->
                            "A origem e o destino devem ser diferentes."
                        dataSaida.isBlank() -> "Informe a data de saída."
                        ViagemRepo.itens.any {
                            it.codigo.equals(codigo.trim(), true) &&
                                    it.id != inicial?.id
                        } -> "Já existe uma viagem com esse código."
                        else -> null
                    }

                    if (erro == null) {
                        onSalvar(
                            Viagem(
                                id = inicial?.id ?: UUID.randomUUID().toString(),
                                codigo = codigo.trim(),
                                veiculo = veiculo.trim(),
                                placa = placa,
                                motorista = motorista.trim(),
                                origem = origem.trim(),
                                destino = destino.trim(),
                                dataSaida = dataSaida.trim(),
                                status = status
                            )
                        )
                    }
                }
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) {
                Text("Cancelar")
            }
        }
    )
}

