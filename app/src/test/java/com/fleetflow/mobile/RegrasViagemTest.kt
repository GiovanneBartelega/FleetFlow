package com.fleetflow.mobile

import com.fleetflow.mobile.data.repository.RegrasViagem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Regras do hodômetro da abertura e do fechamento de viagem.
class RegrasViagemTest {

    @Test
    fun hodometroInicialMenorQueOUltimoBloqueia() {
        assertEquals(
            "O hodômetro informado é menor que o último registrado",
            RegrasViagem.erroHodometroInicial(119_999, 120_000),
        )
    }

    @Test
    fun hodometroInicialIgualOuMaiorQueOUltimoPassa() {
        assertNull(RegrasViagem.erroHodometroInicial(120_000, 120_000))
        assertNull(RegrasViagem.erroHodometroInicial(120_500, 120_000))
    }

    @Test
    fun hodometroVazioEhCampoObrigatorio() {
        assertEquals("Campo obrigatório", RegrasViagem.erroHodometroInicial(null, 120_000))
        assertEquals("Campo obrigatório", RegrasViagem.erroHodometroFinal(120_000, null))
    }

    @Test
    fun hodometroFinalMenorOuIgualAoInicialBloqueia() {
        val mensagem = "O hodômetro final precisa ser maior que o inicial"
        assertEquals(mensagem, RegrasViagem.erroHodometroFinal(120_000, 119_000))
        assertEquals(mensagem, RegrasViagem.erroHodometroFinal(120_000, 120_000))
        assertNull(RegrasViagem.erroHodometroFinal(120_000, 120_001))
    }

    @Test
    fun ultimoHodometroConsideraViagensQueAindaNaoSubiram() {
        assertEquals(120_000, RegrasViagem.ultimoHodometro(120_000, emptyList()))
        assertEquals(120_450, RegrasViagem.ultimoHodometro(120_000, listOf(119_000, 120_450)))
        assertEquals(120_000, RegrasViagem.ultimoHodometro(120_000, listOf(110_000)))
    }

    @Test
    fun kmRodadosEhADiferenca() {
        assertEquals(450, RegrasViagem.kmRodados(120_000, 120_450))
    }

    @Test
    fun acimaDe3000KmPedeConfirmacao() {
        assertFalse(RegrasViagem.quilometragemAlta(120_000, 123_000))
        assertTrue(RegrasViagem.quilometragemAlta(120_000, 123_001))
    }
}
