package com.fleetflow.mobile.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fleetflow.mobile.data.MovimentacaoMensal
import com.fleetflow.mobile.ui.theme.VerdeEsmeralda
import com.fleetflow.mobile.ui.theme.Vermelho

@Composable
fun GraficoReceitasDespesas(dados: List<MovimentacaoMensal>) {
    val maiorValor = dados.maxOf { maxOf(it.receita, it.despesa) }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        val larguraGrupo = size.width / dados.size
        val larguraBarra = larguraGrupo * 0.28f
        val alturaMaxima = size.height * 0.85f

        dados.forEachIndexed { index, mes ->
            val xGrupo = larguraGrupo * index

            val alturaReceita = (mes.receita / maiorValor * alturaMaxima).toFloat()
            drawRect(
                color = VerdeEsmeralda,
                topLeft = androidx.compose.ui.geometry.Offset(
                    x = xGrupo + larguraGrupo * 0.15f,
                    y = size.height - alturaReceita
                ),
                size = androidx.compose.ui.geometry.Size(larguraBarra, alturaReceita)
            )

            val alturaDespesa = (mes.despesa / maiorValor * alturaMaxima).toFloat()
            drawRect(
                color = Vermelho,
                topLeft = androidx.compose.ui.geometry.Offset(
                    x = xGrupo + larguraGrupo * 0.15f + larguraBarra + 6f,
                    y = size.height - alturaDespesa
                ),
                size = androidx.compose.ui.geometry.Size(larguraBarra, alturaDespesa)
            )
        }
    }
}