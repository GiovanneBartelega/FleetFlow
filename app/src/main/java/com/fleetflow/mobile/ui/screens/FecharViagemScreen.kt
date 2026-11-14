package com.fleetflow.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fleetflow.mobile.data.repository.ViagemRepository
import kotlinx.coroutines.launch

@Composable
fun FecharViagemScreen(viagemId: String, onVoltar: () -> Unit, onResponderChecklist: () -> Unit, onFechada: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    var hodometro by remember { mutableStateOf("") }
    var erro by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Fechar viagem", style = MaterialTheme.typography.titleLarge)
        erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(value = hodometro, onValueChange = { hodometro = it.filter(Char::isDigit) }, label = { Text("Hodômetro final") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            val final = hodometro.toIntOrNull() ?: run { erro = "Informe o hodômetro final"; return@Button }
            scope.launch {
                val inicial = ViagemRepository.buscar(viagemId)?.hodometroInicial ?: final
                runCatching { ViagemRepository.fechar(viagemId, final) }
                    .onSuccess { onFechada(final - inicial) }
                    .onFailure { erro = it.message ?: "Não foi possível fechar a viagem" }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Fechar viagem") }
        Button(onClick = onResponderChecklist, modifier = Modifier.fillMaxWidth()) { Text("Responder checklist") }
        Button(onClick = onVoltar, modifier = Modifier.fillMaxWidth()) { Text("Voltar") }
    }
}
