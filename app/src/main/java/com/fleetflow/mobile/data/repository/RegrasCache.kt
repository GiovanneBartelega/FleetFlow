package com.fleetflow.mobile.data.repository

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Regras do cache local dos painéis (especificação, Módulo 5, "Desempenho": os resultados
// ficam em cache local com carimbo de hora, exibido como "atualizado às HH:mm"). Sem
// Android, para o teste unitário cobrir cada caso.
object RegrasCache {

    // Dentro desse tempo, voltar para a tela (trocar de aba, girar o celular) mostra o que
    // está salvo sem chamar a API de novo. O botão Atualizar sempre busca.
    const val VALIDADE_MS = 5 * 60 * 1000L

    // Painel que ninguém abre há 30 dias sai do aparelho.
    const val DESCARTE_MS = 30L * 24 * 60 * 60 * 1000

    // Busca na API se não há nada salvo, se o salvo passou da validade, se a pessoa pediu
    // ou se o relógio do aparelho voltou para antes do salvo.
    fun precisaBuscar(salvoEm: Long?, agora: Long, forcar: Boolean): Boolean =
        forcar || salvoEm == null || salvoEm > agora || agora - salvoEm >= VALIDADE_MS

    // Chave do painel: o nome mais os filtros em ordem alfabética, sem os vazios. O mesmo
    // período cai sempre na mesma linha do cache, em qualquer ordem que a tela passe.
    // Ex.: chave("bi/kpis", mapOf("inicio" to "2026-11-01", "fim" to "2026-11-30"))
    //      = "bi/kpis?fim=2026-11-30&inicio=2026-11-01"
    fun chave(painel: String, filtros: Map<String, String?> = emptyMap()): String {
        val partes = filtros
            .filterValues { !it.isNullOrBlank() }
            .toSortedMap()
            .map { (nome, valor) -> "$nome=$valor" }
        return if (partes.isEmpty()) painel else "$painel?${partes.joinToString("&")}"
    }

    // "Atualizado às 18:42" no mesmo dia; "Atualizado em 14/11 às 18:42" em outro dia do
    // ano; com o ano quando for de outro ano.
    fun textoAtualizacao(salvoEm: Long, agora: Long, zona: ZoneId = ZoneId.systemDefault()): String {
        val salvo = Instant.ofEpochMilli(salvoEm).atZone(zona)
        val hoje = Instant.ofEpochMilli(agora).atZone(zona).toLocalDate()
        val hora = salvo.format(HORA)
        return when {
            salvo.toLocalDate() == hoje -> "Atualizado às $hora"
            salvo.year == hoje.year -> "Atualizado em ${salvo.format(DIA)} às $hora"
            else -> "Atualizado em ${salvo.format(DIA_ANO)} às $hora"
        }
    }

    // Frase do aviso quando a busca falha. Com números na tela, deixa claro que são os salvos;
    // sem nada salvo, diz o que falta. A mensagem da API entra entre parênteses.
    fun textoAviso(semConexao: Boolean, mensagem: String, temDadosSalvos: Boolean): String {
        val motivo = mensagem.trim().trimEnd('.', '!', ' ').ifBlank { "erro desconhecido" }
        return when {
            semConexao && temDadosSalvos -> "Sem conexão. Mostrando os números salvos no aparelho."
            semConexao -> "Sem conexão com o servidor. Os números aparecem quando a conexão voltar."
            temDadosSalvos -> "Não foi possível atualizar ($motivo). Mostrando os números salvos."
            else -> "Não foi possível carregar os números ($motivo)."
        }
    }

    private val HORA = DateTimeFormatter.ofPattern("HH:mm")
    private val DIA = DateTimeFormatter.ofPattern("dd/MM")
    private val DIA_ANO = DateTimeFormatter.ofPattern("dd/MM/yyyy")
}
