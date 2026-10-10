package com.fleetflow.mobile.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fleetflow.mobile.data.local.AppDatabase
import com.fleetflow.mobile.data.local.MovimentacaoDao
import com.fleetflow.mobile.data.local.MovimentacaoEntity
import com.fleetflow.mobile.ui.components.AppDropdown
import com.fleetflow.mobile.ui.components.EmptyState
import com.fleetflow.mobile.ui.components.MoneyField
import com.fleetflow.mobile.ui.components.formatMoney
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// ============================================================
// MODELO (o que a tela usa)
// ============================================================
enum class Tipo { ENTRADA, SAIDA }
enum class StatusMov { PENDENTE, PAGO }

data class Movimentacao(
    val id: String = UUID.randomUUID().toString(),
    val descricao: String,
    val valorCentavos: Long,       // dinheiro em centavos, nunca Double
    val tipo: Tipo,
    val categoria: String,
    val formaPagamento: String,
    val data: String,              // dd/MM/yyyy
    val status: StatusMov,
    val criadoEm: Long = System.currentTimeMillis(),
)

// ============================================================
// REPOSITÓRIO: a tela lê 'itens' e chama 'add'. Por dentro, fala com o Room.
// ============================================================
object MovimentacaoRepo {

    // Escopo próprio, que vive enquanto o app vive.
    // Antes era o escopo da tela, que morre ao girar o celular, e a lista parava de atualizar.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var dao: MovimentacaoDao? = null

    // Lista observável: o Compose redesenha a tela quando ela muda.
    val itens = mutableStateListOf<Movimentacao>()

    // Liga o repositório ao banco. Chamar de novo não faz nada.
    fun init(database: AppDatabase) {
        if (dao != null) return
        val d = database.movimentacaoDao()
        dao = d

        // Banco vazio (primeira vez): grava os exemplos.
        scope.launch {
            if (d.contar() == 0) {
                seedInicial.forEach { d.inserir(it.toEntity()) }
            }
        }

        // Fica escutando a tabela: mudou no banco, atualiza 'itens'.
        scope.launch {
            d.observarTodas().collect { entities ->
                itens.clear()
                itens.addAll(entities.map { it.toModel() })
            }
        }
    }

    // Grava no banco. Não mexe em 'itens': o collect acima faz isso sozinho.
    fun add(m: Movimentacao) {
        val d = dao ?: return
        scope.launch { d.inserir(m.toEntity()) }
    }
}

// Exemplos, com horário de inclusão decrescente para aparecerem nesta ordem.
private val agora = System.currentTimeMillis()
private val seedInicial = listOf(
    Movimentacao(descricao = "Frete Varginha → Campinas", valorCentavos = 520000, tipo = Tipo.ENTRADA, categoria = "Frete", formaPagamento = "Pix", data = "18/09/2026", status = StatusMov.PAGO, criadoEm = agora),
    Movimentacao(descricao = "Abastecimento RTJ4E12", valorCentavos = 89000, tipo = Tipo.SAIDA, categoria = "Combustível", formaPagamento = "Cartão Corporativo", data = "17/09/2026", status = StatusMov.PAGO, criadoEm = agora - 1_000),
    Movimentacao(descricao = "Manutenção preventiva", valorCentavos = 125000, tipo = Tipo.SAIDA, categoria = "Manutenção", formaPagamento = "Boleto", data = "16/09/2026", status = StatusMov.PENDENTE, criadoEm = agora - 2_000),
    Movimentacao(descricao = "Parcela financiamento", valorCentavos = 420000, tipo = Tipo.SAIDA, categoria = "Financiamento", formaPagamento = "Boleto", data = "10/09/2026", status = StatusMov.PENDENTE, criadoEm = agora - 3_000),
)

// Tradução entre o modelo da tela (enums) e a entity do banco (texto).
private fun Movimentacao.toEntity() = MovimentacaoEntity(
    id = id, descricao = descricao, valorCentavos = valorCentavos,
    tipo = tipo.name, categoria = categoria, formaPagamento = formaPagamento,
    data = data, status = status.name, criadoEm = criadoEm,
)

private fun MovimentacaoEntity.toModel() = Movimentacao(
    id = id, descricao = descricao, valorCentavos = valorCentavos,
    tipo = Tipo.valueOf(tipo), categoria = categoria, formaPagamento = formaPagamento,
    data = data, status = StatusMov.valueOf(status), criadoEm = criadoEm,
)

val CATEGORIAS = listOf("Frete", "Combustível", "Pedágio", "Manutenção", "Salário", "Financiamento", "Outros")
val FORMAS = listOf("Boleto", "Pix", "Transferência TED", "Cartão Corporativo", "Dinheiro")

fun tipoDaCategoria(cat: String): Tipo = if (cat == "Frete") Tipo.ENTRADA else Tipo.SAIDA

fun centsToBRL(cents: Long): String = "R$ " + formatMoney(cents.toString())

