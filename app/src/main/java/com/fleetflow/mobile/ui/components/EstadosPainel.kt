package com.fleetflow.mobile.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fleetflow.mobile.data.repository.FalhaPainel
import com.fleetflow.mobile.data.repository.Painel
import com.fleetflow.mobile.data.repository.RegrasCache
import kotlinx.coroutines.delay
import java.lang.reflect.Type

// Estados de carregamento dos painéis (entrega de 18/11). Especificação: "Lista carregando:
// skeleton nos cards, e não tela em branco"; "Falha de rede: Tentar de novo"; "Gráfico sem
// dados mostra estado vazio explicando o que fazer"; cache com carimbo "atualizado às HH:mm".
// As cores saem do tema; a home atual, que tem cores fixas, passa as dela.

// Cria o painel da tela e já carrega: mostra o que está salvo e busca na API se passou da
// validade. Trocar a chave (outro período) troca o painel. Para listas e tipos genéricos,
// passe o tipo com TypeToken: object : TypeToken<List<X>>() {}.type
@Composable
fun <T : Any> rememberPainel(chave: String, tipo: Type, buscar: suspend () -> T): Painel<T> {
    // A busca mais recente que a tela passou, mesmo que ela mude sem trocar a chave.
    val buscarAtual by rememberUpdatedState(buscar)
    val painel = remember(chave) { Painel(chave, tipo, buscar = { buscarAtual() }) }
    LaunchedEffect(painel) { painel.carregar() }
    return painel
}

// Bloco cinza pulsando no lugar de um número ou de um gráfico que ainda não chegou.
@Composable
fun BlocoEsqueleto(
    modifier: Modifier = Modifier,
    forma: Shape = RoundedCornerShape(6.dp),
    cor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val pulso = rememberInfiniteTransition(label = "esqueleto")
    val alfa by pulso.animateFloat(
        initialValue = 0.06f,
        targetValue = 0.14f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 700), RepeatMode.Reverse),
        label = "alfa",
    )
    Box(
        modifier
            .clip(forma)
            .background(cor.copy(alpha = alfa))
            .semantics { contentDescription = "Carregando" }
    )
}

// "Atualizado às 18:42" e o botão de atualizar; enquanto busca a versão nova, gira.
@Composable
fun CarimboAtualizacao(
    atualizadoEm: Long,
    atualizando: Boolean,
    onAtualizar: () -> Unit,
    modifier: Modifier = Modifier,
    cor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    // Relê a hora a cada minuto: com a tela aberta na virada do dia, o texto passa a ter a data.
    val agora by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000)
            value = System.currentTimeMillis()
        }
    }
    Row(modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            RegrasCache.textoAtualizacao(atualizadoEm, agora),
            style = MaterialTheme.typography.labelMedium,
            color = cor,
        )
        if (atualizando) {
            // Mesma largura do botão, para o texto não pular.
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(16.dp), color = cor, strokeWidth = 2.dp)
            }
        } else {
            IconButton(onClick = onAtualizar) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Atualizar", tint = cor)
            }
        }
    }
}

// Aviso quando a busca falha. Com números na tela, diz que eles são os salvos no aparelho;
// sem nada salvo, explica o que falta. Sempre com Tentar de novo, que fica desabilitado e
// com spinner enquanto busca (especificação, "Feedback visual").
@Composable
fun AvisoPainel(
    falha: FalhaPainel,
    temDadosSalvos: Boolean,
    onTentarDeNovo: () -> Unit,
    modifier: Modifier = Modifier,
    tentando: Boolean = false,
    fundo: Color = MaterialTheme.colorScheme.surfaceVariant,
    conteudo: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    corErro: Color = MaterialTheme.colorScheme.error,
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = fundo)) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (falha.semConexao) Icons.Rounded.CloudOff else Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = if (falha.semConexao) conteudo else corErro,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                RegrasCache.textoAviso(falha.semConexao, falha.mensagem, temDadosSalvos),
                style = MaterialTheme.typography.bodyMedium,
                color = conteudo,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp),
            )
            TextButton(onClick = onTentarDeNovo, enabled = !tentando) {
                if (tentando) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Tentar de novo")
            }
        }
    }
}

// Gráfico ou lista sem dados no período: a frase diz o que fazer, nunca uma área em branco.
@Composable
fun EstadoVazioPainel(
    icone: ImageVector,
    texto: String,
    modifier: Modifier = Modifier,
    acao: String? = null,
    onAcao: () -> Unit = {},
    cor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icone, contentDescription = null, tint = cor, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            texto,
            style = MaterialTheme.typography.bodyMedium,
            color = cor,
            textAlign = TextAlign.Center,
        )
        if (acao != null) {
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onAcao) { Text(acao) }
        }
    }
}
