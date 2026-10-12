package com.fleetflow.mobile.ui.screens

import android.net.Uri
import com.fleetflow.mobile.data.repository.FinanceRepository
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
    val dataVencimento:String? = null,
    val dataPagamento:String? = null,
    val comprovanteUri:Uri? = null,
    val syncVersion:Int? = null,
    val criadoEm: Long = System.currentTimeMillis(),
)

// ============================================================
// REPOSITÓRIO: a tela lê 'itens' e chama 'add'. Por dentro, fala com o Room.
// ============================================================
object MovimentacaoRepo {
    val itens = mutableStateListOf<Movimentacao>()
    fun clear() {FinanceRepository.clear()}
}
val CATEGORIAS:List<String> get() = FinanceRepository.categorias.map {it.titulo}
val FORMAS:List<String> get() = FinanceRepository.formas.map {it.nome}
fun tipoDaCategoria(cat:String):Tipo = if(FinanceRepository.categorias.firstOrNull {it.titulo==cat}?.tipo=="Entrada") Tipo.ENTRADA else Tipo.SAIDA

fun centsToBRL(cents: Long): String = "R$ " + formatMoney(cents.toString())

val hoje: String = SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")).format(Date())

// ============================================================
// TELA: mostra a lista ou o formulário
// ============================================================
@Composable
fun FinancasScreen(onMessage:(String)->Unit,onCategorias:()->Unit={},onFormas:()->Unit={},onDividas:()->Unit={}) {
    var showForm by remember {mutableStateOf(false)}
    var editing by remember {mutableStateOf<Movimentacao?>(null)}
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    var busca by remember {mutableStateOf("")}
    var status by remember {mutableStateOf("")}
    var tipo by remember {mutableStateOf("")}
    var page by remember {mutableStateOf(1)}
    val scope=rememberCoroutineScope()
    suspend fun load() {busy=true;error=null;runCatching {FinanceRepository.catalogs();FinanceRepository.load(page,busca,status,tipo)}.onFailure {error=it.message};busy=false}
    LaunchedEffect(page,status,tipo) {load()}
    BackHandler(enabled=showForm) {showForm=false;editing=null}
    if(showForm) {
        NovoLancamentoForm(onInvalid={onMessage("Preencha valor, descrição, categoria, forma de pagamento e datas válidas")},initial=editing,busy=busy,onCancel={showForm=false;editing=null},onSave={m->
            scope.launch {
                busy=true
                runCatching {FinanceRepository.save(m)}.onSuccess {id->
                    m.comprovanteUri?.let {uri->runCatching {FinanceRepository.upload(id,uri)}.onFailure {onMessage("Lançamento salvo. Comprovante não enviado: ${it.message}")}}
                    showForm=false;editing=null
                    onMessage("Lançamento salvo na API")
                    runCatching {FinanceRepository.load(page,busca,status,tipo)}.onFailure {onMessage("Salvo. Falha ao atualizar a lista: ${it.message}")}
                }.onFailure {onMessage(it.message?:"Falha ao salvar")}
                busy=false
            }
        })
    } else Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
            TextButton(onClick=onCategorias){Text("Categorias")}
            TextButton(onClick=onFormas){Text("Pagamentos")}
            TextButton(onClick=onDividas){Text("Dívidas")}
        }
        Row(Modifier.padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
            OutlinedTextField(value=busca,onValueChange={busca=it},label={Text("Buscar descrição")},modifier=Modifier.weight(1f),singleLine=true)
            TextButton(onClick={page=1;scope.launch {load()}}){Text("Buscar")}
        }
        Row(Modifier.padding(16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            AppDropdown("Status",listOf("Todos","Pendente","Pago"),status.ifEmpty {"Todos"},{status=if(it=="Todos")"" else it;page=1},Modifier.weight(1f))
            AppDropdown("Tipo",listOf("Todos","Entrada","Saída"),if(tipo=="Saida")"Saída" else tipo.ifEmpty {"Todos"},{tipo=when(it){"Todos"->"";"Saída"->"Saida";else->it};page=1},Modifier.weight(1f))
        }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let {Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(16.dp))}
        Box(Modifier.weight(1f)) {
            MovimentacoesList(MovimentacaoRepo.itens,onAdd={editing=null;showForm=true},onEdit={editing=it;showForm=true},
                onPay={m->scope.launch {runCatching {FinanceRepository.pay(m);load()}.onFailure {onMessage(it.message?:"Erro")}}},
                onDelete={m->scope.launch {runCatching {FinanceRepository.delete(m);load()}.onFailure {onMessage(it.message?:"Erro")}}})
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
            TextButton(enabled=page>1&&!busy,onClick={page--}){Text("Anterior")}
            Text("$page / ${FinanceRepository.totalPages}")
            TextButton(enabled=page<FinanceRepository.totalPages&&!busy,onClick={page++}){Text("Próxima")}
        }
    }
}

