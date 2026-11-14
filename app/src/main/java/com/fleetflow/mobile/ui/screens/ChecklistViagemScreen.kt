package com.fleetflow.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fleetflow.mobile.data.model.RespostaChecklistItem
import com.fleetflow.mobile.data.repository.ViagemRepository
import kotlinx.coroutines.launch

@Composable
fun ChecklistViagemScreen(viagemId: String, onVoltar: () -> Unit, onSalvo: (Boolean) -> Unit) {
    val itens by remember { ViagemRepository.observarItensChecklist() }.collectAsState(initial = emptyList())
    val escolhas = remember { mutableStateMapOf<String, Boolean>() }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Checklist de saída", style = MaterialTheme.typography.titleLarge)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
            items(itens, key = { it.id }) { item ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = escolhas[item.id] ?: true, onCheckedChange = { escolhas[item.id] = it })
                    Text(item.nome)
                }
            }
        }
        Button(onClick = {
            scope.launch {
                val respostas = itens.map { RespostaChecklistItem(it.id, escolhas[it.id] ?: true) }
                onSalvo(ViagemRepository.responderChecklist(viagemId, respostas))
            }
        }, modifier = Modifier.fillMaxWidth(), enabled = itens.isNotEmpty()) { Text("Salvar checklist") }
        Button(onClick = onVoltar, modifier = Modifier.fillMaxWidth()) { Text("Voltar") }
    }
}
