package com.fleetflow.mobile.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fleetflow.mobile.data.local.SituacaoSync
import com.fleetflow.mobile.data.local.StatusViagem
import com.fleetflow.mobile.data.local.ViagemEntity
import com.fleetflow.mobile.data.model.UserResponseDto
import com.fleetflow.mobile.data.repository.FrotaRepository
import com.fleetflow.mobile.data.repository.RegrasViagem
import com.fleetflow.mobile.data.repository.ViagemRepository
import com.fleetflow.mobile.data.sync.Conectividade
import com.fleetflow.mobile.ui.components.EmptyState
import com.fleetflow.mobile.ui.theme.AmbarAlerta
import kotlinx.coroutines.launch

private enum class TelaViagem { LISTA, ABRIR, CHECKLIST, FECHAR, PENDENCIAS }

// Aba Viagens: lista do Room (online e offline são iguais para a tela), abrir, checklist,
// fechar e, desde 11/11, a tela de pendências de sincronização. A ViagensScreen do Douglas
// (lista da API com o detalhe e os lançamentos da viagem) ainda não tem entrada na navegação.
@Composable
fun ListaViagensScreen(
    usuario: UserResponseDto,
    podeOperar: Boolean,
    podeVerOrdens: Boolean,
    onMessage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var tela by remember { mutableStateOf(TelaViagem.LISTA) }
    var viagemSelecionada by remember { mutableStateOf<String?>(null) }
    // Para onde o checklist volta: a lista, ou o fechamento que pediu o checklist.
    var depoisDoChecklist by remember { mutableStateOf(TelaViagem.LISTA) }
    var aba by remember { mutableStateOf(0) }
    var descartando by remember { mutableStateOf<ViagemEntity?>(null) }
    // null até o Room responder: sem isso, ao entrar na aba aparecia "Nenhuma viagem" por
    // um instante antes da lista.
    val viagensDoRoom by remember { ViagemRepository.observarViagens() }.collectAsState(initial = null)
    val viagens = viagensDoRoom.orEmpty()
    val pendentes by remember { ViagemRepository.observarPendentes() }.collectAsState(initial = 0)
    val avisos by remember { ViagemRepository.observarTotalAvisos() }.collectAsState(initial = 0)

    // A abertura precisa da frota com status e hodômetro atuais. Sem internet, ficam as
    // cópias do Room; erro da frota não impede a lista de viagens.
    suspend fun atualizarTudo() {
        runCatching { FrotaRepository.carregar(incluirOrdens = podeVerOrdens) }
        ViagemRepository.carregar()
    }

    LaunchedEffect(Unit) { atualizarTudo() }

    BackHandler(enabled = tela != TelaViagem.LISTA) { tela = TelaViagem.LISTA }

    val selecionada = viagemSelecionada
    when {
        tela == TelaViagem.ABRIR -> AbrirViagemScreen(
            usuario = usuario,
            onVoltar = { tela = TelaViagem.LISTA },
            onAberta = { viagem ->
                viagemSelecionada = viagem.id
                depoisDoChecklist = TelaViagem.LISTA
                tela = TelaViagem.CHECKLIST
                onMessage("Viagem aberta. Agora responda o checklist de saída.")
            },
        )
        tela == TelaViagem.CHECKLIST && selecionada != null -> ChecklistViagemScreen(
            viagemId = selecionada,
            onVoltar = { tela = TelaViagem.LISTA },
            onSalvo = { liberado ->
                tela = depoisDoChecklist
                onMessage(
                    if (liberado) "Checklist salvo."
                    else "Checklist salvo. Com item obrigatório não conforme, a viagem não pode ser fechada."
                )
            },
        )
        tela == TelaViagem.PENDENCIAS -> PendenciasSyncScreen(
            podeOperar = podeOperar,
            onVoltar = { tela = TelaViagem.LISTA },
        )
        tela == TelaViagem.FECHAR && selecionada != null -> FecharViagemScreen(
            viagemId = selecionada,
            onVoltar = { tela = TelaViagem.LISTA },
            onResponderChecklist = {
                depoisDoChecklist = TelaViagem.FECHAR
                tela = TelaViagem.CHECKLIST
            },
            onFechada = { km ->
                tela = TelaViagem.LISTA
                aba = 1
                onMessage("Viagem fechada: ${kmViagem(km)} km rodados.")
            },
        )
        else -> ListaViagens(
            viagens = viagens,
            lendoAparelho = viagensDoRoom == null,
            pendentes = pendentes,
            avisos = avisos,
            aba = aba,
            podeOperar = podeOperar,
            onAba = { aba = it },
            onAtualizar = { scope.launch { atualizarTudo() } },
            onAbrir = { tela = TelaViagem.ABRIR },
            onChecklist = { viagem ->
                viagemSelecionada = viagem.id
                depoisDoChecklist = TelaViagem.LISTA
                tela = TelaViagem.CHECKLIST
            },
            onFechar = { viagem ->
                viagemSelecionada = viagem.id
                tela = TelaViagem.FECHAR
            },
            onTentarDeNovo = { viagem ->
                scope.launch { ViagemRepository.tentarDeNovo(viagem.id) }
                onMessage("A viagem voltou para a fila de envio.")
            },
            onDescartar = { viagem -> descartando = viagem },
            onPendencias = { tela = TelaViagem.PENDENCIAS },
        )
    }

    descartando?.let { viagem ->
        AlertDialog(
            onDismissRequest = { descartando = null },
            title = { Text("Descartar alterações?") },
            text = {
                Text(
                    if (viagem.idServidor == null) {
                        "Esta viagem não chegou ao servidor e será apagada deste aparelho."
                    } else {
                        "As alterações que não subiram serão apagadas e a viagem volta a mostrar o que está no servidor."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    descartando = null
                    scope.launch {
                        ViagemRepository.descartarAlteracoes(viagem.id)
                        atualizarTudo()
                    }
                }) { Text("Descartar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { descartando = null }) { Text("Cancelar") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListaViagens(
    viagens: List<ViagemEntity>,
    lendoAparelho: Boolean,
    pendentes: Int,
    avisos: Int,
    aba: Int,
    podeOperar: Boolean,
    onAba: (Int) -> Unit,
    onAtualizar: () -> Unit,
    onAbrir: () -> Unit,
    onChecklist: (ViagemEntity) -> Unit,
    onFechar: (ViagemEntity) -> Unit,
    onTentarDeNovo: (ViagemEntity) -> Unit,
    onDescartar: (ViagemEntity) -> Unit,
    onPendencias: () -> Unit,
) {
    val comProblema = viagens.count { precisaAtencao(it) }
    val abertas = viagens.filter { it.status == StatusViagem.ABERTA }
    val encerradas = viagens.filter { it.status != StatusViagem.ABERTA }
    val lista = if (aba == 0) abertas else encerradas

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Viagens") },
                actions = {
                    IndicadorSincronizacao(pendentes = pendentes, comProblema = comProblema > 0, onClick = onPendencias)
                    IconButton(onClick = onAtualizar) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Atualizar e enviar pendências")
                    }
                },
            )
        },
        floatingActionButton = {
            if (podeOperar) {
                // Laranja da marca no botão da ação principal, como no plano.
                ExtendedFloatingActionButton(
                    text = { Text("Abrir viagem") },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    onClick = onAbrir,
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                )
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = aba) {
                Tab(selected = aba == 0, onClick = { onAba(0) }, text = { Text("Em andamento (${abertas.size})") })
                Tab(selected = aba == 1, onClick = { onAba(1) }, text = { Text("Encerradas (${encerradas.size})") })
            }
            if (ViagemRepository.carregando) LinearProgressIndicator(Modifier.fillMaxWidth())

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val semRede = !Conectividade.online
                if (semRede || ViagemRepository.semConexao || pendentes > 0 || avisos > 0) {
                    item {
                        AvisoFila(
                            semRede = semRede,
                            servidorFora = ViagemRepository.semConexao,
                            pendentes = pendentes,
                            comProblema = comProblema,
                            avisos = avisos,
                            onVer = onPendencias,
                        )
                    }
                }
                ViagemRepository.erroCarga?.let { mensagem ->
                    item { AvisoErroCarga(mensagem = mensagem, onTentarDeNovo = onAtualizar) }
                }
                if (lista.isEmpty() && !ViagemRepository.carregando && !lendoAparelho) {
                    item {
                        if (aba == 0) {
                            EmptyState(
                                icon = Icons.Rounded.Route,
                                title = "Nenhuma viagem em andamento",
                                message = if (podeOperar) "Toque em Abrir viagem para começar." else "As viagens abertas aparecem aqui.",
                            )
                        } else {
                            EmptyState(
                                icon = Icons.Rounded.CheckCircle,
                                title = "Nenhuma viagem encerrada",
                                message = "As viagens fechadas aparecem aqui, com os km rodados.",
                            )
                        }
                    }
                }
                items(lista, key = { it.id }) { viagem ->
                    CartaoViagemOffline(
                        viagem = viagem,
                        podeOperar = podeOperar,
                        onChecklist = { onChecklist(viagem) },
                        onFechar = { onFechar(viagem) },
                        onTentarDeNovo = { onTentarDeNovo(viagem) },
                        onDescartar = { onDescartar(viagem) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CartaoViagemOffline(
    viagem: ViagemEntity,
    podeOperar: Boolean,
    onChecklist: () -> Unit,
    onFechar: () -> Unit,
    onTentarDeNovo: () -> Unit,
    onDescartar: () -> Unit,
) {
    val aberta = viagem.status == StatusViagem.ABERTA
    val comErro = precisaAtencao(viagem)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${viagem.rotaOrigem} → ${viagem.rotaDestino}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                SeloStatus(aberta)
            }
            Text(
                "${placaViagem(viagem.veiculoPlaca)} · ${viagem.veiculoMarca} ${viagem.veiculoModelo}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Motorista: ${viagem.motoristaNome}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val linhaDatas = if (aberta) {
                "Aberta em ${dataHoraViagem(viagem.dataAbertura)} · hodômetro ${kmViagem(viagem.hodometroInicial)} km"
            } else {
                val km = viagem.hodometroFinal?.let { RegrasViagem.kmRodados(viagem.hodometroInicial, it) }
                "Fechada em ${dataHoraViagem(viagem.dataFechamento)}" + (km?.let { " · ${kmViagem(it)} km rodados" } ?: "")
            }
            Text(linhaDatas, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SeloSincronizacao(viagem)

            val checklistOk = viagem.checklistRespondido && viagem.checklistLiberado
            if (aberta && !checklistOk) {
                Text(
                    if (!viagem.checklistRespondido) "Checklist de saída pendente"
                    else "Item obrigatório não conforme no checklist",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (podeOperar && comErro) {
                // Erro e conflito pedem decisão: tentar de novo ou desistir das alterações.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDescartar, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Descartar alterações", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = onTentarDeNovo, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Tentar de novo")
                    }
                }
            }

            if (aberta && podeOperar) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!checklistOk) {
                        TextButton(onClick = onChecklist, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(if (!viagem.checklistRespondido) "Responder checklist" else "Responder de novo")
                        }
                    }
                    Button(onClick = onFechar, modifier = Modifier.heightIn(min = 48.dp)) { Text("Fechar viagem") }
                }
            }
        }
    }
}

// Status da viagem em texto, nunca só pela cor.
@Composable
private fun SeloStatus(aberta: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = if (aberta) 0.12f else 0.06f),
        shape = RoundedCornerShape(50),
    ) {
        Text(
            if (aberta) "Em andamento" else "Encerrada",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

// O selo da entrega de 04/11: cada registro que ainda não subiu mostra que está pendente.
@Composable
private fun SeloSincronizacao(viagem: ViagemEntity) {
    when (viagem.situacaoSync) {
        SituacaoSync.PENDENTE -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.CloudUpload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Pendente de sincronização",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SituacaoSync.ERRO -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Não sincronizado: ${viagem.erroSync ?: "a API recusou a operação"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        // Entrega de 11/11: conflito com o servidor, sinalizado até alguém revisar.
        SituacaoSync.CONFLITO -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Warning,
                contentDescription = null,
                tint = AmbarAlerta,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Conflito: ${viagem.erroSync ?: "aguardando revisão"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        else -> Unit
    }
}

private fun precisaAtencao(viagem: ViagemEntity): Boolean =
    viagem.situacaoSync == SituacaoSync.ERRO || viagem.situacaoSync == SituacaoSync.CONFLITO

// Ícone discreto na barra (especificação): sincronizado, sincronizando ou com pendências,
// com a contagem. Toque abre a tela de pendências.
@Composable
private fun IndicadorSincronizacao(pendentes: Int, comProblema: Boolean, onClick: () -> Unit) {
    val (icone, situacao) = when {
        !Conectividade.online -> Icons.Rounded.CloudOff to "Sem conexão"
        ViagemRepository.enviando -> Icons.Rounded.Sync to "Sincronizando"
        pendentes > 0 -> Icons.Rounded.CloudUpload to "$pendentes pendências"
        else -> Icons.Rounded.CloudDone to "Tudo sincronizado"
    }
    IconButton(onClick = onClick) {
        BadgedBox(
            badge = {
                if (pendentes > 0) {
                    Badge(
                        containerColor = if (comProblema) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.secondary,
                    ) { Text(if (pendentes > 99) "99+" else "$pendentes") }
                }
            },
        ) {
            Icon(icone, contentDescription = "$situacao. Ver pendências de sincronização")
        }
    }
}

// Faixa no topo da lista. Antes, sem rede e com fila, dizia "Enviando para o servidor":
// agora a situação da rede vem do Conectividade, e não só da última carga.
@Composable
private fun AvisoFila(
    semRede: Boolean,
    servidorFora: Boolean,
    pendentes: Int,
    comProblema: Int,
    avisos: Int,
    onVer: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when {
                    comProblema > 0 -> Icons.Rounded.ErrorOutline
                    semRede || servidorFora -> Icons.Rounded.CloudOff
                    else -> Icons.Rounded.CloudUpload
                },
                contentDescription = null,
                tint = if (comProblema > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            val conexao = when {
                semRede -> "Sem conexão. Mostrando as viagens salvas no aparelho."
                servidorFora -> "Não foi possível falar com o servidor. Mostrando as viagens salvas no aparelho."
                pendentes > 0 && comProblema == 0 -> "Enviando para o servidor."
                else -> ""
            }
            val fila = when (pendentes) {
                0 -> ""
                1 -> " 1 alteração espera para subir."
                else -> " $pendentes alterações esperam para subir."
            }
            val problema = when (comProblema) {
                0 -> ""
                1 -> " 1 viagem precisa de atenção."
                else -> " $comProblema viagens precisam de atenção."
            }
            val aviso = when (avisos) {
                0 -> ""
                1 -> " 1 aviso de sincronização."
                else -> " $avisos avisos de sincronização."
            }
            Text(
                (conexao + fila + problema + aviso).trim(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp),
            )
            if (pendentes > 0 || avisos > 0) {
                TextButton(onClick = onVer, modifier = Modifier.heightIn(min = 48.dp)) { Text("Ver") }
            }
        }
    }
}

@Composable
private fun AvisoErroCarga(mensagem: String, onTentarDeNovo: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                mensagem,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onTentarDeNovo) { Text("Tentar de novo") }
        }
    }
}
