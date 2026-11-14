package com.fleetflow.mobile

import com.fleetflow.mobile.data.model.VeiculoDto
import com.fleetflow.mobile.data.repository.GravidadeAlerta
import com.fleetflow.mobile.data.repository.RegrasAlertas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RegrasAlertasTest {

    private val hoje = LocalDate.of(2026, 11, 4)

    private fun veiculo(
        hodometroAtual: Int = 10000,
        intervaloKm: Int? = null,
        ultimaManutencao: Int? = null,
        licenciamento: String? = null,
        seguro: String? = null,
    ) = VeiculoDto(
        id = "v1", placa = "ABC1D23", marca = "Volvo", modelo = "FH",
        anoFabricacao = 2020, capacidadeCargaToneladas = "20", status = "Disponivel",
        hodometroAtual = hodometroAtual,
        intervaloManutencaoKm = intervaloKm,
        hodometroUltimaManutencao = ultimaManutencao,
        vencimentoLicenciamento = licenciamento,
        vencimentoSeguro = seguro,
        syncVersion = 1,
    )

    @Test fun diasAte_dataVazia_retornaNull() {
        assertNull(RegrasAlertas.diasAte(null, hoje))
        assertNull(RegrasAlertas.diasAte("", hoje))
    }

    @Test fun diasAte_dataInvalida_retornaNull() {
        assertNull(RegrasAlertas.diasAte("abc", hoje))
    }

    @Test fun diasAte_aceitaDataComHora() {
        assertEquals(10L, RegrasAlertas.diasAte("2026-11-14T00:00:00Z", hoje))
    }

    @Test fun licenciamentoVencido_geraAlertaVencido() {
        val alertas = RegrasAlertas.gerar(listOf(veiculo(licenciamento = "2026-11-01")), hoje)
        assertEquals(1, alertas.size)
        assertEquals("Licenciamento vencido", alertas[0].titulo)
        assertEquals(GravidadeAlerta.VENCIDO, alertas[0].gravidade)
    }

    @Test fun seguroVencendoEm10Dias_geraAtencao() {
        val alertas = RegrasAlertas.gerar(listOf(veiculo(seguro = "2026-11-14")), hoje)
        assertEquals(1, alertas.size)
        assertEquals("Seguro vence em breve", alertas[0].titulo)
        assertEquals(GravidadeAlerta.ATENCAO, alertas[0].gravidade)
    }

    @Test fun documentoComMuitoPrazo_naoGeraAlerta() {
        val alertas = RegrasAlertas.gerar(listOf(veiculo(licenciamento = "2027-06-01")), hoje)
        assertEquals(0, alertas.size)
    }

    @Test fun documentoVenceHoje_ehAtencao() {
        val alertas = RegrasAlertas.gerar(listOf(veiculo(seguro = "2026-11-04")), hoje)
        assertEquals(GravidadeAlerta.ATENCAO, alertas[0].gravidade)
    }

    @Test fun kmParaManutencao_semDados_retornaNull() {
        assertNull(RegrasAlertas.kmParaManutencao(veiculo()))
        assertNull(RegrasAlertas.kmParaManutencao(veiculo(intervaloKm = 10000)))
    }

    @Test fun manutencaoAtrasada_geraVencido() {
        // intervalo 10000, última em 0, atual 10500 -> passou 500 km
        val v = veiculo(hodometroAtual = 10500, intervaloKm = 10000, ultimaManutencao = 0)
        val alertas = RegrasAlertas.gerar(listOf(v), hoje)
        assertEquals("Manutenção atrasada", alertas[0].titulo)
        assertEquals(GravidadeAlerta.VENCIDO, alertas[0].gravidade)
    }

    @Test fun manutencaoProxima_geraAtencao() {
        // faltam 300 km
        val v = veiculo(hodometroAtual = 9700, intervaloKm = 10000, ultimaManutencao = 0)
        val alertas = RegrasAlertas.gerar(listOf(v), hoje)
        assertEquals("Manutenção próxima", alertas[0].titulo)
        assertEquals(GravidadeAlerta.ATENCAO, alertas[0].gravidade)
    }

    @Test fun manutencaoLonge_naoGeraAlerta() {
        val v = veiculo(hodometroAtual = 2000, intervaloKm = 10000, ultimaManutencao = 0)
        assertEquals(0, RegrasAlertas.gerar(listOf(v), hoje).size)
    }

    @Test fun vencidosVemPrimeiro() {
        val v = veiculo(seguro = "2026-11-14", licenciamento = "2026-10-01")
        val alertas = RegrasAlertas.gerar(listOf(v), hoje)
        assertEquals(GravidadeAlerta.VENCIDO, alertas[0].gravidade)
        assertEquals(GravidadeAlerta.ATENCAO, alertas[1].gravidade)
    }
}
