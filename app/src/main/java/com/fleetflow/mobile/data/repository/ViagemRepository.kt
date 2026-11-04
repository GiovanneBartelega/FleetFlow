package com.fleetflow.mobile.data.repository

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.room.withTransaction
import com.fleetflow.mobile.data.api.ApiClient
import com.fleetflow.mobile.data.api.ViagemApi
import com.fleetflow.mobile.data.local.AppDatabase
import com.fleetflow.mobile.data.local.EntidadeSync
import com.fleetflow.mobile.data.local.FilaSyncDao
import com.fleetflow.mobile.data.local.ItemChecklistEntity
import com.fleetflow.mobile.data.local.OperacaoSyncEntity
import com.fleetflow.mobile.data.local.RotaEntity
import com.fleetflow.mobile.data.local.SituacaoSync
import com.fleetflow.mobile.data.local.StatusViagem
import com.fleetflow.mobile.data.local.VerboSync
import com.fleetflow.mobile.data.local.ViagemDao
import com.fleetflow.mobile.data.local.ViagemEntity
import com.fleetflow.mobile.data.local.paraEntity
import com.fleetflow.mobile.data.model.AbrirViagemRequestDto
import com.fleetflow.mobile.data.model.AtualizarRespostaChecklistDto
import com.fleetflow.mobile.data.model.ChecklistPayload
import com.fleetflow.mobile.data.model.ErrorResponseDto
import com.fleetflow.mobile.data.model.FecharViagemRequestDto
import com.fleetflow.mobile.data.model.RespostaChecklistItem
import com.fleetflow.mobile.data.model.RespostaChecklistRequestDto
import com.fleetflow.mobile.data.model.RespostaChecklistServidorDto
import com.fleetflow.mobile.data.model.VeiculoDto
import com.fleetflow.mobile.data.model.ViagemDto
import com.fleetflow.mobile.data.sync.SyncScheduler
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.Response
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

// Erro que a tela mostra ao usuário: dado inválido antes de gravar.
class ViagemValidacaoException(message: String) : Exception(message)

class ViagemApiException(message: String) : Exception(message)

enum class ResultadoEnvio { CONCLUIDO, TENTAR_DE_NOVO }

// Viagens offline-first (especificação, "Sincronização offline-first").
// Escrita: tela -> Room + linha na fila, na mesma transação -> SyncWorker -> API.
// Leitura: API -> Room -> tela. A tela observa o Room e nunca espera a rede.
object ViagemRepository {
    private lateinit var appContext: Context
    private lateinit var db: AppDatabase
    private lateinit var dao: ViagemDao
    private lateinit var fila: FilaSyncDao
    private lateinit var api: ViagemApi
    private val gson = Gson()

    // Uma passada pela fila de cada vez, mesmo que dois envios disparem juntos.
    private val envio = Mutex()

    // Depois de tantas falhas de rede seguidas, a operação vira erro (especificação).
    private const val MAX_TENTATIVAS = 10

    var carregando by mutableStateOf(false); private set
    var semConexao by mutableStateOf(false); private set
    var erroCarga by mutableStateOf<String?>(null); private set

    // Pode ser chamado mais de uma vez: pelo MainActivity e pelo SyncWorker, que às vezes
    // roda com o app fechado.
    fun init(context: Context) {
        if (::db.isInitialized) return
        appContext = context.applicationContext
        db = AppDatabase.get(appContext)
        dao = db.viagemDao()
        fila = db.filaSyncDao()
        api = ApiClient.getRetrofit(appContext).create(ViagemApi::class.java)
    }

    fun observarViagens(): Flow<List<ViagemEntity>> = dao.observarViagens()
    fun observarRotas(): Flow<List<RotaEntity>> = dao.observarRotas()
    fun observarItensChecklist(): Flow<List<ItemChecklistEntity>> = dao.observarItensChecklist()
    fun observarPendentes(): Flow<Int> = fila.observarTotal()

    suspend fun buscar(id: String): ViagemEntity? = dao.buscar(id)

    fun sincronizarAgora() {
        if (::appContext.isInitialized) SyncScheduler.enviarAgora(appContext)
    }

