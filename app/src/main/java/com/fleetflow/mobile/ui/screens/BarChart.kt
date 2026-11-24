package com.fleetflow.mobile.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.fleetflow.mobile.data.MovimentacaoMensal
import com.fleetflow.mobile.ui.theme.VerdeEsmeralda
import com.fleetflow.mobile.ui.theme.Vermelho
import kotlin.math.max

@Composable
fun GraficoReceitasDespesas(dados: List<MovimentacaoMensal>) {
    if (dados.isEmpty()) return

    val maiorValor = dados.maxOf {
        max(it.receita, it.despesa)
    }.coerceAtLeast(1.0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            val margemSuperior = 12.dp.toPx()
            val margemInferior = 8.dp.toPx()
            val alturaUtil = size.height - margemSuperior - margemInferior

            val larguraGrupo = size.width / dados.size
            val larguraBarra = (larguraGrupo * 0.25f).coerceAtMost(22.dp.toPx())
            val espacamentoBarras = 5.dp.toPx()
            val alturaMaxima = alturaUtil * 0.88f

            // Linhas horizontais de referência
            for (i in 0..3) {
                val y = margemSuperior + alturaMaxima * i / 3f

                drawLine(
                    color = Color.LightGray.copy(alpha = 0.45f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            dados.forEachIndexed { index, item ->
                val centroGrupo = larguraGrupo * index + larguraGrupo / 2f

                val alturaReceita =
                    (item.receita / maiorValor * alturaMaxima).toFloat()

                val alturaDespesa =
                    (item.despesa / maiorValor * alturaMaxima).toFloat()

                val larguraTotal = larguraBarra * 2 + espacamentoBarras
                val inicioGrupo = centroGrupo - larguraTotal / 2f

                // Barra de receitas
                drawRect(
                    color = VerdeEsmeralda,
                    topLeft = Offset(
                        x = inicioGrupo,
                        y = margemSuperior + alturaMaxima - alturaReceita
                    ),
                    size = Size(
                        larguraBarra,
                        alturaReceita
                    )
                )

                // Barra de despesas
                drawRect(
                    color = Vermelho,
                    topLeft = Offset(
                        x = inicioGrupo + larguraBarra + espacamentoBarras,
                        y = margemSuperior + alturaMaxima - alturaDespesa
                    ),
                    size = Size(
                        larguraBarra,
                        alturaDespesa
                    )
                )
            }
        }

        // Identificação dos períodos
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            dados.forEach { item ->
                Text(
                    text = item.mesAbreviado,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp,
                    color = Color.Gray,
                    maxLines = 1
                )
            }
        }
    }
}
