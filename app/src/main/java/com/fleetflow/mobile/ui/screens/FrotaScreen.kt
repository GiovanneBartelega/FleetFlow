package com.fleetflow.mobile.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
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
import com.fleetflow.mobile.data.model.OrdemServicoDto
import com.fleetflow.mobile.data.model.OrdemServicoRequestDto
import com.fleetflow.mobile.data.model.VeiculoDto
import com.fleetflow.mobile.data.model.VeiculoRequestDto
import com.fleetflow.mobile.data.repository.FinanceRepository
import com.fleetflow.mobile.data.repository.FrotaRepository
import com.fleetflow.mobile.ui.components.AppDropdown
import com.fleetflow.mobile.ui.components.EmptyState
import com.fleetflow.mobile.ui.components.MoneyField
import com.fleetflow.mobile.ui.components.formatMoney
import kotlinx.coroutines.launch
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

private enum class FrotaTela { LISTA, VEICULO, ORDEM }

// Frota e manutenção. Administrador e Gestor de Frota cadastram; o motorista abre a
// mesma tela pela aba Veículos, só para consulta e sem as ordens de serviço.
@Composable
fun FrotaScreen(
    podeEditar: Boolean,
    mostrarOrdens: Boolean = true,
    onRotasClick: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var tela by remember { mutableStateOf(FrotaTela.LISTA) }
    var veiculoSelecionado by remember { mutableStateOf<VeiculoDto?>(null) }
    var erroCarga by remember { mutableStateOf<String?>(null) }
    var erroFormulario by remember { mutableStateOf<String?>(null) }
    var salvando by remember { mutableStateOf(false) }

    fun recarregar() {
        scope.launch {
            erroCarga = null
            runCatching { FrotaRepository.carregar(incluirOrdens = mostrarOrdens) }
                .onFailure { erroCarga = mensagemDeErroFrota(it) }
        }
    }

    // Roda uma ação na API. Sucesso: volta para a lista e mostra a confirmação.
    // Erro: na lista vira snackbar; no formulário aparece no topo do formulário.
    fun executar(confirmacao: String, acao: suspend () -> Unit) {
        if (salvando) return
        scope.launch {
            salvando = true
            erroFormulario = null
            val resultado = runCatching { acao() }
            salvando = false
            resultado
                .onSuccess {
                    tela = FrotaTela.LISTA
                    scope.launch { snackbar.showSnackbar(confirmacao) }
                }
                .onFailure { erro ->
                    val mensagem = mensagemDeErroFrota(erro)
                    if (tela == FrotaTela.LISTA) scope.launch { snackbar.showSnackbar(mensagem) }
                    else erroFormulario = mensagem
                }
        }
    }

    LaunchedEffect(mostrarOrdens) {
        erroCarga = null
        runCatching { FrotaRepository.carregar(incluirOrdens = mostrarOrdens) }
            .onFailure { erroCarga = mensagemDeErroFrota(it) }
    }

    // O voltar do Android fecha o formulário em vez de sair da aba.
    BackHandler(enabled = tela != FrotaTela.LISTA) { tela = FrotaTela.LISTA; erroFormulario = null }

    when (tela) {
        FrotaTela.LISTA -> FrotaLista(
            podeEditar = podeEditar,
            mostrarOrdens = mostrarOrdens,
            erroCarga = erroCarga,
            snackbar = snackbar,
            onRotasClick = onRotasClick,
            onTentarDeNovo = { recarregar() },
            onNovoVeiculo = { veiculoSelecionado = null; erroFormulario = null; tela = FrotaTela.VEICULO },
            onVeiculo = { veiculoSelecionado = it; erroFormulario = null; tela = FrotaTela.VEICULO },
            onExcluirVeiculo = { item -> executar("Veículo excluído") { FrotaRepository.excluirVeiculo(item) } },
            onNovaOrdem = { erroFormulario = null; tela = FrotaTela.ORDEM },
            onConcluirOrdem = { item, data ->
                executar("Ordem de serviço concluída") { FrotaRepository.concluirOrdem(item, data.toString()) }
            },
            onExcluirOrdem = { item -> executar("Ordem de serviço excluída") { FrotaRepository.excluirOrdem(item) } },
        )
        FrotaTela.VEICULO -> VeiculoFormulario(
            atual = veiculoSelecionado,
            erroServidor = erroFormulario,
            salvando = salvando,
            onVoltar = { tela = FrotaTela.LISTA; erroFormulario = null },
            onSalvar = { body ->
                executar("Veículo salvo") { FrotaRepository.salvarVeiculo(veiculoSelecionado, body) }
            },
        )
        FrotaTela.ORDEM -> OrdemFormulario(
            veiculos = FrotaRepository.veiculos,
            erroServidor = erroFormulario,
            salvando = salvando,
            onVoltar = { tela = FrotaTela.LISTA; erroFormulario = null },
            onSalvar = { body ->
                executar("Ordem criada. A despesa foi lançada em Finanças.") { FrotaRepository.criarOrdem(body) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FrotaLista(
    podeEditar: Boolean,
    mostrarOrdens: Boolean,
    erroCarga: String?,
    snackbar: SnackbarHostState,
    onRotasClick: (() -> Unit)?,
    onTentarDeNovo: () -> Unit,
    onNovoVeiculo: () -> Unit,
    onVeiculo: (VeiculoDto) -> Unit,
    onExcluirVeiculo: (VeiculoDto) -> Unit,
    onNovaOrdem: () -> Unit,
    onConcluirOrdem: (OrdemServicoDto, LocalDate) -> Unit,
    onExcluirOrdem: (OrdemServicoDto) -> Unit,
) {
    var veiculoParaExcluir by remember { mutableStateOf<VeiculoDto?>(null) }
    var ordemParaExcluir by remember { mutableStateOf<OrdemServicoDto?>(null) }
    var ordemParaConcluir by remember { mutableStateOf<OrdemServicoDto?>(null) }
    val veiculos = FrotaRepository.veiculos
    val ordens = FrotaRepository.ordens
    val podeAbrirOrdem = veiculos.any { aceitaOrdem(it) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (mostrarOrdens) "Frota e manutenção" else "Veículos") },
                actions = {
                    if (onRotasClick != null) {
                        TextButton(onClick = onRotasClick) {
                            Icon(Icons.Filled.Map, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Rotas")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (podeEditar) {
                FloatingActionButton(onClick = onNovoVeiculo) { Icon(Icons.Filled.Add, "Cadastrar veículo") }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (FrotaRepository.carregando) LinearProgressIndicator(Modifier.fillMaxWidth())

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
            ) {
                if (FrotaRepository.semConexao) {
                    item { AvisoSemConexao(salvoEm = FrotaRepository.salvoEm, onTentarDeNovo = onTentarDeNovo) }
                }
                if (erroCarga != null) {
                    item { AvisoErro(mensagem = erroCarga, onTentarDeNovo = onTentarDeNovo) }
                }

                item { Titulo("Veículos", veiculos.size) }
                if (veiculos.isEmpty()) {
                    item {
                        if (FrotaRepository.carregando) {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            EmptyState(
                                icon = Icons.Filled.LocalShipping,
                                title = "Nenhum veículo cadastrado",
                                message = if (podeEditar) "Toque em + para cadastrar o primeiro veículo da frota."
                                else "Os veículos cadastrados pelo gestor aparecem aqui.",
                            )
                        }
                    }
                }
                items(veiculos, key = { it.id }) { item ->
                    CartaoVeiculo(
                        item = item,
                        podeEditar = podeEditar,
                        onClick = { onVeiculo(item) },
                        onExcluir = { veiculoParaExcluir = item },
                    )
                }

                if (mostrarOrdens) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Titulo("Ordens de serviço", ordens.size)
                            if (podeEditar) {
                                Button(onClick = onNovaOrdem, enabled = podeAbrirOrdem) {
                                    Icon(Icons.Filled.Build, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Nova ordem")
                                }
                            }
                        }
                        if (podeEditar && veiculos.isNotEmpty() && !podeAbrirOrdem) {
                            Text(
                                "Nenhum veículo disponível: os que estão em viagem ou inativos não recebem ordem de serviço.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (ordens.isEmpty() && !FrotaRepository.carregando) {
                        item {
                            EmptyState(
                                icon = Icons.Filled.Build,
                                title = "Nenhuma ordem de serviço",
                                message = "As manutenções registradas aparecem aqui.",
                            )
                        }
                    }
                    items(ordens, key = { it.id }) { item ->
                        CartaoOrdem(
                            item = item,
                            podeEditar = podeEditar,
                            onConcluir = { ordemParaConcluir = item },
                            onExcluir = { ordemParaExcluir = item },
                        )
                    }
                }
            }
        }
    }

    veiculoParaExcluir?.let { item ->
        ConfirmarExclusao(
            titulo = "Excluir veículo?",
            texto = "${item.marca} ${item.modelo}, placa ${formatarPlaca(item.placa)}, sai da lista da frota.",
            onConfirmar = { veiculoParaExcluir = null; onExcluirVeiculo(item) },
            onCancelar = { veiculoParaExcluir = null },
        )
    }
    ordemParaExcluir?.let { item ->
        ConfirmarExclusao(
            titulo = "Excluir ordem de serviço?",
            texto = "\"${item.descricao}\" sai da lista. A despesa que ela gerou em Finanças não é apagada.",
            onConfirmar = { ordemParaExcluir = null; onExcluirOrdem(item) },
            onCancelar = { ordemParaExcluir = null },
        )
    }
    ordemParaConcluir?.let { item ->
        val inicioManutencao = dataIso(item.dataManutencao) ?: LocalDate.now()
        SeletorDeData(
            titulo = "Data de conclusão",
            inicial = if (LocalDate.now().isBefore(inicioManutencao)) inicioManutencao else LocalDate.now(),
            minima = inicioManutencao,
            onConfirmar = { data -> ordemParaConcluir = null; onConcluirOrdem(item, data) },
            onCancelar = { ordemParaConcluir = null },
        )
    }
}

@Composable
private fun Titulo(texto: String, quantidade: Int) {
    Text("$texto ($quantidade)", style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun AvisoSemConexao(salvoEm: Long?, onTentarDeNovo: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CloudOff, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (salvoEm != null) "Sem conexão. Mostrando a frota salva no aparelho em ${dataHoraBr(salvoEm)}."
                else "Sem conexão e ainda não há frota salva no aparelho.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onTentarDeNovo) { Text("Tentar de novo") }
        }
    }
}

@Composable
private fun AvisoErro(mensagem: String, onTentarDeNovo: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                mensagem,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onTentarDeNovo) { Text("Tentar de novo") }
        }
    }
}

@Composable
private fun CartaoVeiculo(item: VeiculoDto, podeEditar: Boolean, onClick: () -> Unit, onExcluir: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(enabled = podeEditar, onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${item.marca} ${item.modelo}", fontWeight = FontWeight.SemiBold)
                Text("${formatarPlaca(item.placa)} · ${item.anoFabricacao} · ${statusLegivel(item.status)}")
                Text(
                    "Hodômetro: ${milhar(item.hodometroAtual)} km",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (podeEditar) {
                IconButton(onClick = onExcluir) { Icon(Icons.Filled.Delete, "Excluir veículo") }
            }
        }
    }
}

@Composable
private fun CartaoOrdem(item: OrdemServicoDto, podeEditar: Boolean, onConcluir: () -> Unit, onExcluir: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(item.descricao, fontWeight = FontWeight.SemiBold)
            Text("${formatarPlaca(item.veiculo.placa)} · ${item.tipo} · ${reais(item.valorTotal)}")
            Text(
                text = if (item.concluida) "Concluída em ${dataBr(item.dataConclusao)}"
                else "Aberta desde ${dataBr(item.dataManutencao)}",
                style = MaterialTheme.typography.bodySmall,
                color = if (item.concluida) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            )
            item.hodometroNaManutencao?.let {
                Text(
                    "Hodômetro na manutenção: ${milhar(it)} km",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (podeEditar) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!item.concluida) OutlinedButton(onClick = onConcluir) { Text("Concluir") }
                    IconButton(onClick = onExcluir) { Icon(Icons.Filled.Delete, "Excluir ordem de serviço") }
                }
            }
        }
    }
}