    // ---------------------------------------------------------------
    // Descida: rotas, itens do checklist e viagens da API para o Room
    // ---------------------------------------------------------------
    suspend fun carregar() {
        carregando = true
        erroCarga = null
        try {
            val agora = System.currentTimeMillis()
            val rotas = verificar(api.rotas(mapOf("pageSize" to "100"))).data
            dao.substituirRotas(rotas.map { it.paraEntity(agora) })
            // O catálogo do checklist não é aberto a todos os perfis; sem ele, segue o resto.
            val itens = api.itensChecklist()
            if (itens.isSuccessful) {
                itens.body()?.data?.let { lista -> dao.substituirItensChecklist(lista.map { it.paraEntity(agora) }) }
            }
            // O motorista recebe só as próprias viagens: a API já filtra.
            val doServidor = verificar(api.viagens(mapOf("pageSize" to "100"))).data
            aplicarDoServidor(doServidor, agora)
            semConexao = false
        } catch (e: IOException) {
            // Sem conexão não é erro para a tela: ela mostra o que está no Room.
            semConexao = true
        } catch (e: ViagemApiException) {
            erroCarga = e.message
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Resposta fora do formato esperado: avisa sem derrubar o app.
            erroCarga = "Não foi possível ler as viagens que a API enviou"
        } finally {
            carregando = false
        }
        sincronizarAgora()
    }

    // Junta o que veio da API com o que está no aparelho. O que ainda não subiu (Pendente
    // ou Erro) vale a versão local; o resto passa a ser a versão do servidor.
    private suspend fun aplicarDoServidor(doServidor: List<ViagemDto>, agora: Long) {
        db.withTransaction {
            val locais = dao.listarViagens()
            val porIdServidor = locais.filter { it.idServidor != null }.associateBy { it.idServidor }
            val idsDoServidor: Set<String?> = doServidor.map { it.id }.toSet()
            val sumiram = locais.filter {
                it.situacaoSync == SituacaoSync.SINCRONIZADA && it.idServidor !in idsDoServidor
            }
            if (sumiram.isNotEmpty()) dao.apagarViagens(sumiram.map { it.id })
            val atualizadas = doServidor.mapNotNull { dto ->
                val local = porIdServidor[dto.id]
                when {
                    local == null -> dto.paraEntity(
                        idLocal = dto.id,
                        checklistRespondido = false,
                        checklistLiberado = false,
                        salvoEm = agora,
                    )
                    local.situacaoSync != SituacaoSync.SINCRONIZADA -> null
                    else -> dto.paraEntity(
                        idLocal = local.id,
                        checklistRespondido = local.checklistRespondido,
                        checklistLiberado = local.checklistLiberado,
                        salvoEm = agora,
                    )
                }
            }
            if (atualizadas.isNotEmpty()) dao.salvarViagens(atualizadas)
        }
    }

    // ---------------------------------------------------------------
    // Escrita: sempre no Room primeiro, com a operação na fila
    // ---------------------------------------------------------------

    // A API usa o usuário logado como motorista; motoristaId e motoristaNome são só para a
    // tela mostrar quem abriu.
    suspend fun abrir(
        veiculo: VeiculoDto,
        rota: RotaEntity,
        hodometroInicial: Int,
        motoristaId: String,
        motoristaNome: String,
    ): ViagemEntity {
        val agora = System.currentTimeMillis()
        val dataAbertura = Instant.now().toString()
        val viagem = ViagemEntity(
            id = UUID.randomUUID().toString(),
            idServidor = null,
            rotaId = rota.id,
            rotaNome = rota.nome,
            rotaOrigem = rota.origem,
            rotaDestino = rota.destino,
            veiculoId = veiculo.id,
            veiculoPlaca = veiculo.placa,
            veiculoMarca = veiculo.marca,
            veiculoModelo = veiculo.modelo,
            motoristaId = motoristaId,
            motoristaNome = motoristaNome,
            hodometroInicial = hodometroInicial,
            hodometroFinal = null,
            dataAbertura = dataAbertura,
            dataFechamento = null,
            status = StatusViagem.ABERTA,
            observacao = null,
            checklistRespondido = false,
            checklistLiberado = false,
            syncVersion = 0,
            situacaoSync = SituacaoSync.PENDENTE,
            erroSync = null,
            salvoEm = agora,
        )
        val corpo = AbrirViagemRequestDto(
            rotaId = rota.id,
            veiculoId = veiculo.id,
            hodometroInicial = hodometroInicial,
            dataAbertura = dataAbertura,
        )
        db.withTransaction {
            val locais = dao.listarViagens()
            // Um veículo, uma viagem aberta: confere também o que ainda não subiu.
            val emViagem = locais.any { it.status == StatusViagem.ABERTA && it.veiculoId == veiculo.id }
            if (veiculo.status != "Disponivel" || emViagem) {
                throw ViagemValidacaoException("Este veículo está em manutenção ou já está em viagem")
            }
            val ultimo = RegrasViagem.ultimoHodometro(
                veiculo.hodometroAtual,
                locais.filter { it.veiculoId == veiculo.id }.map { it.hodometroFinal ?: it.hodometroInicial },
            )
            RegrasViagem.erroHodometroInicial(hodometroInicial, ultimo)?.let { throw ViagemValidacaoException(it) }
            dao.salvarViagem(viagem)
            fila.inserir(novaOperacao(viagem.id, VerboSync.ABRIR, gson.toJson(corpo), agora))
        }
        FrotaRepository.ajustarVeiculoLocal(veiculo.id, "EmViagem", hodometroInicial)
        sincronizarAgora()
        return viagem
    }

