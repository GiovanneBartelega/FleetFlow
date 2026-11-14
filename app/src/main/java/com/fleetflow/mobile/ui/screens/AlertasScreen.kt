package com.fleetflow.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fleetflow.mobile.data.repository.AlertaFrota
import com.fleetflow.mobile.data.repository.FrotaRepository
import com.fleetflow.mobile.data.repository.GravidadeAlerta
import com.fleetflow.mobile.data.repository.RegrasAlertas
import com.fleetflow.mobile.ui.theme.AmbarAlerta
import com.fleetflow.mobile.ui.theme.BackgroundTela
import com.fleetflow.mobile.ui.theme.FundoInsight
import com.fleetflow.mobile.ui.theme.TextoPrincipal
import com.fleetflow.mobile.ui.theme.TextoSecundario
import com.fleetflow.mobile.ui.theme.Vermelho
import com.fleetflow.mobile.ui.theme.VermelhoSuave

@Composable
fun AlertasScreen() {
    // Recalcula sozinho quando a lista de veículos muda.
    val alertas by remember {
        derivedStateOf { RegrasAlertas.gerar(FrotaRepository.veiculos.toList()) }
    }

    // Erro da API (sessão expirada, por exemplo) não derruba o app: fica a cópia salva.
    LaunchedEffect(Unit) { runCatching { FrotaRepository.carregar() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundTela)
            .statusBarsPadding()
            .padding(16.dp),
    ) {
        Text(
            "Alertas da frota",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextoPrincipal,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Documentos e manutenções que pedem atenção.",
            fontSize = 14.sp,
            color = TextoSecundario,
        )
        if (FrotaRepository.semConexao) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Sem conexão: mostrando os últimos dados salvos.",
                fontSize = 13.sp,
                color = AmbarAlerta,
            )
        }
        Spacer(Modifier.height(16.dp))

        when {
            FrotaRepository.carregando && alertas.isEmpty() ->
                CircularProgressIndicator(color = AmbarAlerta)

            alertas.isEmpty() ->
                Text("Nenhum alerta no momento.", fontSize = 15.sp, color = TextoSecundario)

            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(alertas) { alerta -> CartaoAlerta(alerta) }
            }
        }
    }
}

@Composable
private fun CartaoAlerta(alerta: AlertaFrota) {
    val vencido = alerta.gravidade == GravidadeAlerta.VENCIDO
    val cor = if (vencido) Vermelho else AmbarAlerta
    val fundo = if (vencido) VermelhoSuave else FundoInsight

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = fundo),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = cor)
            Column {
                Text(alerta.titulo, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextoPrincipal)
                Text("${alerta.placa} — ${alerta.detalhe}", fontSize = 14.sp, color = TextoSecundario)
            }
        }
    }
}
