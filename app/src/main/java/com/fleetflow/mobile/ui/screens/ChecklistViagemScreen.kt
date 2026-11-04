package com.fleetflow.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fleetflow.mobile.data.local.ItemChecklistEntity
import com.fleetflow.mobile.data.model.RespostaChecklistItem
import com.fleetflow.mobile.data.repository.ViagemRepository
import com.fleetflow.mobile.ui.components.EmptyState
import kotlinx.coroutines.launch

// Checklist de saída (protótipo, tela 20): um toque por item, botões grandes para quem
// está em pé ao lado do caminhão. Item não conforme exige observação. Funciona sem
// internet: as respostas sobem pela mesma fila da viagem.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistViagemScreen(
    viagemId: String,
    onVoltar: () -> Unit,
    onSalvo: (liberado: Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val itens by remember { ViagemRepository.observarItensChecklist() }.collectAsState(initial = emptyList())
    val respostas = remember { mutableStateMapOf<String, Boolean>() }     // id do item -> conforme
    val observacoes = remember { mutableStateMapOf<String, String>() }
    var tentouSalvar by remember { mutableStateOf(false) }
    var confirmarNaoConforme by remember { mutableStateOf(false) }
    var salvando by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }

    val faltando = itens.filter { it.id !in respostas }
    val semObservacao = itens.filter { respostas[it.id] == false && observacoes[it.id].isNullOrBlank() }
    val obrigatoriosNaoConformes = itens.filter { it.obrigatorio && respostas[it.id] == false }

    fun salvar() {
        salvando = true
        scope.launch {
            val lista = itens.map { item ->
                val conforme = respostas[item.id] == true
                RespostaChecklistItem(
                    itemChecklistId = item.id,
                    conforme = conforme,
                    // O campo só aparece no item não conforme; texto que sobrou de antes não vai.
                    observacao = if (conforme) null else observacoes[item.id]?.trim()?.takeIf { it.isNotEmpty() },
                )
            }
            runCatching { ViagemRepository.responderChecklist(viagemId, lista) }
                .onSuccess { liberado -> onSalvo(liberado) }
                .onFailure { erro = it.message ?: "Não foi possível salvar o checklist" }
            salvando = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Checklist de saída") },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Confira cada item antes de sair. Item não conforme pede uma observação.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (itens.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.CheckCircle,
                        title = "Checklist não baixado",
                        message = "Conecte-se uma vez para baixar os itens do checklist.",
                    )
                }
            }
            items(itens, key = { it.id }) { item ->
                CartaoItemChecklist(
                    item = item,
                    conforme = respostas[item.id],
                    observacao = observacoes[item.id].orEmpty(),
                    mostrarErros = tentouSalvar,
                    onResposta = { respostas[item.id] = it },
                    onObservacao = { observacoes[item.id] = it },
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (tentouSalvar && faltando.isNotEmpty()) {
                        Text("Responda o checklist antes de iniciar a viagem", color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        onClick = {
                            tentouSalvar = true
                            erro = null
                            if (itens.isEmpty() || faltando.isNotEmpty() || semObservacao.isNotEmpty()) return@Button
                            if (obrigatoriosNaoConformes.isNotEmpty()) confirmarNaoConforme = true else salvar()
                        },
                        enabled = !salvando && itens.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                    ) {
                        if (salvando) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text("Salvar checklist")
                    }
                }
            }
        }
    }

    if (confirmarNaoConforme) {
        AlertDialog(
            onDismissRequest = { confirmarNaoConforme = false },
            title = { Text("Item obrigatório não conforme") },
            text = {
                Text(
                    "${obrigatoriosNaoConformes.joinToString { it.nome }}: não conforme. " +
                        "Com item obrigatório não conforme, a viagem não pode ser fechada. " +
                        "Depois de resolver o problema, responda o checklist de novo. Salvar assim mesmo?"
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmarNaoConforme = false; salvar() }) { Text("Salvar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmarNaoConforme = false }) { Text("Revisar") }
            },
        )
    }
}

@Composable
private fun CartaoItemChecklist(
    item: ItemChecklistEntity,
    conforme: Boolean?,
    observacao: String,
    mostrarErros: Boolean,
    onResposta: (Boolean) -> Unit,
    onObservacao: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(item.nome, style = MaterialTheme.typography.titleMedium)
            if (item.obrigatorio) {
                Text("Obrigatório", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BotaoResposta(
                    texto = "Conforme",
                    selecionado = conforme == true,
                    negativo = false,
                    onClick = { onResposta(true) },
                    modifier = Modifier.weight(1f),
                )
                BotaoResposta(
                    texto = "Não conforme",
                    selecionado = conforme == false,
                    negativo = true,
                    onClick = { onResposta(false) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (mostrarErros && conforme == null) {
                Text("Responda este item", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
            if (conforme == false) {
                val faltaObservacao = mostrarErros && observacao.isBlank()
                OutlinedTextField(
                    value = observacao,
                    onValueChange = { onObservacao(it.take(200)) },
                    label = { Text("Observação") },
                    isError = faltaObservacao,
                    supportingText = { if (faltaObservacao) Text("Descreva o problema encontrado neste item") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// Selecionado = botão cheio; o outro fica só com contorno. Ícone e texto juntos, nunca só a cor.
@Composable
private fun BotaoResposta(
    texto: String,
    selecionado: Boolean,
    negativo: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icone = if (negativo) Icons.Rounded.Cancel else Icons.Rounded.CheckCircle
    if (selecionado) {
        Button(
            onClick = onClick,
            modifier = modifier.heightIn(min = 56.dp),
            colors = if (negativo) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                )
            } else {
                ButtonDefaults.buttonColors()
            },
        ) {
            Icon(icone, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(texto)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 56.dp)) {
            Icon(icone, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(texto)
        }
    }
}
