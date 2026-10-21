package com.fleetflow.mobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fleetflow.mobile.data.model.OrdemServicoRequestDto
import com.fleetflow.mobile.data.model.VeiculoDto
import com.fleetflow.mobile.data.model.VeiculoRequestDto
import com.fleetflow.mobile.data.repository.FinanceRepository
import com.fleetflow.mobile.data.repository.FrotaRepository
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class FrotaTela { LISTA, VEICULO, ORDEM }

@Composable
fun FrotaScreen(podeEditar: Boolean) {
    val scope = rememberCoroutineScope()
    var tela by remember { mutableStateOf(FrotaTela.LISTA) }
    var veiculoSelecionado by remember { mutableStateOf<VeiculoDto?>(null) }
    var mensagem by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { runCatching { FrotaRepository.carregar() }.onFailure { mensagem = it.message } }

    when (tela) {
        FrotaTela.LISTA -> FrotaLista(
            podeEditar = podeEditar,
            mensagem = mensagem,
            onNovoVeiculo = { veiculoSelecionado = null; tela = FrotaTela.VEICULO },
            onVeiculo = { veiculoSelecionado = it; tela = FrotaTela.VEICULO },
            onNovaOrdem = { tela = FrotaTela.ORDEM },
            onExcluirVeiculo = { item -> scope.launch { runCatching { FrotaRepository.excluirVeiculo(item) }.onFailure { mensagem = it.message } } },
            onConcluirOrdem = { item -> scope.launch { runCatching { FrotaRepository.concluirOrdem(item, LocalDate.now().toString()) }.onFailure { mensagem = it.message } } },
            onExcluirOrdem = { item -> scope.launch { runCatching { FrotaRepository.excluirOrdem(item) }.onFailure { mensagem = it.message } } },
        )
        FrotaTela.VEICULO -> VeiculoFormulario(
            atual = veiculoSelecionado,
            erroServidor = mensagem,
            onVoltar = { tela = FrotaTela.LISTA },
            onSalvar = { body -> scope.launch { runCatching { FrotaRepository.salvarVeiculo(veiculoSelecionado, body); tela = FrotaTela.LISTA }.onFailure { mensagem = it.message } } },
        )
        FrotaTela.ORDEM -> OrdemFormulario(
            veiculos = FrotaRepository.veiculos,
            erroServidor = mensagem,
            onVoltar = { tela = FrotaTela.LISTA },
            onSalvar = { body -> scope.launch { runCatching { FrotaRepository.criarOrdem(body); tela = FrotaTela.LISTA }.onFailure { mensagem = it.message } } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FrotaLista(
    podeEditar: Boolean,
    mensagem: String?,
    onNovoVeiculo: () -> Unit,
    onVeiculo: (VeiculoDto) -> Unit,
    onNovaOrdem: () -> Unit,
    onExcluirVeiculo: (VeiculoDto) -> Unit,
    onConcluirOrdem: (com.fleetflow.mobile.data.model.OrdemServicoDto) -> Unit,
    onExcluirOrdem: (com.fleetflow.mobile.data.model.OrdemServicoDto) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Frota e manutenção") }) },
        floatingActionButton = {
            if (podeEditar) FloatingActionButton(onClick = onNovoVeiculo) { Icon(Icons.Default.Add, "Cadastrar veículo") }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { mensagem?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) } }
            item { Text("Veículos", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 10.dp)) }
            if (FrotaRepository.carregando) item { CircularProgressIndicator() }
            items(FrotaRepository.veiculos, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth().clickable { onVeiculo(item) }) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("${item.marca} ${item.modelo}", fontWeight = FontWeight.SemiBold)
                            Text("${formatarPlaca(item.placa)} · ${item.anoFabricacao} · ${item.status}")
                            Text("Hodômetro: ${item.hodometroAtual} km", style = MaterialTheme.typography.bodySmall)
                        }
                        if (podeEditar) IconButton(onClick = { onExcluirVeiculo(item) }) { Icon(Icons.Default.Delete, "Excluir veículo") }
                    }
                }
            }
            if (FrotaRepository.veiculos.isEmpty() && !FrotaRepository.carregando) item { Text("Nenhum veículo cadastrado.") }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Ordens de serviço", style = MaterialTheme.typography.titleLarge)
                    if (podeEditar) Button(onClick = onNovaOrdem) { Icon(Icons.Default.Build, null); Spacer(Modifier.width(6.dp)); Text("Nova ordem") }
                }
            }
            items(FrotaRepository.ordens, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(item.descricao, fontWeight = FontWeight.SemiBold)
                            Text("${formatarPlaca(item.veiculo.placa)} · ${item.tipo} · R$ ${item.valorTotal}")
                            Text(if (item.concluida) "Concluída em ${item.dataConclusao}" else "Aberta desde ${item.dataManutencao}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (podeEditar) Row {
                            if (!item.concluida) Button(onClick = { onConcluirOrdem(item) }) { Text("Concluir") }
                            IconButton(onClick = { onExcluirOrdem(item) }) { Icon(Icons.Default.Delete, "Excluir ordem de serviço") }
                        }
                    }
                }
            }
            if (FrotaRepository.ordens.isEmpty() && !FrotaRepository.carregando) item { Text("Nenhuma ordem de serviço cadastrada.") }
            item { Spacer(Modifier.padding(bottom = 48.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VeiculoFormulario(atual: VeiculoDto?, erroServidor: String?, onVoltar: () -> Unit, onSalvar: (VeiculoRequestDto) -> Unit) {
    var placa by remember(atual) { mutableStateOf(atual?.placa ?: "") }
    var marca by remember(atual) { mutableStateOf(atual?.marca ?: "") }
    var modelo by remember(atual) { mutableStateOf(atual?.modelo ?: "") }
    var ano by remember(atual) { mutableStateOf(atual?.anoFabricacao?.toString() ?: "") }
    var capacidade by remember(atual) { mutableStateOf(atual?.capacidadeCargaToneladas ?: "") }
    var hodometro by remember(atual) { mutableStateOf(atual?.hodometroAtual?.toString() ?: "0") }
    var status by remember(atual) { mutableStateOf(atual?.status ?: "Disponivel") }
    var erro by remember { mutableStateOf<String?>(null) }
    var statusAberto by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text(if (atual == null) "Novo veículo" else "Editar veículo") }, navigationIcon = { IconButton(onClick = onVoltar) { Icon(Icons.Default.ArrowBack, "Voltar") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            (erro ?: erroServidor)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedTextField(placa, { placa = it.uppercase() }, label = { Text("Placa") }, supportingText = { Text("ABC-1234 ou ABC1D23") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(marca, { marca = it }, label = { Text("Marca") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(modelo, { modelo = it }, label = { Text("Modelo") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(ano, { ano = it }, label = { Text("Ano de fabricação") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(capacidade, { capacidade = it.replace(',', '.') }, label = { Text("Capacidade de carga (toneladas)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(hodometro, { hodometro = it }, label = { Text("Hodômetro atual (km)") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { statusAberto = true }) { Text("Status: $status") }
            DropdownMenu(expanded = statusAberto, onDismissRequest = { statusAberto = false }) {
                listOf("Disponivel", "EmViagem", "EmManutencao", "Inativo").forEach { opcao -> DropdownMenuItem(text = { Text(opcao) }, onClick = { status = opcao; statusAberto = false }) }
            }
            Button(onClick = {
                val body = runCatching {
                    VeiculoRequestDto(placa, marca.trim(), modelo.trim(), ano.toInt(), capacidade.toDouble(), status, hodometro.toInt())
                }.getOrElse { erro = "Revise placa, ano, capacidade e hodômetro."; return@Button }
                onSalvar(body)
            }, modifier = Modifier.fillMaxWidth()) { Text("Salvar veículo") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrdemFormulario(veiculos: List<VeiculoDto>, erroServidor: String?, onVoltar: () -> Unit, onSalvar: (OrdemServicoRequestDto) -> Unit) {
    var veiculoId by remember { mutableStateOf(veiculos.firstOrNull()?.id ?: "") }
    var descricao by remember { mutableStateOf("") }
    var valor by remember { mutableStateOf("") }
    var tipo by remember { mutableStateOf("Preventiva") }
    var formaId by remember { mutableStateOf("") }
    var erro by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { runCatching { FinanceRepository.catalogs() }.onFailure { erro = it.message } }
    Scaffold(topBar = { TopAppBar(title = { Text("Nova ordem de serviço") }, navigationIcon = { IconButton(onClick = onVoltar) { Icon(Icons.Default.ArrowBack, "Voltar") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            (erro ?: erroServidor)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("Veículo", fontWeight = FontWeight.SemiBold)
            veiculos.forEach { item -> Button(onClick = { veiculoId = item.id }, enabled = item.status != "EmViagem") { Text(if (veiculoId == item.id) "✓ ${formatarPlaca(item.placa)}" else formatarPlaca(item.placa)) } }
            OutlinedTextField(descricao, { descricao = it }, label = { Text("Descrição") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(valor, { valor = it.replace(',', '.') }, label = { Text("Valor total") }, modifier = Modifier.fillMaxWidth())
            Row { Button(onClick = { tipo = "Preventiva" }) { Text(if (tipo == "Preventiva") "✓ Preventiva" else "Preventiva") }; Spacer(Modifier.width(8.dp)); Button(onClick = { tipo = "Corretiva" }) { Text(if (tipo == "Corretiva") "✓ Corretiva" else "Corretiva") } }
            Text("Forma de pagamento", fontWeight = FontWeight.SemiBold)
            FinanceRepository.formas.forEach { item -> Button(onClick = { formaId = item.id }) { Text(if (formaId == item.id) "✓ ${item.nome}" else item.nome) } }
            Button(onClick = {
                if (veiculoId.isBlank() || descricao.isBlank() || formaId.isBlank() || valor.toDoubleOrNull() == null) { erro = "Preencha veículo, descrição, valor e forma de pagamento." }
                else onSalvar(OrdemServicoRequestDto(veiculoId, LocalDate.now().toString(), descricao.trim(), tipo, valor, formaPagamentoId = formaId))
            }, modifier = Modifier.fillMaxWidth()) { Text("Criar ordem e despesa") }
        }
    }
}

private fun formatarPlaca(placa: String): String = if (placa.length == 7) "${placa.substring(0, 3)}-${placa.substring(3)}" else placa