@Composable
private fun ConfirmarExclusao(titulo: String, texto: String, onConfirmar: () -> Unit, onCancelar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(titulo) },
        text = { Text(texto) },
        confirmButton = {
            TextButton(onClick = onConfirmar) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
    )
}

// Calendário do Material 3. As datas do DatePicker vêm em UTC, meia-noite.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeletorDeData(
    titulo: String,
    inicial: LocalDate,
    minima: LocalDate?,
    onConfirmar: (LocalDate) -> Unit,
    onCancelar: () -> Unit,
) {
    val minimaMillis = minima?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    val estado = rememberDatePickerState(
        initialSelectedDateMillis = inicial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                minimaMillis == null || utcTimeMillis >= minimaMillis
        },
    )
    DatePickerDialog(
        onDismissRequest = onCancelar,
        confirmButton = {
            TextButton(
                onClick = {
                    estado.selectedDateMillis?.let {
                        onConfirmar(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                },
                enabled = estado.selectedDateMillis != null,
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
    ) {
        DatePicker(state = estado, title = { Text(titulo, Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VeiculoFormulario(
    atual: VeiculoDto?,
    erroServidor: String?,
    salvando: Boolean,
    onVoltar: () -> Unit,
    onSalvar: (VeiculoRequestDto) -> Unit,
) {
    var placa by remember(atual) { mutableStateOf(atual?.placa ?: "") }
    var marca by remember(atual) { mutableStateOf(atual?.marca ?: "") }
    var modelo by remember(atual) { mutableStateOf(atual?.modelo ?: "") }
    var ano by remember(atual) { mutableStateOf(atual?.anoFabricacao?.toString() ?: "") }
    var capacidade by remember(atual) { mutableStateOf(atual?.capacidadeCargaToneladas ?: "") }
    var hodometro by remember(atual) { mutableStateOf(atual?.hodometroAtual?.toString() ?: "0") }
    var status by remember(atual) { mutableStateOf(atual?.status ?: "Disponivel") }
    var erro by remember { mutableStateOf<String?>(null) }
    var statusAberto by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (atual == null) "Novo veículo" else "Editar veículo") },
                navigationIcon = { IconButton(onClick = onVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            (erro ?: erroServidor)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedTextField(placa, { placa = it.uppercase() }, label = { Text("Placa") }, supportingText = { Text("ABC-1234 ou ABC1D23") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(marca, { marca = it }, label = { Text("Marca") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(modelo, { modelo = it }, label = { Text("Modelo") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(ano, { ano = it }, label = { Text("Ano de fabricação") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(capacidade, { capacidade = it.replace(',', '.') }, label = { Text("Capacidade de carga (toneladas)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(hodometro, { hodometro = it }, label = { Text("Hodômetro atual (km)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            Box {
                OutlinedButton(onClick = { statusAberto = true }) { Text("Status: ${statusLegivel(status)}") }
                DropdownMenu(expanded = statusAberto, onDismissRequest = { statusAberto = false }) {
                    listOf("Disponivel", "EmViagem", "EmManutencao", "Inativo").forEach { opcao ->
                        DropdownMenuItem(text = { Text(statusLegivel(opcao)) }, onClick = { status = opcao; statusAberto = false })
                    }
                }
            }
            Button(
                onClick = {
                    val body = runCatching {
                        VeiculoRequestDto(placa.replace("-", "").trim(), marca.trim(), modelo.trim(), ano.trim().toInt(), capacidade.trim().toDouble(), status, hodometro.trim().toInt())
                    }.getOrElse { erro = "Revise placa, ano, capacidade e hodômetro."; return@Button }
                    erro = null
                    onSalvar(body)
                },
                enabled = !salvando,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (salvando) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Salvar veículo")
            }
        }
    }
}

// Nova ordem de serviço (manutenção). Ao salvar, a API grava a ordem e lança a
// despesa em Finanças na mesma operação.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrdemFormulario(
    veiculos: List<VeiculoDto>,
    erroServidor: String?,
    salvando: Boolean,
    onVoltar: () -> Unit,
    onSalvar: (OrdemServicoRequestDto) -> Unit,
) {
    val disponiveis = veiculos.filter { aceitaOrdem(it) }
    var veiculoRotulo by remember { mutableStateOf<String?>(null) }
    val veiculo = disponiveis.firstOrNull { rotuloVeiculo(it) == veiculoRotulo }
    var data by remember { mutableStateOf(LocalDate.now()) }
    var escolhendoData by remember { mutableStateOf(false) }
    var descricao by remember { mutableStateOf("") }
    var tipo by remember { mutableStateOf("Preventiva") }
    var valorDigitos by remember { mutableStateOf("") }
    var hodometro by remember { mutableStateOf("") }
    var formaNome by remember { mutableStateOf<String?>(null) }
    var tentouSalvar by remember { mutableStateOf(false) }
    var erroCatalogo by remember { mutableStateOf<String?>(null) }

    // Formas de pagamento vêm da API de Finanças.
    LaunchedEffect(Unit) {
        if (FinanceRepository.formas.isEmpty()) {
            runCatching { FinanceRepository.catalogs() }
                .onFailure { erroCatalogo = "Não foi possível carregar as formas de pagamento. ${mensagemDeErroFrota(it)}" }
        }
    }
    // Ao escolher o veículo, o hodômetro já vem com o valor atual dele.
    LaunchedEffect(veiculo?.id) {
        if (veiculo != null) hodometro = veiculo.hodometroAtual.toString()
    }

    val forma = FinanceRepository.formas.firstOrNull { it.nome == formaNome }
    val erroVeiculo = if (veiculo == null) "Escolha o veículo." else null
    val erroDescricao = if (descricao.isBlank()) "Descreva o serviço." else null
    val erroValor = if ((valorDigitos.toLongOrNull() ?: 0L) <= 0L) "Informe um valor maior que zero." else null
    val erroHodometro = if (hodometro.isNotBlank() && hodometro.toIntOrNull() == null) "Use só números." else null
    val erroForma = if (forma == null) "Escolha a forma de pagamento." else null
    val valido = listOf(erroVeiculo, erroDescricao, erroValor, erroHodometro, erroForma).all { it == null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nova ordem de serviço") },
                navigationIcon = { IconButton(onClick = onVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            (erroServidor ?: erroCatalogo)?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            AppDropdown(
                label = "Veículo",
                options = disponiveis.map { rotuloVeiculo(it) },
                selected = veiculoRotulo,
                onSelected = { veiculoRotulo = it },
                modifier = Modifier.fillMaxWidth(),
            )
            ErroDoCampo(if (tentouSalvar) erroVeiculo else null)

            OutlinedTextField(
                value = data.format(FORMATO_DATA),
                onValueChange = {},
                readOnly = true,
                label = { Text("Data da manutenção") },
                trailingIcon = { IconButton(onClick = { escolhendoData = true }) { Icon(Icons.Filled.Event, "Escolher data") } },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = descricao,
                onValueChange = { if (it.length <= 200) descricao = it },
                label = { Text("Descrição do serviço") },
                placeholder = { Text("Ex.: Troca de óleo") },
                isError = tentouSalvar && erroDescricao != null,
                supportingText = { Text(erroDescricao?.takeIf { tentouSalvar } ?: "${descricao.length}/200") },
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Tipo", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Preventiva", "Corretiva").forEach { opcao ->
                    FilterChip(selected = tipo == opcao, onClick = { tipo = opcao }, label = { Text(opcao) })
                }
            }

            MoneyField(
                value = valorDigitos,
                onValueChange = { valorDigitos = it },
                label = "Valor total",
                isError = tentouSalvar && erroValor != null,
                errorText = erroValor,
            )

            OutlinedTextField(
                value = hodometro,
                onValueChange = { hodometro = it.filter { c -> c.isDigit() }.take(9) },
                label = { Text("Hodômetro na manutenção (km)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = erroHodometro != null,
                supportingText = { Text(erroHodometro ?: "Opcional. Vem com o hodômetro atual do veículo.") },
                modifier = Modifier.fillMaxWidth(),
            )

            AppDropdown(
                label = "Forma de pagamento",
                options = FinanceRepository.formas.map { it.nome },
                selected = formaNome,
                onSelected = { formaNome = it },
                modifier = Modifier.fillMaxWidth(),
            )
            ErroDoCampo(if (tentouSalvar) erroForma else null)

            Text(
                "A despesa entra em Finanças como Pendente, na categoria Manutenção.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = {
                    tentouSalvar = true
                    if (valido && veiculo != null && forma != null) {
                        onSalvar(
                            OrdemServicoRequestDto(
                                veiculoId = veiculo.id,
                                dataManutencao = data.toString(),
                                descricao = descricao.trim(),
                                tipo = tipo,
                                valorTotal = BigDecimal(valorDigitos).movePointLeft(2).toPlainString(),
                                hodometroNaManutencao = hodometro.toIntOrNull(),
                                formaPagamentoId = forma.id,
                            )
                        )
                    }
                },
                enabled = !salvando,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (salvando) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Criar ordem e despesa")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (escolhendoData) {
        SeletorDeData(
            titulo = "Data da manutenção",
            inicial = data,
            minima = null,
            onConfirmar = { data = it; escolhendoData = false },
            onCancelar = { escolhendoData = false },
        )
    }
}

@Composable
private fun ErroDoCampo(mensagem: String?) {
    if (mensagem != null) {
        Text(mensagem, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

// ---------- formatação e regras pequenas ----------

private val FORMATO_DATA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

// Veículo em viagem ou inativo não recebe ordem de serviço.
private fun aceitaOrdem(v: VeiculoDto) = v.status != "EmViagem" && v.status != "Inativo"

private fun rotuloVeiculo(v: VeiculoDto) = "${formatarPlaca(v.placa)} · ${v.marca} ${v.modelo}"

private fun statusLegivel(status: String) = when (status) {
    "Disponivel" -> "Disponível"
    "EmViagem" -> "Em viagem"
    "EmManutencao" -> "Em manutenção"
    else -> status
}

private fun formatarPlaca(placa: String): String =
    if (placa.length == 7) "${placa.substring(0, 3)}-${placa.substring(3)}" else placa

// 120000 -> "120.000"
private fun milhar(valor: Int): String = valor.toString().reversed().chunked(3).joinToString(".").reversed()

// "1234.5" (texto decimal da API) -> "R$ 1.234,50"
private fun reais(valor: String): String = runCatching {
    val centavos = BigDecimal(valor).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toBigInteger()
    "R$ " + formatMoney(centavos.toString())
}.getOrDefault("R$ $valor")

private fun dataIso(texto: String?): LocalDate? = texto?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

private fun dataBr(texto: String?): String = dataIso(texto)?.format(FORMATO_DATA) ?: (texto ?: "-")

private fun dataHoraBr(millis: Long): String =
    SimpleDateFormat("dd/MM 'às' HH:mm", Locale.forLanguageTag("pt-BR")).format(Date(millis))

// Sem conexão é a falha mais comum no celular; o resto vem da API, já em português.
private fun mensagemDeErroFrota(erro: Throwable): String = when (erro) {
    is IOException -> "Sem conexão com o servidor. Confira a internet e tente de novo."
    else -> erro.message ?: "Algo deu errado. Tente de novo."
}