    // Devolve false quando algum item obrigatório ficou não conforme: a resposta é gravada
    // e enviada, mas a viagem não pode ser fechada até o checklist ser respondido de novo.
    suspend fun responderChecklist(viagemId: String, respostas: List<RespostaChecklistItem>): Boolean {
        if (respostas.isEmpty()) throw ViagemValidacaoException("Responda o checklist antes de iniciar a viagem")
        if (respostas.any { !it.conforme && it.observacao.isNullOrBlank() }) {
            throw ViagemValidacaoException("Descreva o problema encontrado neste item")
        }
        val agora = System.currentTimeMillis()
        val liberado = db.withTransaction {
            val viagem = dao.buscar(viagemId) ?: throw ViagemValidacaoException("Viagem não encontrada")
            if (viagem.status != StatusViagem.ABERTA) {
                throw ViagemValidacaoException("O checklist só pode ser respondido em uma viagem aberta")
            }
            // A API só fecha a viagem com todos os itens obrigatórios conformes.
            val obrigatorios = dao.listarItensChecklist().filter { it.obrigatorio }.map { it.id }.toSet()
            val semPendencia = respostas.none { !it.conforme && it.itemChecklistId in obrigatorios }
            fila.inserir(novaOperacao(viagem.id, VerboSync.CHECKLIST, gson.toJson(ChecklistPayload(respostas)), agora))
            dao.salvarViagem(
                comSituacao(viagem.copy(checklistRespondido = true, checklistLiberado = semPendencia, salvoEm = agora))
            )
            semPendencia
        }
        sincronizarAgora()
        return liberado
    }

    suspend fun fechar(viagemId: String, hodometroFinal: Int): ViagemEntity {
        val agora = System.currentTimeMillis()
        val dataFechamento = Instant.now().toString()
        val fechada = db.withTransaction {
            val viagem = dao.buscar(viagemId) ?: throw ViagemValidacaoException("Viagem não encontrada")
            if (viagem.status != StatusViagem.ABERTA) throw ViagemValidacaoException("Esta viagem já foi encerrada")
            if (!viagem.checklistRespondido) throw ViagemValidacaoException("Responda o checklist antes de fechar a viagem")
            if (!viagem.checklistLiberado) {
                throw ViagemValidacaoException(
                    "Há item obrigatório não conforme no checklist. Resolva o problema e responda o checklist de novo."
                )
            }
            RegrasViagem.erroHodometroFinal(viagem.hodometroInicial, hodometroFinal)
                ?.let { throw ViagemValidacaoException(it) }
            val corpo = FecharViagemRequestDto(hodometroFinal = hodometroFinal, dataFechamento = dataFechamento)
            fila.inserir(novaOperacao(viagem.id, VerboSync.FECHAR, gson.toJson(corpo), agora))
            val nova = comSituacao(
                viagem.copy(
                    status = StatusViagem.ENCERRADA,
                    hodometroFinal = hodometroFinal,
                    dataFechamento = dataFechamento,
                    salvoEm = agora,
                )
            )
            dao.salvarViagem(nova)
            nova
        }
        FrotaRepository.ajustarVeiculoLocal(fechada.veiculoId, "Disponivel", hodometroFinal)
        sincronizarAgora()
        return fechada
    }

