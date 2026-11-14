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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.fleetflow.mobile.data.local.AvisoSyncEntity
import com.fleetflow.mobile.data.local.OperacaoSyncEntity
import com.fleetflow.mobile.data.local.SituacaoSync
import com.fleetflow.mobile.data.local.VerboSync
import com.fleetflow.mobile.data.repository.PendenciaSync
import com.fleetflow.mobile.data.repository.ViagemRepository
import com.fleetflow.mobile.data.sync.Conectividade
import com.fleetflow.mobile.ui.components.EmptyState
import com.fleetflow.mobile.ui.theme.AmbarAlerta
import kotlinx.coroutines.launch

// Tela de pendências de sincronização (especificação, "O que o usuário vê"): o que ainda
// não subiu, o erro de cada item, tentar de novo e descartar. Também mostra os avisos da
// resolução de conflito, quando o app descartou uma alteração sozinho.
// Abre pelo Perfil ("Pendências de sincronização") e pelo ícone da barra de Viagens.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendenciasSyncScreen(
    podeOperar: Boolean = true,
    onVoltar: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    // null até o Room responder, para não mostrar "Tudo sincronizado" antes da hora.
    val pendenciasDoRoom by remember { ViagemRepository.observarPendencias() }.collectAsState(initial = null)
    val pendencias = pendenciasDoRoom.orEmpty()
    val avisos by remember { ViagemRepository.observarAvisos() }.collectAsState(initial = emptyList())
    var descartando by remember { mutableStateOf<PendenciaSync?>(null) }

    val totalOperacoes = pendencias.sumOf { it.operacoes.size }
    val comProblema = pendencias.count { it.situacao != SituacaoSync.PENDENTE }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pendências de sincronização") },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (pendenciasDoRoom != null) {
                item {
                    ResumoSincronizacao(
                        totalOperacoes = totalOperacoes,
                        comProblema = comProblema,
                        onEnviarAgora = {
                            ViagemRepository.sincronizarAgora()
                            scope.launch { snackbar.showSnackbar("Enviando o que está na fila.") }
                        },
                    )
                }
            }

            if (avisos.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Avisos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { scope.launch { ViagemRepository.dispensarAvisos() } }) {
                            Text("Limpar avisos")
                        }
                    }
                }
                items(avisos, key = { "aviso-${it.id}" }) { aviso ->
                    CartaoAviso(aviso, onOk = { scope.launch { ViagemRepository.dispensarAviso(aviso.id) } })
                }
            }

            if (pendenciasDoRoom == null) {
                // Lendo a fila do aparelho: não mostra nada por um instante.
            } else if (pendencias.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.CloudDone,
                        title = "Tudo sincronizado",
                        message = "Nenhuma alteração deste aparelho está esperando para subir.",
                    )
                }
            } else {
                item { Text("Esperando para subir", style = MaterialTheme.typography.titleMedium) }
                items(pendencias, key = { it.registroId }) { pendencia ->
                    CartaoPendencia(
                        pendencia = pendencia,
                        podeOperar = podeOperar,
                        onTentarDeNovo = {
                            scope.launch {
                                ViagemRepository.tentarDeNovo(pendencia.registroId)
                                snackbar.showSnackbar("A viagem voltou para a fila de envio.")
                            }
                        },
                        onDescartar = { descartando = pendencia },
                    )
                }
            }
        }
    }

    descartando?.let { pendencia ->
        AlertDialog(
            onDismissRequest = { descartando = null },
            title = { Text("Descartar alterações?") },
            text = {
                Text(
                    if (pendencia.viagem?.idServidor == null) {
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
                        ViagemRepository.descartarAlteracoes(pendencia.registroId)
                        // Traz a versão do servidor para a viagem que já existia lá.
                        ViagemRepository.carregar()
                    }
                }) { Text("Descartar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { descartando = null }) { Text("Cancelar") }
            },
        )
    }
}

// Ícone discreto + frase: sincronizado, sincronizando, sem conexão ou com pendências.
@Composable
private fun ResumoSincronizacao(totalOperacoes: Int, comProblema: Int, onEnviarAgora: () -> Unit) {
    val online = Conectividade.online
    val (icone, texto) = when {
        !online -> Icons.Rounded.CloudOff to
            "Sem conexão. As alterações ficam salvas no aparelho e sobem quando a internet voltar."
        ViagemRepository.enviando -> Icons.Rounded.Sync to "Enviando para o servidor…"
        totalOperacoes == 0 -> Icons.Rounded.CloudDone to "Tudo sincronizado."
        comProblema > 0 -> Icons.Rounded.ErrorOutline to
            (if (comProblema == 1) "1 viagem precisa da sua atenção." else "$comProblema viagens precisam da sua atenção.")
        else -> Icons.Rounded.CloudUpload to
            (if (totalOperacoes == 1) "1 alteração esperando para subir." else "$totalOperacoes alterações esperando para subir.")
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (totalOperacoes > 0) {
                Button(
                    onClick = onEnviarAgora,
                    enabled = online && !ViagemRepository.enviando,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) { Text("Enviar agora") }
            }
        }
    }
}

