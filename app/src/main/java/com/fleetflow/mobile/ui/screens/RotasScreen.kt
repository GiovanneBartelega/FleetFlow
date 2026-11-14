package com.fleetflow.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fleetflow.mobile.data.model.RotaDto
import com.fleetflow.mobile.data.model.RotaRequestDto
import com.fleetflow.mobile.data.repository.RotasRepository
import com.fleetflow.mobile.data.repository.ViagensRepository
import kotlinx.coroutines.launch
import java.io.IOException

// Rotas cadastradas na API. A lista, o cadastro, a edição e a exclusão passam pelo
// RotasRepository, que também atualiza a cópia de rotas do aparelho usada em Abrir viagem.
// Campos iguais aos da API e da especificação: nome, origem, destino, distância e descrição.

// Viagens que usam a rota, pelo id da rota (a API não deixa excluir rota com viagens).
private fun viagensDaRota(r: RotaDto): Int =
    ViagensRepository.viagens.count { it.rota.id == r.id }

@Composable
fun RotasScreen(somenteLeitura: Boolean = false) {
    val scope = rememberCoroutineScope()
    var busca by remember { mutableStateOf("") }
    var criando by remember { mutableStateOf(false) }
    var editando by remember { mutableStateOf<RotaDto?>(null) }
    var excluindo by remember { mutableStateOf<RotaDto?>(null) }
    var mensagem by remember { mutableStateOf<String?>(null) }

    suspend fun recarregar() {
        RotasRepository.carregar()
        // Só para a contagem de viagens por rota; sem ela a tela funciona do mesmo jeito.
        runCatching { ViagensRepository.carregar() }
    }

    LaunchedEffect(Unit) { recarregar() }

    val termo = busca.trim()
    val todas = RotasRepository.rotas
    val lista = todas.filter {
        termo.isEmpty() ||
            it.nome.contains(termo, ignoreCase = true) ||
            it.origem.contains(termo, ignoreCase = true) ||
            it.destino.contains(termo, ignoreCase = true)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Text(
                "Rotas",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                if (todas.size == 1) "1 rota cadastrada" else "${todas.size} rotas cadastradas",
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
            if (RotasRepository.carregando) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            RotasRepository.erro?.let { erro ->
                AvisoRotas(
                    texto = if (RotasRepository.semConexao && todas.isNotEmpty()) {
                        "Sem conexão. Mostrando as rotas carregadas antes."
                    } else {
                        erro
                    },
                    onAcao = { scope.launch { recarregar() } }
                )
            }
            mensagem?.let { texto ->
                AvisoRotas(texto = texto, botao = "OK", onAcao = { mensagem = null })
            }
            if (lista.isEmpty() && !RotasRepository.carregando && RotasRepository.erro == null) {
                Text(
                    if (todas.isEmpty() && !somenteLeitura) "Nenhuma rota cadastrada. Toque em + para cadastrar."
                    else "Nenhuma rota encontrada.",
                    modifier = Modifier.padding(top = 16.dp)
                )
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
            onSalvar = { dados -> RotasRepository.criar(dados) },
            onSalvo = { criando = false; mensagem = "Rota cadastrada. Ela já aparece em Abrir viagem." },
            onCancelar = { criando = false }
        )
    }
    editando?.let { atual ->
        FormularioRota(
            inicial = atual,
            // Depois de um conflito, a lista já tem a versão nova da rota: salvar de novo
            // grava a versão desta pessoa por cima, agora sabendo da outra alteração.
            onSalvar = { dados ->
                val maisRecente = RotasRepository.rotas.firstOrNull { it.id == atual.id } ?: atual
                RotasRepository.editar(maisRecente, dados)
            },
            onSalvo = { editando = null; mensagem = "Rota atualizada." },
            onCancelar = { editando = null }
        )
    }
    excluindo?.let { alvo ->
        val usadas = viagensDaRota(alvo)
        AlertDialog(
            onDismissRequest = { excluindo = null },
            title = { Text(if (usadas > 0) "Rota em uso" else "Excluir rota?") },
            text = {
                Text(
                    if (usadas > 0) {
                        "${alvo.nome} tem $usadas viagem(ns) e não pode ser excluída: ela continua no histórico das viagens."
                    } else {
                        "${alvo.nome} será removida e deixa de aparecer em Abrir viagem."
                    }
                )
            },
            confirmButton = {
                if (usadas > 0) {
                    TextButton(onClick = { excluindo = null }) { Text("Entendi") }
                } else {
                    TextButton(onClick = {
                        excluindo = null
                        scope.launch {
                            mensagem = runCatching { RotasRepository.remover(alvo) }.fold(
                                onSuccess = { "Rota excluída." },
                                onFailure = { erro -> mensagemDeErroRota(erro) }
                            )
                        }
                    }) {
                        Text("Excluir", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                if (usadas == 0) TextButton(onClick = { excluindo = null }) { Text("Cancelar") }
            }
        )
    }
}

private fun mensagemDeErroRota(erro: Throwable): String = when (erro) {
    is IOException -> "Sem conexão. Cadastro de rotas precisa de internet."
    else -> erro.message ?: "Não foi possível salvar a rota."
}

@Composable
private fun AvisoRotas(texto: String, botao: String = "Tentar de novo", onAcao: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(texto, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
            TextButton(onClick = onAcao) { Text(botao) }
        }
    }
}

@Composable
private fun CartaoRota(
    r: RotaDto,
    somenteLeitura: Boolean,
    onEditar: () -> Unit,
    onExcluir: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                r.nome,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text("${r.origem} → ${r.destino}", modifier = Modifier.padding(top = 4.dp))
            Text("${kmViagem(r.distanciaEstimadaKm)} km estimados", fontSize = 13.sp)
            r.descricao?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "Viagens nesta rota: ${viagensDaRota(r)}",
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

// onSalvar chama a API; o diálogo fica aberto até ela responder e mostra o erro se recusar.
@Composable
private fun FormularioRota(
    inicial: RotaDto?,
    onSalvar: suspend (RotaRequestDto) -> Unit,
    onSalvo: () -> Unit,
    onCancelar: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var nome by remember { mutableStateOf(inicial?.nome ?: "") }
    var origem by remember { mutableStateOf(inicial?.origem ?: "") }
    var destino by remember { mutableStateOf(inicial?.destino ?: "") }
    var distancia by remember { mutableStateOf(inicial?.distanciaEstimadaKm?.toString() ?: "") }
    var descricao by remember { mutableStateOf(inicial?.descricao ?: "") }
    var erro by remember { mutableStateOf<String?>(null) }
    var salvando by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!salvando) onCancelar() },
        title = { Text(if (inicial == null) "Nova rota" else "Editar rota") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CampoRota("Nome da rota", nome) { nome = it.take(100) }
                CampoRota("Origem (ex.: Varginha - MG)", origem) { origem = it.take(120) }
                CampoRota("Destino (ex.: Campinas - SP)", destino) { destino = it.take(120) }
                CampoRota("Distância (km)", distancia, numerico = true) {
                    distancia = it.filter(Char::isDigit).take(5)
                }
                CampoRota("Descrição (opcional)", descricao) { descricao = it.take(500) }
                erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                enabled = !salvando,
                onClick = {
                    val dist = distancia.toIntOrNull()
                    val msg = when {
                        nome.isBlank() || origem.isBlank() || destino.isBlank() ->
                            "Preencha nome, origem e destino."
                        origem.trim().equals(destino.trim(), ignoreCase = true) ->
                            "Origem e destino precisam ser diferentes."
                        dist == null || dist <= 0 -> "Informe a distância em km (maior que zero)."
                        RotasRepository.parExiste(origem, destino, inicial?.id) ->
                            "Já existe uma rota com essa origem e destino."
                        else -> null
                    }
                    erro = msg
                    if (msg == null && dist != null) {
                        salvando = true
                        scope.launch {
                            val dados = RotaRequestDto(
                                nome = nome.trim(),
                                origem = origem.trim(),
                                destino = destino.trim(),
                                distanciaEstimadaKm = dist,
                                descricao = descricao.trim().ifBlank { null },
                            )
                            runCatching { onSalvar(dados) }
                                .onSuccess { onSalvo() }
                                .onFailure { erro = mensagemDeErroRota(it) }
                            salvando = false
                        }
                    }
                }
            ) {
                if (salvando) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Salvar")
            }
        },
        dismissButton = { TextButton(enabled = !salvando, onClick = onCancelar) { Text("Cancelar") } }
    )
}