// ============================================================
// LISTA
// ============================================================
@Composable
private fun MovimentacoesList(itens: List<Movimentacao>, onAdd: () -> Unit, onEdit:(Movimentacao)->Unit, onPay:(Movimentacao)->Unit, onDelete:(Movimentacao)->Unit) {
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
                    Text("Saldo atual: R$ ${FinanceRepository.saldo?.saldoAtual ?: "0.00"}",style=MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(12.dp))
                    val entradas = java.math.BigDecimal(FinanceRepository.totais?.entradas ?: "0").movePointRight(2).toLong()
                    val saidas = java.math.BigDecimal(FinanceRepository.totais?.saidas ?: "0").movePointRight(2).toLong()
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
                    items(itens, key = { it.id }) { m -> Column {
                        MovRow(m)
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                            TextButton(onClick={onEdit(m)}){Text("Editar")}
                            if(m.status==StatusMov.PENDENTE) TextButton(onClick={onPay(m)}){Text("Pagar/Receber")}
                            var confirm by remember {mutableStateOf(false)}
                            TextButton(onClick={confirm=true}){Text("Excluir")}
                            if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("Excluir lançamento?")},text={Text(m.descricao)},
                                confirmButton={TextButton(onClick={confirm=false;onDelete(m)}){Text("Excluir")}},dismissButton={TextButton(onClick={confirm=false}){Text("Cancelar")}})
                        }
                    } }
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
private fun NovoLancamentoForm(onInvalid:()->Unit={},initial:Movimentacao?=null,busy:Boolean=false,onCancel: () -> Unit, onSave: (Movimentacao) -> Unit) {
    var valor by remember(initial) { mutableStateOf(initial?.valorCentavos?.toString() ?: "") }
    var descricao by remember(initial) { mutableStateOf(initial?.descricao ?: "") }
    var categoria by remember(initial) { mutableStateOf(initial?.categoria) }
    var forma by remember(initial) { mutableStateOf(initial?.formaPagamento) }
    var status by remember(initial) { mutableStateOf(initial?.status ?: StatusMov.PENDENTE) }
    var vencimento by remember(initial) {mutableStateOf(initial?.dataVencimento ?: hoje)}
    var pagamento by remember(initial) {mutableStateOf(initial?.dataPagamento?.takeIf {it.isNotBlank()} ?: hoje)}
    var comprovante by remember {mutableStateOf<Uri?>(null)}
    var tentouSalvar by remember { mutableStateOf(false) }

    val valorValido = valor.isNotEmpty() && (valor.toLongOrNull() ?: 0) > 0
    val descricaoValida = descricao.isNotBlank()
    val datasValidas = runCatching {com.fleetflow.mobile.data.repository.isoDate(vencimento);if(status==StatusMov.PAGO)com.fleetflow.mobile.data.repository.isoDate(pagamento)}.isSuccess
    val formValido = valorValido && descricaoValida && categoria != null && forma != null && datasValidas

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

            OutlinedTextField(value=vencimento,onValueChange={vencimento=it},label={Text("Vencimento dd/MM/aaaa")},modifier=Modifier.fillMaxWidth(),isError=tentouSalvar&&!datasValidas)
            if(status==StatusMov.PAGO) OutlinedTextField(value=pagamento,onValueChange={pagamento=it},label={Text("Pagamento dd/MM/aaaa")},modifier=Modifier.fillMaxWidth(),isError=tentouSalvar&&!datasValidas)
            ComprovanteAnexo(comprovanteUri=comprovante,onComprovanteChange={comprovante=it})
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
                    if (!formValido) onInvalid()
                    if (formValido) {
                        onSave(
                            Movimentacao(
                                id=initial?.id ?: UUID.randomUUID().toString(),syncVersion=initial?.syncVersion,
                                dataVencimento=vencimento,dataPagamento=if(status==StatusMov.PAGO)pagamento else null,comprovanteUri=comprovante,
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
                enabled = !busy, modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) { Text(if(busy) "Salvando…" else "Salvar") }
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

