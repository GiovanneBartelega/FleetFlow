package com.fleetflow.mobile

import com.fleetflow.mobile.data.local.CachePainelDao
import com.fleetflow.mobile.data.local.CachePainelEntity
import com.fleetflow.mobile.data.repository.ApiException
import com.fleetflow.mobile.data.repository.EstadoPainel
import com.fleetflow.mobile.data.repository.Painel
import com.fleetflow.mobile.data.repository.PainelRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant

// O painel com o cache local: o que a tela mostra em cada situação (entrega de 18/11).
// O Room é trocado por um mapa em memória; a API, por uma função que o teste controla.
class PainelTest {

    private class DaoEmMemoria : CachePainelDao() {
        val linhas = linkedMapOf<String, CachePainelEntity>()
        override suspend fun buscar(chave: String) = linhas[chave]
        override suspend fun salvar(linha: CachePainelEntity) { linhas[linha.chave] = linha }
        override suspend fun apagarAntigos(antes: Long) { linhas.values.removeAll { it.salvoEm < antes } }
        override suspend fun apagarDeOutrasContas(prefixo: String) { linhas.keys.removeAll { !it.startsWith(prefixo) } }
        override suspend fun limpar() = linhas.clear()
    }

    // Mesmo formato do SaldoDto da API, reduzido ao que o teste usa.
    data class Saldo(val saldoAtual: String, val aPagar: String)

    private val dao = DaoEmMemoria()
    private var agora = Instant.parse("2026-11-18T21:42:00Z").toEpochMilli()
    private val minuto = 60 * 1000L
    private var chamadasNaApi = 0

    @Before
    fun preparar() {
        PainelRepository.iniciarComDao(dao)
        PainelRepository.usarConta("admin-1")
    }

    private fun painel(api: () -> Saldo) = Painel(
        chave = "movimentacoes/saldo",
        tipo = Saldo::class.java,
        buscar = { chamadasNaApi++; api() },
        agora = { agora },
    )

    private fun dados(painel: Painel<Saldo>) = painel.estado as EstadoPainel.Dados<Saldo>

    @Test
    fun primeiraVezMostraSkeletonEDepoisOsNumeros() = runBlocking {
        val p = painel { Saldo("1500.00", "200.00") }
        assertEquals(EstadoPainel.Carregando, p.estado)

        p.carregar()

        val d = dados(p)
        assertEquals("1500.00", d.valor.saldoAtual)
        assertEquals(agora, d.atualizadoEm)
        assertNull(d.falha)
        assertFalse(d.atualizando)
        assertEquals(listOf("admin-1|movimentacoes/saldo"), dao.linhas.keys.toList())
    }

    @Test
    fun voltarParaAHomeLogoDepoisNaoChamaAApi() = runBlocking {
        painel { Saldo("1500.00", "200.00") }.carregar()
        agora += 4 * minuto

        val p = painel { Saldo("9999.00", "0.00") }
        p.carregar()

        assertEquals(1, chamadasNaApi)
        assertEquals("1500.00", dados(p).valor.saldoAtual)
    }

    @Test
    fun salvoVencidoAbreNaHoraEBuscaONovo() = runBlocking {
        painel { Saldo("1500.00", "200.00") }.carregar()
        agora += 6 * minuto

        val p = painel { Saldo("1800.00", "150.00") }
        p.carregar()

        assertEquals(2, chamadasNaApi)
        assertEquals("1800.00", dados(p).valor.saldoAtual)
        assertEquals(agora, dados(p).atualizadoEm)
    }

    @Test
    fun semConexaoFicamOsNumerosSalvosComAHoraDeles() = runBlocking {
        painel { Saldo("1500.00", "200.00") }.carregar()
        val salvoEm = agora
        agora += 30 * minuto

        val p = painel { throw IOException("Failed to connect to /10.0.2.2:3000") }
        p.carregar()

        val d = dados(p)
        assertEquals("1500.00", d.valor.saldoAtual)
        assertEquals(salvoEm, d.atualizadoEm)
        assertEquals(true, d.falha?.semConexao)
        // A mensagem técnica do OkHttp não chega à tela.
        assertEquals("Sem conexão com o servidor.", d.falha?.mensagem)
        assertFalse(d.atualizando)
    }