    // Põe de volta na fila as operações que a API recusou ou que estouraram as tentativas,
    // por exemplo depois de o problema ser resolvido no servidor.
    suspend fun tentarDeNovo(viagemId: String) {
        db.withTransaction {
            fila.listarDoRegistro(viagemId)
                .filter { it.situacao == SituacaoSync.ERRO }
                .forEach { fila.atualizar(it.copy(situacao = SituacaoSync.PENDENTE, tentativas = 0)) }
            atualizarSituacao(viagemId)
        }
        sincronizarAgora()
    }

    // Saída para um erro que não se resolve: apaga as operações do registro. A viagem que
    // nunca chegou à API some do aparelho; a que chegou volta a valer a versão do servidor
    // na próxima carga.
    suspend fun descartarAlteracoes(viagemId: String) {
        db.withTransaction {
            val viagem = dao.buscar(viagemId) ?: return@withTransaction
            fila.apagarDoRegistro(viagemId)
            if (viagem.idServidor == null) {
                dao.apagarViagens(listOf(viagemId))
            } else {
                dao.definirSituacao(viagemId, SituacaoSync.SINCRONIZADA, null)
            }
        }
    }

    // Ao sair da conta: apaga viagens, rotas, checklist e a fila deste aparelho.
    suspend fun limpar() {
        if (!::db.isInitialized) return
        db.withTransaction {
            fila.limpar()
            dao.limparTudo()
        }
        semConexao = false
        erroCarga = null
    }

    // ---------------------------------------------------------------
    // Subida: a fila, na ordem em que as operações foram criadas
    // ---------------------------------------------------------------
    suspend fun enviarPendentes(): ResultadoEnvio = envio.withLock {
        var tentarDeNovo = false
        // Registro com operação parada (erro ou à espera da anterior) não manda as próximas:
        // o fechamento nunca passa na frente da abertura.
        val bloqueados = mutableSetOf<String>()
        for (operacao in fila.listar()) {
            if (operacao.registroId in bloqueados) continue
            if (operacao.situacao == SituacaoSync.ERRO) {
                bloqueados += operacao.registroId
                continue
            }
            val viagem = dao.buscar(operacao.registroId)
            if (viagem == null) {
                fila.apagar(operacao.seq)
                continue
            }
            val falha = try {
                enviar(operacao, viagem)
            } catch (e: IOException) {
                Falha.Rede(e.message ?: "Sem conexão")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Resposta fora do formato esperado: conta como tentativa, como falha de rede.
                Falha.Rede(e.message ?: "Resposta inesperada da API")
            }
            when (falha) {
                Falha.Nenhuma -> {
                    fila.apagar(operacao.seq)
                    atualizarSituacao(operacao.registroId)
                }
                is Falha.Rede -> {
                    registrarTentativa(operacao, falha.mensagem)
                    tentarDeNovo = true
                    break
                }
                // Sessão acabou: para aqui sem contar tentativa; tenta de novo ao abrir o app.
                Falha.Sessao -> break
                is Falha.Recusada -> {
                    marcarErro(operacao, falha.mensagem)
                    bloqueados += operacao.registroId
                }
                Falha.Aguardando -> bloqueados += operacao.registroId
            }
        }
        if (tentarDeNovo) ResultadoEnvio.TENTAR_DE_NOVO else ResultadoEnvio.CONCLUIDO
    }

    private suspend fun enviar(operacao: OperacaoSyncEntity, viagem: ViagemEntity): Falha = when (operacao.verbo) {
        VerboSync.ABRIR -> enviarAbertura(operacao, viagem)
        VerboSync.CHECKLIST -> enviarChecklist(operacao, viagem)
        VerboSync.FECHAR -> enviarFechamento(operacao, viagem)
        else -> Falha.Recusada("Operação desconhecida: ${operacao.verbo}")
    }

