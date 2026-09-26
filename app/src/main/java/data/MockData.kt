package com.fleetflow.mobile.data

data class MovimentacaoMensal(
    val mesAbreviado: String,
    val receita: Double,
    val despesa: Double
)

object MockDataService {
    fun obterMovimentacoesMensais(): List<MovimentacaoMensal> = listOf(
        MovimentacaoMensal("Abr", 28500.0, 24100.0),
        MovimentacaoMensal("Mai", 31200.0, 26800.0),
        MovimentacaoMensal("Jun", 27900.0, 25300.0),
        MovimentacaoMensal("Jul", 33400.0, 27600.0),
        MovimentacaoMensal("Ago", 35100.0, 29200.0),
        MovimentacaoMensal("Set", 32800.0, 28450.0)
    )
}
