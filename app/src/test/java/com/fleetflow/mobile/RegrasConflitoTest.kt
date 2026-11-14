package com.fleetflow.mobile

import com.fleetflow.mobile.data.local.VerboSync
import com.fleetflow.mobile.data.repository.RegrasConflito
import com.fleetflow.mobile.data.repository.RegrasConflito.FechamentoNoAparelho
import com.fleetflow.mobile.data.repository.RegrasConflito.Lado
import com.fleetflow.mobile.data.repository.RegrasConflito.Resolucao
import com.fleetflow.mobile.data.repository.RegrasConflito.Tipo
import com.fleetflow.mobile.data.repository.RegrasConflito.ViagemNoServidor
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

// Tabela "Conflitos" da especificação, uma linha por teste.
class RegrasConflitoTest {

    private val dezHoras = Instant.parse("2026-11-11T10:00:00Z").toEpochMilli()
    private val dezEMeia = "2026-11-11T10:30:00.000Z"
    private val noveEMeia = "2026-11-11T09:30:00.000Z"

    // --- Vence a alteração com AtualizadoEm mais recente --------------------------------

    @Test
    fun alteracaoMaisRecenteVence() {
        assertEquals(Lado.SERVIDOR, RegrasConflito.maisRecente(dezHoras, dezEMeia))
        assertEquals(Lado.APARELHO, RegrasConflito.maisRecente(dezHoras, noveEMeia))
    }

    @Test
    fun empateFicaComOServidor() {
        assertEquals(Lado.SERVIDOR, RegrasConflito.maisRecente(dezHoras, "2026-11-11T10:00:00.000Z"))
    }

    @Test
    fun semDataDoServidorValeOAparelho() {
        assertEquals(Lado.APARELHO, RegrasConflito.maisRecente(dezHoras, null))
        assertEquals(Lado.APARELHO, RegrasConflito.maisRecente(dezHoras, "ontem"))
    }

    @Test
    fun fechamentoMaisAntigoQueODoServidorPerde() {
        val r = RegrasConflito.resolverFechamento(
            FechamentoNoAparelho(hodometroFinal = 120_450, alteradoEm = dezHoras),
            ViagemNoServidor(status = "Encerrada", hodometroFinal = 120_500, atualizadoEm = dezEMeia),
        )
        assertEquals(Resolucao.ACEITAR_SERVIDOR, r)
    }

    @Test
    fun fechamentoMaisNovoQueODoServidorFicaParaRevisao() {
        // A API não reabre viagem encerrada: o aparelho ganha, mas o gestor precisa revisar.
        val r = RegrasConflito.resolverFechamento(
            FechamentoNoAparelho(hodometroFinal = 120_450, alteradoEm = dezHoras),
            ViagemNoServidor(status = "Encerrada", hodometroFinal = 120_500, atualizadoEm = noveEMeia),
        )
        assertEquals(Resolucao.AGUARDAR_REVISAO, r)
    }

    @Test
    fun mesmoFechamentoJaNoServidorNaoEConflito() {
        // A resposta do primeiro envio se perdeu e o aparelho reenviou.
        val r = RegrasConflito.resolverFechamento(
            FechamentoNoAparelho(hodometroFinal = 120_450, alteradoEm = dezHoras),
            ViagemNoServidor(status = "Encerrada", hodometroFinal = 120_450, atualizadoEm = dezEMeia),
        )
        assertEquals(Resolucao.JA_APLICADO, r)
    }

    @Test
    fun viagemAindaAbertaNoServidorReenviaComAVersaoNova() {
        // Mudou outra coisa na viagem; o fechamento não perde nada.
        val r = RegrasConflito.resolverFechamento(
            FechamentoNoAparelho(hodometroFinal = 120_450, alteradoEm = dezHoras),
            ViagemNoServidor(status = "Aberta", hodometroFinal = null, atualizadoEm = dezEMeia),
        )
        assertEquals(Resolucao.REENVIAR_COM_VERSAO_NOVA, r)
    }

    @Test
    fun versaoDesatualizadaNoFechamentoEConflito() {
        assertEquals(Tipo.VERSAO_DESATUALIZADA, RegrasConflito.classificar(VerboSync.FECHAR, 409, "VERSION_CONFLICT"))
        assertEquals(Tipo.JA_ENCERRADA, RegrasConflito.classificar(VerboSync.FECHAR, 409, "TRIP_ALREADY_CLOSED"))
        assertEquals(Tipo.JA_ENCERRADA, RegrasConflito.classificar(VerboSync.CHECKLIST, 409, "TRIP_CLOSED"))
    }

    // --- Viagem feita offline, veículo já em outra viagem no servidor -------------------

    @Test
    fun veiculoEmOutraViagemFicaSinalizado() {
        assertEquals(Tipo.VEICULO_OCUPADO, RegrasConflito.classificar(VerboSync.ABRIR, 409, "OPEN_TRIP_EXISTS"))
        assertEquals(Tipo.VEICULO_OCUPADO, RegrasConflito.classificar(VerboSync.ABRIR, 409, "VEHICLE_UNAVAILABLE"))
    }

    // --- Registro apagado no servidor e editado no celular ------------------------------

    @Test
    fun viagemApagadaNoServidorDescartaAEdicao() {
        assertEquals(Tipo.APAGADO_NO_SERVIDOR, RegrasConflito.classificar(VerboSync.FECHAR, 404, "NOT_FOUND"))
        assertEquals(
            Tipo.APAGADO_NO_SERVIDOR,
            RegrasConflito.classificar(VerboSync.CHECKLIST, 400, "INVALID_REFERENCE", campo = "viagemId"),
        )
        val r = RegrasConflito.resolverFechamento(FechamentoNoAparelho(120_450, dezHoras), servidor = null)
        assertEquals(Resolucao.DESCARTAR_APAGADA, r)
    }

    @Test
    fun itemDeChecklistApagadoNaoEAViagemApagada() {
        assertEquals(
            Tipo.RECUSADA,
            RegrasConflito.classificar(VerboSync.CHECKLIST, 400, "INVALID_REFERENCE", campo = "itemChecklistId"),
        )
    }

    // --- Hodômetro inválido depois de sincronizar ---------------------------------------

    @Test
    fun hodometroRecusadoVaiParaPendencias() {
        assertEquals(Tipo.RECUSADA, RegrasConflito.classificar(VerboSync.FECHAR, 400, "INVALID_ODOMETER"))
        assertEquals(Tipo.RECUSADA, RegrasConflito.classificar(VerboSync.ABRIR, 400, "INVALID_ODOMETER"))
        assertEquals(Tipo.RECUSADA, RegrasConflito.classificar(VerboSync.FECHAR, 409, "CHECKLIST_INCOMPLETE"))
    }
}