val hoje: String = SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")).format(Date())

// ============================================================
// TELA: mostra a lista ou o formulário
// ============================================================
@Composable
fun FinancasScreen(onMessage: (String) -> Unit) {
    var showForm by remember { mutableStateOf(false) }

    BackHandler(enabled = showForm) { showForm = false }

    if (showForm) {
        NovoLancamentoForm(
            onCancel = { showForm = false },
            onSave = { m ->
                MovimentacaoRepo.add(m)
                showForm = false
                onMessage("Lançamento salvo")
            }
        )
    } else {
        MovimentacoesList(
            itens = MovimentacaoRepo.itens,
            onAdd = { showForm = true }
        )
    }
}

// ============================================================
// LISTA
// ============================================================
@Composable
private fun MovimentacoesList(itens: List<Movimentacao>, onAdd: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        if (itens.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Inbox,
                title = "Nenhuma movimentação",
                message = "Toque em adicionar para registrar a primeira entrada ou saída.",
                actionLabel = "Adicionar",
                onAction = onAdd,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Movimentações", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(12.dp))
                    val entradas = itens.filter { it.tipo == Tipo.ENTRADA }.sumOf { it.valorCentavos }
                    val saidas = itens.filter { it.tipo == Tipo.SAIDA }.sumOf { it.valorCentavos }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ResumoChip("Entradas", centsToBRL(entradas), MaterialTheme.colorScheme.tertiary)
                        ResumoChip("Saídas", centsToBRL(saidas), MaterialTheme.colorScheme.error)
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp),
                ) {
                    // key: ajuda o Compose a saber qual item é qual quando a lista muda
                    items(itens, key = { it.id }) { m -> MovRow(m) }
                }
            }
        }

        FloatingActionButton(
            onClick = onAdd,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Adicionar")
        }
    }
}

@Composable
private fun RowScope.ResumoChip(label: String, valor: String, cor: Color) {
    Card(Modifier.weight(1f)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(valor, style = MaterialTheme.typography.titleMedium, color = cor)
        }
    }
}

@Composable
private fun MovRow(m: Movimentacao) {
    val entrada = m.tipo == Tipo.ENTRADA
    val cor = if (entrada) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (entrada) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown, null, tint = cor)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(m.descricao, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${m.categoria} • ${m.data}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text((if (entrada) "+" else "-") + centsToBRL(m.valorCentavos), color = cor, fontWeight = FontWeight.SemiBold)
            if (m.status == StatusMov.PENDENTE) {
                Text("Pendente", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
}

// ============================================================
// FORMULÁRIO
// ============================================================
@Composable
private fun NovoLancamentoForm(onCancel: () -> Unit, onSave: (Movimentacao) -> Unit) {
    var valor by remember { mutableStateOf("") }
    var descricao by remember { mutableStateOf("") }
    var categoria by remember { mutableStateOf<String?>(null) }
    var forma by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf(StatusMov.PENDENTE) }
    var tentouSalvar by remember { mutableStateOf(false) }

    val valorValido = valor.isNotEmpty() && valor.toLong() > 0
    val descricaoValida = descricao.isNotBlank()
    val formValido = valorValido && descricaoValida && categoria != null && forma != null

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = MaterialTheme.colorScheme.onPrimary)
            }
            Text("Novo lançamento", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
        }

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MoneyField(
                value = valor, onValueChange = { valor = it }, label = "Valor",
                isError = tentouSalvar && !valorValido, errorText = "Informe um valor maior que zero",
            )
            OutlinedTextField(
                value = descricao, onValueChange = { descricao = it }, label = { Text("Descrição") },
                isError = tentouSalvar && !descricaoValida,
                supportingText = { if (tentouSalvar && !descricaoValida) Text("Campo obrigatório") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            AppDropdown("Categoria", CATEGORIAS, categoria, { categoria = it })
            AppDropdown("Forma de pagamento", FORMAS, forma, { forma = it })

            Text("Status", style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusButton("Pendente", status == StatusMov.PENDENTE) { status = StatusMov.PENDENTE }
                StatusButton("Pago/Recebido", status == StatusMov.PAGO) { status = StatusMov.PAGO }
            }
        }

        Surface(shadowElevation = 8.dp) {
            Button(
                onClick = {
                    tentouSalvar = true
                    if (formValido) {
                        onSave(
                            Movimentacao(
                                descricao = descricao.trim(),
                                valorCentavos = valor.toLong(),
                                tipo = tipoDaCategoria(categoria!!),
                                categoria = categoria!!,
                                formaPagamento = forma!!,
                                data = hoje,
                                status = status,
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) { Text("Salvar") }
        }
    }
}

@Composable
private fun RowScope.StatusButton(text: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick, Modifier.weight(1f)) { Text(text) }
    } else {
        OutlinedButton(onClick, Modifier.weight(1f)) { Text(text) }
    }
}