    private suspend fun enviarAbertura(operacao: OperacaoSyncEntity, viagem: ViagemEntity): Falha {
        if (viagem.idServidor != null) return Falha.Nenhuma
        val corpo = gson.fromJson(operacao.payload, AbrirViagemRequestDto::class.java)
        val resposta = api.abrir(operacao.id, corpo)
        if (resposta.isSuccessful) {
            // Sem o corpo não há o id; o próximo envio cai no OPEN_TRIP_EXISTS e adota a viagem.
            val dto = resposta.body() ?: return Falha.Rede("A API respondeu sem a viagem")
            dao.adotarIdServidor(viagem.id, dto.id, dto.syncVersion)
            return Falha.Nenhuma
        }
        val erro = lerErro(resposta)
        if (erro.codigo == "OPEN_TRIP_EXISTS") {
            // A abertura pode ter chegado e só a resposta se perdeu. Se a viagem aberta do
            // veículo for esta (mesmo motorista, rota, hodômetro e horário), adota o id dela.
            val abertas = api.viagens(mapOf("veiculoId" to corpo.veiculoId, "status" to StatusViagem.ABERTA))
                .body()?.data.orEmpty()
            val mesma = abertas.firstOrNull {
                it.motorista.id == viagem.motoristaId &&
                    it.rota.id == corpo.rotaId &&
                    it.hodometroInicial == corpo.hodometroInicial &&
                    mesmoSegundo(it.dataAbertura, corpo.dataAbertura)
            }
            if (mesma != null) {
                dao.adotarIdServidor(viagem.id, mesma.id, mesma.syncVersion)
                return Falha.Nenhuma
            }
        }
        return falhaDaResposta(resposta.code(), erro)
    }

    private suspend fun enviarChecklist(operacao: OperacaoSyncEntity, viagem: ViagemEntity): Falha {
        val idServidor = viagem.idServidor ?: return Falha.Aguardando
        val payload = gson.fromJson(operacao.payload, ChecklistPayload::class.java)
        var noServidor: Map<String, RespostaChecklistServidorDto>? = null
        for (item in payload.respostas) {
            val chave = "${operacao.id}-${item.itemChecklistId}"
            val observacao = if (item.conforme) null else item.observacao?.takeIf { it.isNotBlank() }
            val corpo = RespostaChecklistRequestDto(
                viagemId = idServidor,
                itemChecklistId = item.itemChecklistId,
                conforme = item.conforme,
                observacao = observacao,
            )
            val resposta = api.responderChecklist(chave, corpo)
            if (resposta.isSuccessful) continue
            val erro = lerErro(resposta)
            if (erro.codigo != "DUPLICATE") return falhaDaResposta(resposta.code(), erro)
            // O item já tem resposta na API: reenvio de uma resposta perdida ou checklist
            // respondido de novo. Se a resposta mudou, atualiza a que está lá.
            val existentes = noServidor ?: run {
                val lista = api.respostasChecklist(mapOf("viagemId" to idServidor))
                if (!lista.isSuccessful) return falhaDaResposta(lista.code(), lerErro(lista))
                lista.body()?.data.orEmpty().associateBy { it.item.id }.also { noServidor = it }
            }
            val atual = existentes[item.itemChecklistId] ?: continue
            if (atual.conforme == item.conforme && (item.conforme || atual.observacao == observacao)) continue
            val troca = api.atualizarRespostaChecklist(
                atual.id,
                chave,
                AtualizarRespostaChecklistDto(conforme = item.conforme, observacao = observacao),
            )
            if (!troca.isSuccessful) return falhaDaResposta(troca.code(), lerErro(troca))
        }
        return Falha.Nenhuma
    }

    private suspend fun enviarFechamento(operacao: OperacaoSyncEntity, viagem: ViagemEntity): Falha {
        val idServidor = viagem.idServidor ?: return Falha.Aguardando
        val corpo = gson.fromJson(operacao.payload, FecharViagemRequestDto::class.java)
            .copy(syncVersion = viagem.syncVersion.takeIf { it > 0 })
        val resposta = api.fechar(idServidor, operacao.id, corpo)
        if (resposta.isSuccessful) {
            resposta.body()?.let { dto -> dao.definirSyncVersion(viagem.id, dto.syncVersion) }
            return Falha.Nenhuma
        }
        val erro = lerErro(resposta)
        if (erro.codigo == "TRIP_ALREADY_CLOSED") {
            // Pode ser o reenvio de um fechamento que já chegou.
            val noServidor = api.viagem(idServidor).body()
            if (noServidor != null && noServidor.status == StatusViagem.ENCERRADA &&
                noServidor.hodometroFinal == corpo.hodometroFinal
            ) {
                dao.definirSyncVersion(viagem.id, noServidor.syncVersion)
                return Falha.Nenhuma
            }
        }
        return falhaDaResposta(resposta.code(), erro)
    }

