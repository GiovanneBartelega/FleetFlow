package com.fleetflow.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fleetflow.mobile.data.local.ViagemEntity
import com.fleetflow.mobile.data.repository.RegrasViagem
import com.fleetflow.mobile.data.repository.ViagemRepository
import kotlinx.coroutines.launch

// Fechar viagem (protótipo, telas 23 e 24): hodômetro final e km rodados.
// Hodômetro menor ou igual ao inicial bloqueia; acima de 3.000 km pede confirmação.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FecharViagemScreen(
    viagemId: String,
    onVoltar: () -> Unit,
    onResponderChecklist: () -> Unit,
    onFechada: (kmRodados: Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var viagem by remember { mutableStateOf<ViagemEntity?>(null) }
    var hodometro by remember { mutableStateOf("") }
    var tentouSalvar by remember { mutableStateOf(false) }
    var confirmarAlta by remember { mutableStateOf(false) }
    var salvando by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(viagemId) { viagem = ViagemRepository.buscar(viagemId) }

    fun fechar(hodometroFinal: Int, kmRodados: Int) {
        salvando = true
        scope.launch {
            runCatching { ViagemRepository.fechar(viagemId, hodometroFinal) }
                .onSuccess { onFechada(kmRodados) }
                .onFailure { erro = it.message ?: "Não foi possível fechar a viagem" }
            salvando = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fechar viagem") },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val v = viagem
            if (v == null) {
                CircularProgressIndicator()
            } else {
                val hodometroFinal = hodometro.toIntOrNull()
                val erroHodometro = RegrasViagem.erroHodometroFinal(v.hodometroInicial, hodometroFinal)
                val kmRodados = if (hodometroFinal != null && erroHodometro == null) {
                    RegrasViagem.kmRodados(v.hodometroInicial, hodometroFinal)
                } else {
                    null
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${v.rotaOrigem} → ${v.rotaDestino}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${placaViagem(v.veiculoPlaca)} · ${v.veiculoMarca} ${v.veiculoModelo}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Aberta em ${dataHoraViagem(v.dataAbertura)} · hodômetro inicial ${kmViagem(v.hodometroInicial)} km",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                erro?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

                val checklistOk = v.checklistRespondido && v.checklistLiberado
                if (!checklistOk) {
                    Text(
                        if (!v.checklistRespondido) {
                            "Responda o checklist antes de fechar a viagem"
                        } else {
                            "Há item obrigatório não conforme no checklist. Resolva o problema e responda o checklist de novo."
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = onResponderChecklist, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (!v.checklistRespondido) "Responder checklist" else "Responder checklist de novo")
                    }
                }

                OutlinedTextField(
                    value = hodometro,
                    onValueChange = { hodometro = it.filter(Char::isDigit).take(9) },
                    label = { Text("Hodômetro final (km)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = tentouSalvar && erroHodometro != null,
                    supportingText = {
                        val texto = when {
                            tentouSalvar && erroHodometro != null -> erroHodometro
                            kmRodados != null -> "${kmViagem(kmRodados)} km rodados"
                            else -> "Maior que ${kmViagem(v.hodometroInicial)} km"
                        }
                        Text(texto)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Button(
                    onClick = {
                        tentouSalvar = true
                        erro = null
                        val valorFinal = hodometroFinal
                        val km = kmRodados
                        if (!checklistOk || valorFinal == null || km == null) return@Button
                        if (RegrasViagem.quilometragemAlta(v.hodometroInicial, valorFinal)) confirmarAlta = true
                        else fechar(valorFinal, km)
                    },
                    enabled = !salvando,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                ) {
                    if (salvando) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text("Fechar viagem")
                }

                if (confirmarAlta && hodometroFinal != null && kmRodados != null) {
                    AlertDialog(
                        onDismissRequest = { confirmarAlta = false },
                        title = { Text("Confirma a quilometragem?") },
                        text = { Text("A diferença parece alta: ${kmViagem(kmRodados)} km nesta viagem.") },
                        confirmButton = {
                            TextButton(onClick = { confirmarAlta = false; fechar(hodometroFinal, kmRodados) }) {
                                Text("Confirmar")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmarAlta = false }) { Text("Corrigir") }
                        },
                    )
                }
            }
        }
    }
}
