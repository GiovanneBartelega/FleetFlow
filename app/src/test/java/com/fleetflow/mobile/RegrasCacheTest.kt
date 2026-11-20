package com.fleetflow.mobile

import com.fleetflow.mobile.data.repository.RegrasCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

// Regras do cache local dos painéis (especificação, Módulo 5, "Desempenho").
class RegrasCacheTest {

    private val saoPaulo = ZoneId.of("America/Sao_Paulo")
    private fun hora(texto: String) = ZonedDateTime.parse(texto).toInstant().toEpochMilli()

    private val salvo = hora("2026-11-18T18:42:00-03:00")
    private val minuto = 60 * 1000L

    // --- Quando buscar na API ---------------------------------------------------------------

    @Test
    fun semNadaSalvoBusca() {
        assertTrue(RegrasCache.precisaBuscar(salvoEm = null, agora = salvo, forcar = false))
    }

    @Test
    fun dentroDosCincoMinutosUsaOSalvo() {
        assertFalse(RegrasCache.precisaBuscar(salvo, salvo, forcar = false))
        assertFalse(RegrasCache.precisaBuscar(salvo, salvo + 4 * minuto, forcar = false))
    }

    @Test
    fun aPartirDosCincoMinutosBuscaDeNovo() {
        assertTrue(RegrasCache.precisaBuscar(salvo, salvo + 5 * minuto, forcar = false))
        assertTrue(RegrasCache.precisaBuscar(salvo, salvo + 3 * 60 * minuto, forcar = false))
    }

    @Test
    fun botaoAtualizarSempreBusca() {
        assertTrue(RegrasCache.precisaBuscar(salvo, salvo, forcar = true))
    }

    @Test
    fun relogioDoAparelhoVoltouBusca() {
        // Salvo "no futuro": o relógio foi acertado para trás. Não dá para confiar na idade.
        assertTrue(RegrasCache.precisaBuscar(salvo, salvo - 10 * minuto, forcar = false))
    }

    // --- Chave do painel ----------------------------------------------------------------------

    @Test
    fun semFiltroAChaveEOPainel() {
        assertEquals("movimentacoes/saldo", RegrasCache.chave("movimentacoes/saldo"))
    }

    @Test
    fun filtrosEmOrdemAlfabetica() {
        assertEquals(
            "bi/kpis?fim=2026-11-30&inicio=2026-11-01",
            RegrasCache.chave("bi/kpis", mapOf("inicio" to "2026-11-01", "fim" to "2026-11-30")),
        )
    }

    @Test
    fun mesmoPeriodoEmOutraOrdemCaiNaMesmaChave() {
        val a = RegrasCache.chave("bi/ranking-rotas", mapOf("inicio" to "2026-01-01", "fim" to "2026-12-31", "limite" to "3"))
        val b = RegrasCache.chave("bi/ranking-rotas", mapOf("limite" to "3", "fim" to "2026-12-31", "inicio" to "2026-01-01"))
        assertEquals(a, b)
    }

    @Test
    fun filtroVazioNaoEntraNaChave() {
        assertEquals(
            "bi/fluxo-caixa?meses=6",
            RegrasCache.chave("bi/fluxo-caixa", mapOf("meses" to "6", "regime" to null, "veiculoId" to " ")),
        )
    }

    // --- Carimbo "atualizado às HH:mm" --------------------------------------------------------

    @Test
    fun mesmoDiaMostraSoAHora() {
        assertEquals(
            "Atualizado às 18:42",
            RegrasCache.textoAtualizacao(salvo, hora("2026-11-18T21:05:00-03:00"), saoPaulo),
        )
    }

    @Test
    fun outroDiaMostraADataTambem() {
        assertEquals(
            "Atualizado em 18/11 às 18:42",
            RegrasCache.textoAtualizacao(salvo, hora("2026-11-19T08:00:00-03:00"), saoPaulo),
        )
    }

    @Test
    fun outroAnoMostraOAno() {
        assertEquals(
            "Atualizado em 31/12/2025 às 23:59",
            RegrasCache.textoAtualizacao(hora("2025-12-31T23:59:00-03:00"), hora("2026-01-02T10:00:00-03:00"), saoPaulo),
        )
    }

    @Test
    fun oDiaEODoFusoDoAparelhoNaoODoServidor() {
        // 23:30 em São Paulo já é dia 19 em UTC; para quem está no aparelho, ainda é hoje.
        val tarde = hora("2026-11-18T23:30:00-03:00")
        assertEquals(
            "Atualizado às 23:30",
            RegrasCache.textoAtualizacao(tarde, hora("2026-11-18T23:50:00-03:00"), saoPaulo),
        )
    }

    // --- Aviso quando a busca falha -----------------------------------------------------------

    @Test
    fun semConexaoComNumerosSalvos() {
        assertEquals(
            "Sem conexão. Mostrando os números salvos no aparelho.",
            RegrasCache.textoAviso(semConexao = true, mensagem = "Sem conexão com o servidor.", temDadosSalvos = true),
        )
    }

    @Test
    fun semConexaoSemNadaSalvo() {
        assertEquals(
            "Sem conexão com o servidor. Os números aparecem quando a conexão voltar.",
            RegrasCache.textoAviso(semConexao = true, mensagem = "Sem conexão com o servidor.", temDadosSalvos = false),
        )
    }

    @Test
    fun erroDaApiEntraNoAviso() {
        assertEquals(
            "Não foi possível atualizar (Acesso negado). Mostrando os números salvos.",
            RegrasCache.textoAviso(semConexao = false, mensagem = "Acesso negado.", temDadosSalvos = true),
        )
        assertEquals(
            "Não foi possível carregar os números (Erro 500).",
            RegrasCache.textoAviso(semConexao = false, mensagem = "Erro 500", temDadosSalvos = false),
        )
    }

    @Test
    fun mensagemVaziaNaoDeixaParentesesVazios() {
        assertEquals(
            "Não foi possível carregar os números (erro desconhecido).",
            RegrasCache.textoAviso(semConexao = false, mensagem = "  ", temDadosSalvos = false),
        )
    }
}