    // ---------------------------------------------------------------
    // Apoio
    // ---------------------------------------------------------------
    private sealed class Falha {
        object Nenhuma : Falha()
        data class Rede(val mensagem: String) : Falha()
        object Sessao : Falha()
        data class Recusada(val mensagem: String) : Falha()
        object Aguardando : Falha()
    }

    private data class ErroApi(val codigo: String?, val mensagem: String)

    private fun lerErro(response: Response<*>): ErroApi {
        val corpo = runCatching {
            gson.fromJson(response.errorBody()?.string(), ErrorResponseDto::class.java)
        }.getOrNull()
        val detalhe = corpo?.error?.details?.firstOrNull()?.get("mensagem")
        return ErroApi(corpo?.error?.code, detalhe ?: corpo?.error?.message ?: "Erro ${response.code()} da API")
    }

    // 401 = sessão; 5xx, 408 e 429 = tentar de novo; o resto a API recusou de vez.
    private fun falhaDaResposta(status: Int, erro: ErroApi): Falha = when {
        status == 401 -> Falha.Sessao
        status >= 500 || status == 408 || status == 429 -> Falha.Rede(erro.mensagem)
        else -> Falha.Recusada(erro.mensagem)
    }

    private fun <T> verificar(response: Response<T>): T {
        if (!response.isSuccessful) throw ViagemApiException(lerErro(response).mensagem)
        return response.body() ?: throw ViagemApiException("Resposta vazia da API")
    }

    // A API guarda a data sem os milissegundos; compara até o segundo.
    private fun mesmoSegundo(a: String?, b: String?): Boolean {
        if (a == null || b == null) return false
        return runCatching { Instant.parse(a).epochSecond == Instant.parse(b).epochSecond }.getOrDefault(false)
    }

    private fun novaOperacao(registroId: String, verbo: String, payload: String, agora: Long) = OperacaoSyncEntity(
        id = UUID.randomUUID().toString(),
        entidade = EntidadeSync.VIAGEM,
        registroId = registroId,
        verbo = verbo,
        payload = payload,
        criadoEm = agora,
    )

    // A situação do registro sai da fila: alguma operação com erro = Erro; alguma
    // pendente = Pendente; fila vazia = Sincronizada.
    private suspend fun comSituacao(viagem: ViagemEntity): ViagemEntity {
        val operacoes = fila.listarDoRegistro(viagem.id)
        val comErro = operacoes.firstOrNull { it.situacao == SituacaoSync.ERRO }
        val situacao = when {
            comErro != null -> SituacaoSync.ERRO
            operacoes.isNotEmpty() -> SituacaoSync.PENDENTE
            else -> SituacaoSync.SINCRONIZADA
        }
        return viagem.copy(situacaoSync = situacao, erroSync = comErro?.ultimoErro)
    }

    private suspend fun atualizarSituacao(registroId: String) {
        db.withTransaction {
            val viagem = dao.buscar(registroId) ?: return@withTransaction
            val nova = comSituacao(viagem)
            dao.definirSituacao(registroId, nova.situacaoSync, nova.erroSync)
        }
    }

    private suspend fun registrarTentativa(operacao: OperacaoSyncEntity, mensagem: String) {
        val tentativas = operacao.tentativas + 1
        if (tentativas >= MAX_TENTATIVAS) {
            marcarErro(operacao.copy(tentativas = tentativas), "Não subiu depois de $MAX_TENTATIVAS tentativas: $mensagem")
        } else {
            fila.atualizar(operacao.copy(tentativas = tentativas, ultimoErro = mensagem))
        }
    }

    private suspend fun marcarErro(operacao: OperacaoSyncEntity, mensagem: String) {
        fila.atualizar(operacao.copy(situacao = SituacaoSync.ERRO, ultimoErro = mensagem))
        atualizarSituacao(operacao.registroId)
    }
}
