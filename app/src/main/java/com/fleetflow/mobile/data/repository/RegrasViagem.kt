package com.fleetflow.mobile.data.repository

object RegrasViagem {
    fun ultimoHodometro(hodometroVeiculo: Int, registros: List<Int>): Int =
        (registros + hodometroVeiculo).maxOrNull() ?: hodometroVeiculo

    fun erroHodometroInicial(valor: Int?, ultimo: Int): String? = when {
        valor == null -> "Informe o hodômetro inicial"
        valor < ultimo -> "O hodômetro não pode ser menor que o último registrado ($ultimo km)"
        else -> null
    }

    fun erroHodometroFinal(inicial: Int, final: Int?): String? = when {
        final == null -> "Informe o hodômetro final"
        final < inicial -> "O hodômetro final não pode ser menor que o inicial"
        else -> null
    }

    fun kmRodados(inicial: Int, final: Int): Int = final - inicial
}
