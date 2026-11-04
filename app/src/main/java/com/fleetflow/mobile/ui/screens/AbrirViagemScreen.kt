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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fleetflow.mobile.data.local.StatusViagem
import com.fleetflow.mobile.data.local.ViagemEntity
import com.fleetflow.mobile.data.model.UserResponseDto
import com.fleetflow.mobile.data.repository.FrotaRepository
import com.fleetflow.mobile.data.repository.RegrasViagem
import com.fleetflow.mobile.data.repository.ViagemRepository
import com.fleetflow.mobile.ui.components.AppDropdown
import kotlinx.coroutines.launch

// Abrir viagem (protótipo, tela 19): três campos, veículo, rota e hodômetro inicial.
// O motorista é sempre quem está logado: a API abre a viagem para o próprio usuário.
// Funciona sem internet: grava no aparelho e entra na fila de sincronização.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbrirViagemScreen(
    usuario: UserResponseDto,
    onVoltar: () -> Unit,
    onAberta: (ViagemEntity) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val rotas by remember { ViagemRepository.observarRotas() }.collectAsState(initial = emptyList())
    val viagens by remember { ViagemRepository.observarViagens() }.collectAsState(initial = emptyList())

    // Só aparecem veículos disponíveis e sem viagem aberta no aparelho, inclusive as que
    // ainda não subiram para o servidor.
    val emViagem = viagens.filter { it.status == StatusViagem.ABERTA }.map { it.veiculoId }.toSet()
    val disponiveis = FrotaRepository.veiculos.filter { it.status == "Disponivel" && it.id !in emViagem }
    val veiculosPorRotulo = disponiveis.associateBy { rotuloVeiculoViagem(it) }
    val rotasPorRotulo = rotas.associateBy { rotuloRotaViagem(it) }

    var veiculoRotulo by remember { mutableStateOf<String?>(null) }
    var rotaRotulo by remember { mutableStateOf<String?>(null) }
    var hodometro by remember { mutableStateOf("") }
    var tentouSalvar by remember { mutableStateOf(false) }
    var salvando by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }

    val veiculo = veiculoRotulo?.let { veiculosPorRotulo[it] }
    val rota = rotaRotulo?.let { rotasPorRotulo[it] }
    val hodometroNumero = hodometro.toIntOrNull()
    // Inclui as viagens do aparelho que a frota ainda não recebeu, como no repositório.
    val ultimoRegistrado = veiculo?.let { v ->
        RegrasViagem.ultimoHodometro(
            v.hodometroAtual,
            viagens.filter { it.veiculoId == v.id }.map { it.hodometroFinal ?: it.hodometroInicial },
        )
    }
    val erroHodometro = ultimoRegistrado?.let { RegrasViagem.erroHodometroInicial(hodometroNumero, it) }

    // Ao escolher o veículo, o hodômetro já vem com o último registrado.
    LaunchedEffect(veiculo?.id) {
        if (ultimoRegistrado != null) hodometro = ultimoRegistrado.toString()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Abrir viagem") },
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
            erro?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            if (disponiveis.isEmpty()) {
                Text(
                    "Nenhum veículo disponível. Os que estão em viagem ou em manutenção não aparecem aqui.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AppDropdown(
                label = "Veículo",
                options = veiculosPorRotulo.keys.toList(),
                selected = veiculoRotulo,
                onSelected = { veiculoRotulo = it },
            )
            if (tentouSalvar && veiculo == null) CampoObrigatorio()

            if (rotas.isEmpty()) {
                Text(
                    "Nenhuma rota salva no aparelho. Conecte-se uma vez para baixar as rotas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AppDropdown(
                label = "Rota",
                options = rotasPorRotulo.keys.toList(),
                selected = rotaRotulo,
                onSelected = { rotaRotulo = it },
            )
            if (tentouSalvar && rota == null) CampoObrigatorio()

            OutlinedTextField(
                value = hodometro,
                onValueChange = { hodometro = it.filter(Char::isDigit).take(9) },
                label = { Text("Hodômetro inicial (km)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = tentouSalvar && erroHodometro != null,
                supportingText = {
                    val texto = when {
                        tentouSalvar && erroHodometro != null -> erroHodometro
                        ultimoRegistrado != null -> "Último registrado: ${kmViagem(ultimoRegistrado)} km"
                        else -> "Escolha o veículo primeiro"
                    }
                    Text(texto)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = usuario.nome,
                onValueChange = {},
                label = { Text("Motorista") },
                readOnly = true,
                enabled = false,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    tentouSalvar = true
                    erro = null
                    val v = veiculo
                    val r = rota
                    val km = hodometroNumero
                    if (v == null || r == null || km == null || erroHodometro != null) return@Button
                    salvando = true
                    scope.launch {
                        runCatching { ViagemRepository.abrir(v, r, km, usuario.id, usuario.nome) }
                            .onSuccess { onAberta(it) }
                            .onFailure { erro = it.message ?: "Não foi possível abrir a viagem" }
                        salvando = false
                    }
                },
                enabled = !salvando,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            ) {
                if (salvando) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Abrir viagem")
            }
        }
    }
}

@Composable
private fun CampoObrigatorio() {
    Text(
        "Campo obrigatório",
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.labelSmall,
    )
}
