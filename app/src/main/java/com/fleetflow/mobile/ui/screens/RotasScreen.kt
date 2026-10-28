package com.fleetflow.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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

data class Rota(
    val id: String,
    val nome: String,
    val origem: String,
    val destino: String,
    val distanciaKm: Int,
    val duracaoHoras: Int,
    val pedagioReais: Int,
    val ativa: Boolean
)

// ÚNICO lugar que mexe nos dados de rotas. Hoje são dados de exemplo em memória
// (somem ao fechar o app). Quando a API existir, é só trocar o corpo destas funções.
object RotaRepo {
    val itens = mutableStateListOf(
        Rota("1", "Varginha → Belo Horizonte", "Varginha - MG", "Belo Horizonte - MG", 305, 4, 60, true),
        Rota("2", "Lavras → São Paulo", "Lavras - MG", "São Paulo - SP", 400, 6, 90, true),
        Rota("3", "Varginha → Campinas", "Varginha - MG", "Campinas - SP", 330, 4, 70, false)
    )

    fun salvar(r: Rota) {
        val i = itens.indexOfFirst { it.id == r.id }
        if (i >= 0) itens[i] = r else itens.add(0, r)
    }

    fun remover(id: String) {
        itens.removeAll { it.id == id }
    }

    fun parExiste(origem: String, destino: String, ignorarId: String?): Boolean =
        itens.any {
            it.id != ignorarId &&
                    it.origem.trim().equals(origem.trim(), ignoreCase = true) &&
                    it.destino.trim().equals(destino.trim(), ignoreCase = true)
        }
}

// Lê as viagens sem alterar a tela de Viagens: compara origem e destino.
private fun viagensDaRota(r: Rota): Int =
    ViagemRepo.itens.count {
        it.origem.trim().equals(r.origem.trim(), ignoreCase = true) &&
                it.destino.trim().equals(r.destino.trim(), ignoreCase = true)
    }

