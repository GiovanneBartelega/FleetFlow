package com.fleetflow.mobile.data.repository

// Regras do hodômetro (especificação, "Validações e mensagens"). Funções puras, sem
// Android, para o teste unitário rodar sem emulador.
object RegrasViagem {

    // Acima disso o app pede confirmação, mas não bloqueia.
    const val LIMITE_KM_ALTO = 3000

    fun erroHodometroInicial(informado: Int?, ultimoDoVeiculo: Int): String? = when {
        informado == null -> "Campo obrigatório"
        informado < ultimoDoVeiculo -> "O hodômetro informado é menor que o último registrado"
        else -> null
    }

    fun erroHodometroFinal(inicial: Int, hodometroFinal: Int?): String? = when {
        hodometroFinal == null -> "Campo obrigatório"
        hodometroFinal <= inicial -> "O hodômetro final precisa ser maior que o inicial"
        else -> null
    }

    // O maior hodômetro conhecido do veículo: o da frota ou o de uma viagem do aparelho que
    // a frota ainda não recebeu (fechada sem internet, por exemplo).
    fun ultimoHodometro(daFrota: Int, dasViagens: List<Int>): Int = maxOf(daFrota, dasViagens.maxOrNull() ?: daFrota)

    fun kmRodados(inicial: Int, hodometroFinal: Int): Int = hodometroFinal - inicial

    fun quilometragemAlta(inicial: Int, hodometroFinal: Int): Boolean =
        kmRodados(inicial, hodometroFinal) > LIMITE_KM_ALTO
}