    @Test
    fun semConexaoENadaSalvoEErroComTentarDeNovo() = runBlocking {
        var online = false
        val p = painel { if (online) Saldo("1500.00", "200.00") else throw IOException("timeout") }

        p.carregar()
        val erro = p.estado as EstadoPainel.Erro
        assertTrue(erro.falha.semConexao)

        // Tentar de novo com a conexão de volta.
        online = true
        p.carregar(forcar = true)
        assertEquals("1500.00", dados(p).valor.saldoAtual)
    }

    @Test
    fun erroDaApiTrazAMensagemDela() = runBlocking {
        val p = painel { throw ApiException("FORBIDDEN", "Acesso negado.") }
        p.carregar()

        val erro = p.estado as EstadoPainel.Erro
        assertFalse(erro.falha.semConexao)
        assertEquals("Acesso negado.", erro.falha.mensagem)
    }

    @Test
    fun atualizarBuscaMesmoDentroDaValidade() = runBlocking {
        val p = painel { Saldo("1500.00", "${chamadasNaApi}.00") }
        p.carregar()
        agora += minuto

        p.carregar(forcar = true)

        assertEquals(2, chamadasNaApi)
        assertEquals("2.00", dados(p).valor.aPagar)
    }

    @Test
    fun falhaAoAtualizarMantemONumeroNaTela() = runBlocking {
        var online = true
        val p = painel { if (online) Saldo("1500.00", "200.00") else throw IOException("timeout") }
        p.carregar()

        online = false
        p.carregar(forcar = true)

        val d = dados(p)
        assertEquals("1500.00", d.valor.saldoAtual)
        assertTrue(d.falha!!.semConexao)
    }

    @Test
    fun outraContaNoMesmoAparelhoNaoVeOsNumeros() = runBlocking {
        painel { Saldo("1500.00", "200.00") }.carregar()

        // A sessão do administrador expirou e o financeiro entrou no mesmo celular, sem rede.
        PainelRepository.usarConta("financeiro-7")
        val semRede = painel { throw IOException("timeout") }
        semRede.carregar()
        assertTrue(semRede.estado is EstadoPainel.Erro)

        // Quando o financeiro grava o dele, o que sobrou da outra conta sai do aparelho.
        painel { Saldo("80.00", "10.00") }.carregar()
        assertEquals(listOf("financeiro-7|movimentacoes/saldo"), dao.linhas.keys.toList())
    }

    @Test
    fun sairDaContaApagaOsNumerosENaoGravaMais() = runBlocking {
        painel { Saldo("1500.00", "200.00") }.carregar()

        PainelRepository.limpar()
        assertTrue(dao.linhas.isEmpty())

        // Uma busca que termina depois do logout não deixa linha para trás.
        painel { Saldo("1500.00", "200.00") }.carregar()
        assertTrue(dao.linhas.isEmpty())
    }

    @Test
    fun jsonQueNaoAbreEIgnorado() = runBlocking {
        dao.linhas["admin-1|movimentacoes/saldo"] = CachePainelEntity("admin-1|movimentacoes/saldo", "{nao e json", agora)

        val p = painel { throw IOException("timeout") }
        p.carregar()

        assertTrue(p.estado is EstadoPainel.Erro)
    }

    @Test
    fun painelSemAbrirHaTrintaDiasSaiDoAparelho() = runBlocking {
        val antigo = agora - 31L * 24 * 60 * minuto
        dao.linhas["admin-1|bi/kpis?mes=2026-10"] = CachePainelEntity("admin-1|bi/kpis?mes=2026-10", "{}", antigo)

        painel { Saldo("1500.00", "200.00") }.carregar()

        assertEquals(listOf("admin-1|movimentacoes/saldo"), dao.linhas.keys.toList())
    }
}
