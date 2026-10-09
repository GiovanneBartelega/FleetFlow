package com.fleetflow.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fleetflow.mobile.data.MovimentacaoMensal
import com.fleetflow.mobile.ui.theme.*

@Composable
fun GraficoReceitasDespesas(dados: List<MovimentacaoMensal>) {
    // Se a lista estiver vazia, exibe um aviso amigável em vez de quebrar o app
    if (dados.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Nenhuma projeção pendente cadastrada.",
                color = TextoSecundario,
                fontSize = 12.sp
            )
        }
        return
    }

    // Encontra o maior valor para calcular a proporção das barras com segurança
    val maiorValor = dados.maxOfOrNull { maxOf(it.receita, it.despesa) } ?: 1.0
    val alturaMaximaBarra = 100.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.Bottom
    ) {
        dados.forEach { item ->
            val alturaReceita = if (maiorValor > 0) {
                (item.receita / maiorValor).toFloat() * alturaMaximaBarra.value
            } else 0f

            val alturaDespesa = if (maiorValor > 0) {
                (item.despesa / maiorValor).toFloat() * alturaMaximaBarra.value
            } else 0f

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.fillMaxHeight()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.height(alturaMaximaBarra)
                ) {
                    // Barra de Receita (Verde)
                    Box(
                        modifier = Modifier
                            .width(10.dp)
                            .height(alturaReceita.coerceAtLeast(4f).dp)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(VerdeEsmeralda)
                    )

                    // Barra de Despesa (Vermelho)
                    Box(
                        modifier = Modifier
                            .width(10.dp)
                            .height(alturaDespesa.coerceAtLeast(4f).dp)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(Vermelho)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = item.mesAbreviado,
                    color = TextoSecundario,
                    fontSize = 10.sp
                )
            }
        }
    }
}