@Composable
private fun CartaoAviso(aviso: AvisoSyncEntity, onOk: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 4.dp)) {
            Icon(
                Icons.Rounded.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(aviso.titulo, style = MaterialTheme.typography.titleSmall)
                Text(aviso.mensagem, style = MaterialTheme.typography.bodyMedium)
                Text(
                    dataHoraLocal(aviso.criadoEm),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onOk, modifier = Modifier.heightIn(min = 48.dp)) { Text("Entendi") }
                }
            }
        }
    }
}

@Composable
private fun CartaoPendencia(
    pendencia: PendenciaSync,
    podeOperar: Boolean,
    onTentarDeNovo: () -> Unit,
    onDescartar: () -> Unit,
) {
    val viagem = pendencia.viagem
    val situacao = pendencia.situacao
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    viagem?.let { "${it.rotaOrigem} → ${it.rotaDestino}" } ?: "Viagem",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                SeloSituacao(situacao)
            }
            viagem?.let {
                Text(
                    "${placaViagem(it.veiculoPlaca)} · aberta em ${dataHoraViagem(it.dataAbertura)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            pendencia.operacoes.forEach { operacao -> LinhaOperacao(operacao) }

            val precisaAtencao = situacao == SituacaoSync.ERRO || situacao == SituacaoSync.CONFLITO
            if (podeOperar) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDescartar, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Descartar", color = MaterialTheme.colorScheme.error)
                    }
                    if (precisaAtencao) {
                        Button(onClick = onTentarDeNovo, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Tentar de novo")
                        }
                    }
                }
            }
        }
    }
}

// Uma linha por operação da fila: o que é, em que pé está e o erro, se houver.
@Composable
private fun LinhaOperacao(operacao: OperacaoSyncEntity) {
    val (icone, cor) = iconeDaSituacao(operacao.situacao)
    Row(verticalAlignment = Alignment.Top) {
        Icon(icone, contentDescription = null, tint = cor, modifier = Modifier.size(18.dp).padding(top = 2.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(nomeDaOperacao(operacao.verbo), style = MaterialTheme.typography.bodyMedium)
            val detalhe = when (operacao.situacao) {
                SituacaoSync.ERRO -> operacao.ultimoErro ?: "A API recusou esta alteração."
                SituacaoSync.CONFLITO -> operacao.ultimoErro ?: "Conflito com o servidor, aguardando revisão."
                else -> if (operacao.tentativas > 0) {
                    "Tentativa ${operacao.tentativas} de ${ViagemRepository.MAX_TENTATIVAS} falhou: " +
                        (operacao.ultimoErro ?: "sem conexão")
                } else {
                    "Salva em ${dataHoraLocal(operacao.criadoEm)}, esperando a conexão."
                }
            }
            Text(
                detalhe,
                style = MaterialTheme.typography.labelMedium,
                color = if (operacao.situacao == SituacaoSync.ERRO) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// Situação em texto, nunca só pela cor.
@Composable
private fun SeloSituacao(situacao: String) {
    val (texto, cor) = when (situacao) {
        SituacaoSync.ERRO -> "Erro" to MaterialTheme.colorScheme.error
        SituacaoSync.CONFLITO -> "Conflito" to AmbarAlerta
        else -> "Pendente" to MaterialTheme.colorScheme.primary
    }
    Surface(color = cor.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
        Text(
            texto,
            style = MaterialTheme.typography.labelSmall,
            color = if (situacao == SituacaoSync.CONFLITO) MaterialTheme.colorScheme.onSurface else cor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun iconeDaSituacao(situacao: String): Pair<ImageVector, Color> = when (situacao) {
    SituacaoSync.ERRO -> Icons.Rounded.ErrorOutline to MaterialTheme.colorScheme.error
    SituacaoSync.CONFLITO -> Icons.Rounded.Warning to AmbarAlerta
    else -> Icons.Rounded.CloudUpload to MaterialTheme.colorScheme.primary
}

private fun nomeDaOperacao(verbo: String): String = when (verbo) {
    VerboSync.ABRIR -> "Abertura da viagem"
    VerboSync.CHECKLIST -> "Checklist de saída"
    VerboSync.FECHAR -> "Fechamento da viagem"
    else -> verbo
}