@Composable
fun RotasScreen(somenteLeitura: Boolean = false) {
    var busca by remember { mutableStateOf("") }
    var criando by remember { mutableStateOf(false) }
    var editando by remember { mutableStateOf<Rota?>(null) }
    var excluindo by remember { mutableStateOf<Rota?>(null) }

    val termo = busca.trim()
    val lista = RotaRepo.itens.filter {
        termo.isEmpty() ||
                it.nome.contains(termo, ignoreCase = true) ||
                it.origem.contains(termo, ignoreCase = true) ||
                it.destino.contains(termo, ignoreCase = true)
    }
    val ativas = RotaRepo.itens.count { it.ativa }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Text(
                "Rotas",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                "${RotaRepo.itens.size} rotas · $ativas ativas",
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = busca,
                onValueChange = { busca = it },
                label = { Text("Buscar por nome, origem ou destino") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (lista.isEmpty()) {
                Text("Nenhuma rota encontrada.", modifier = Modifier.padding(top = 16.dp))
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
            ) {
                items(lista, key = { it.id }) { r ->
                    CartaoRota(
                        r = r,
                        somenteLeitura = somenteLeitura,
                        onEditar = { editando = r },
                        onExcluir = { excluindo = r }
                    )
                }
            }
        }
        if (!somenteLeitura) {
            FloatingActionButton(
                onClick = { criando = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Nova rota")
            }
        }
    }

    if (criando) {
        FormularioRota(
            inicial = null,
            onSalvar = { RotaRepo.salvar(it); criando = false },
            onCancelar = { criando = false }
        )
    }
    editando?.let { atual ->
        FormularioRota(
            inicial = atual,
            onSalvar = { RotaRepo.salvar(it); editando = null },
            onCancelar = { editando = null }
        )
    }
    excluindo?.let { alvo ->
        val usadas = viagensDaRota(alvo)
        AlertDialog(
            onDismissRequest = { excluindo = null },
            title = { Text("Excluir rota?") },
            text = {
                Text(
                    if (usadas > 0) {
                        "${alvo.nome} será removida. As $usadas viagem(ns) com esse trajeto continuam existindo."
                    } else {
                        "${alvo.nome} será removida."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { RotaRepo.remover(alvo.id); excluindo = null }) {
                    Text("Excluir")
                }
            },
            dismissButton = { TextButton(onClick = { excluindo = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun CartaoRota(
    r: Rota,
    somenteLeitura: Boolean,
    onEditar: () -> Unit,
    onExcluir: () -> Unit
) {
    val cor = if (r.ativa) Color(0xFF2E7D32) else Color(0xFF757575)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    r.nome,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Surface(color = cor.copy(alpha = 0.15f), shape = RoundedCornerShape(50)) {
                    Text(
                        if (r.ativa) "Ativa" else "Inativa",
                        color = cor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Text("${r.origem} → ${r.destino}", modifier = Modifier.padding(top = 4.dp))
            Text(
                "${r.distanciaKm} km · ${r.duracaoHoras} h · pedágios ~ R$ ${r.pedagioReais}",
                fontSize = 13.sp
            )
            Text(
                "Viagens neste trajeto: ${viagensDaRota(r)}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            if (!somenteLeitura) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onEditar) {
                        Icon(Icons.Filled.Edit, contentDescription = "Editar")
                    }
                    IconButton(onClick = onExcluir) {
                        Icon(Icons.Filled.Delete, contentDescription = "Excluir")
                    }
                }
            }
        }
    }
}

@Composable
private fun CampoRota(
    rotulo: String,
    valor: String,
    numerico: Boolean = false,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onChange,
        label = { Text(rotulo) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numerico) KeyboardType.Number else KeyboardType.Text
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun FormularioRota(
    inicial: Rota?,
    onSalvar: (Rota) -> Unit,
    onCancelar: () -> Unit
) {
    var nome by remember { mutableStateOf(inicial?.nome ?: "") }
    var origem by remember { mutableStateOf(inicial?.origem ?: "") }
    var destino by remember { mutableStateOf(inicial?.destino ?: "") }
    var distancia by remember { mutableStateOf(inicial?.distanciaKm?.toString() ?: "") }
    var horas by remember { mutableStateOf(inicial?.duracaoHoras?.toString() ?: "") }
    var pedagio by remember { mutableStateOf(inicial?.pedagioReais?.toString() ?: "") }
    var ativa by remember { mutableStateOf(inicial?.ativa ?: true) }
    var erro by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(if (inicial == null) "Nova rota" else "Editar rota") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CampoRota("Nome da rota", nome) { nome = it }
                CampoRota("Origem (ex.: Varginha - MG)", origem) { origem = it }
                CampoRota("Destino (ex.: Campinas - SP)", destino) { destino = it }
                CampoRota("Distância (km)", distancia, numerico = true) {
                    distancia = it.filter(Char::isDigit).take(5)
                }
                CampoRota("Tempo estimado (horas)", horas, numerico = true) {
                    horas = it.filter(Char::isDigit).take(3)
                }
                CampoRota("Pedágios estimados (R$)", pedagio, numerico = true) {
                    pedagio = it.filter(Char::isDigit).take(6)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Rota ativa", modifier = Modifier.weight(1f))
                    Switch(checked = ativa, onCheckedChange = { ativa = it })
                }
                erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val dist = distancia.toIntOrNull()
                val horasN = horas.toIntOrNull()
                val pedN = pedagio.toIntOrNull()
                val msg = when {
                    nome.isBlank() || origem.isBlank() || destino.isBlank() ->
                        "Preencha nome, origem e destino."
                    origem.trim().equals(destino.trim(), ignoreCase = true) ->
                        "Origem e destino precisam ser diferentes."
                    dist == null || dist <= 0 -> "Informe a distância em km (maior que zero)."
                    horasN == null || horasN <= 0 -> "Informe o tempo estimado em horas (maior que zero)."
                    pedN == null -> "Informe o valor dos pedágios (use 0 se não houver)."
                    RotaRepo.parExiste(origem, destino, inicial?.id) ->
                        "Já existe uma rota com essa origem e destino."
                    else -> null
                }
                erro = msg
                if (msg == null) {
                    // Os "!!" são seguros: a validação acima já garantiu que não são nulos.
                    onSalvar(
                        Rota(
                            id = inicial?.id ?: UUID.randomUUID().toString(),
                            nome = nome.trim(),
                            origem = origem.trim(),
                            destino = destino.trim(),
                            distanciaKm = dist!!,
                            duracaoHoras = horasN!!,
                            pedagioReais = pedN!!,
                            ativa = ativa
                        )
                    )
                }
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}