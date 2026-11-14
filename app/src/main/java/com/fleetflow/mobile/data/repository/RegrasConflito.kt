package com.fleetflow.mobile.data.repository

import com.fleetflow.mobile.data.local.StatusViagem
import com.fleetflow.mobile.data.local.VerboSync
import java.time.Instant

// Resolução de conflito da fila de sincronização (especificação, "Conflitos"). Aqui ficam
// só as decisões, sem Android nem rede, para o teste unitário cobrir cada linha da tabela.
// Quem executa a decisão (apagar, adotar a versão do servidor, avisar) é o ViagemRepository.
//
// | Situação                                                 | O que o app faz                                         |
// |----------------------------------------------------------|---------------------------------------------------------|
// | Mesmo registro alterado nos dois lados                   | Vence a alteração com AtualizadoEm mais recente         |
// | Viagem feita offline, veículo em outra viagem no servidor| Fica sinalizada como conflito e sobe quando o servidor   |
// |                                                          | liberar o veículo (o gestor revisa a outra viagem)      |
// | Registro apagado no servidor e editado no celular        | A edição é descartada e o usuário é avisado             |
// | Hodômetro inválido depois de sincronizar                 | A API recusa e o item fica na tela de pendências         |
object RegrasConflito {

    enum class Lado { APARELHO, SERVIDOR }

    // Vence a alteração mais recente. Empate fica com o servidor, que já está gravado.
    // Sem a data do servidor (resposta antiga ou ilegível), vale o aparelho, como antes.
    fun maisRecente(alteradoNoAparelhoEm: Long, atualizadoNoServidorEm: String?): Lado {
        val servidor = atualizadoNoServidorEm
            ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            ?: return Lado.APARELHO
        return if (alteradoNoAparelhoEm > servidor) Lado.APARELHO else Lado.SERVIDOR
    }

    // Primeiro passo: o que a recusa da API significa, pelo verbo da fila e pelo código do erro.
    enum class Tipo {
        APAGADO_NO_SERVIDOR,   // a viagem não existe mais na API
        VEICULO_OCUPADO,       // abertura feita offline, mas o veículo já está em outra viagem
        JA_ENCERRADA,          // a viagem foi encerrada no servidor por outra pessoa
        VERSAO_DESATUALIZADA,  // a viagem mudou no servidor depois que o aparelho a recebeu
        RECUSADA,              // erro comum (hodômetro, checklist incompleto...): vai para pendências
    }

    // `campo` é o primeiro campo de details do erro da API (por exemplo, "viagemId").
    fun classificar(verbo: String, status: Int, codigo: String?, campo: String? = null): Tipo = when {
        verbo == VerboSync.FECHAR && status == 404 -> Tipo.APAGADO_NO_SERVIDOR
        verbo == VerboSync.CHECKLIST && codigo == "INVALID_REFERENCE" && campo == "viagemId" -> Tipo.APAGADO_NO_SERVIDOR
        verbo == VerboSync.ABRIR && (codigo == "OPEN_TRIP_EXISTS" || codigo == "VEHICLE_UNAVAILABLE") -> Tipo.VEICULO_OCUPADO
        verbo == VerboSync.FECHAR && codigo == "TRIP_ALREADY_CLOSED" -> Tipo.JA_ENCERRADA
        verbo == VerboSync.CHECKLIST && codigo == "TRIP_CLOSED" -> Tipo.JA_ENCERRADA
        verbo == VerboSync.FECHAR && codigo == "VERSION_CONFLICT" -> Tipo.VERSAO_DESATUALIZADA
        else -> Tipo.RECUSADA
    }

    // Segundo passo, para o fechamento: compara o que o aparelho fechou com a viagem como
    // ela está agora no servidor.
    enum class Resolucao {
        JA_APLICADO,              // o servidor já tem este fechamento (resposta perdida no envio)
        REENVIAR_COM_VERSAO_NOVA, // a viagem segue aberta lá: os campos do fechamento não
                                  // mudaram, então não há conflito de verdade
        ACEITAR_SERVIDOR,         // o servidor encerrou depois: vale a versão dele, com aviso
        AGUARDAR_REVISAO,         // o aparelho encerrou depois, mas a API não reabre viagem
        DESCARTAR_APAGADA,        // a viagem não existe mais no servidor
    }

    data class FechamentoNoAparelho(val hodometroFinal: Int, val alteradoEm: Long)

    data class ViagemNoServidor(val status: String, val hodometroFinal: Int?, val atualizadoEm: String?)

    fun resolverFechamento(aparelho: FechamentoNoAparelho, servidor: ViagemNoServidor?): Resolucao = when {
        servidor == null -> Resolucao.DESCARTAR_APAGADA
        servidor.status != StatusViagem.ENCERRADA -> Resolucao.REENVIAR_COM_VERSAO_NOVA
        servidor.hodometroFinal == aparelho.hodometroFinal -> Resolucao.JA_APLICADO
        maisRecente(aparelho.alteradoEm, servidor.atualizadoEm) == Lado.SERVIDOR -> Resolucao.ACEITAR_SERVIDOR
        else -> Resolucao.AGUARDAR_REVISAO
    }
}
