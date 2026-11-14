package com.fleetflow.mobile.data.repository

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fleetflow.mobile.data.api.ApiClient
import com.fleetflow.mobile.data.api.ViagemApi
import com.fleetflow.mobile.data.model.ErrorResponseDto
import com.fleetflow.mobile.data.model.RotaDto
import com.fleetflow.mobile.data.model.RotaRequestDto
import com.google.gson.Gson
import retrofit2.Response
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

class RotasApiException(val codigo: String?, message: String) : Exception(message)

// Cadastro de rotas ligado à API (Administrador e Gestor de Frota escrevem; os outros
// perfis só leem). Tudo o que muda aqui também vai para a cópia de rotas do aparelho, que
// é de onde a tela Abrir viagem lê: assim a rota recém-cadastrada já aparece no seletor,
// inclusive sem internet depois.
//
// Antes a tela de Rotas usava dados de exemplo em memória (RotaRepo): a rota cadastrada
// nunca chegava à API e, por isso, não aparecia na abertura de viagem.
object RotasRepository {

    private lateinit var api: ViagemApi
    private val gson = Gson()

    val rotas = mutableStateListOf<RotaDto>()

    var carregando by mutableStateOf(false)
        private set

    var semConexao by mutableStateOf(false)
        private set

    var erro by mutableStateOf<String?>(null)
        private set

    fun init(context: Context) {
        if (::api.isInitialized) return
        api = ApiClient.getRetrofit(context.applicationContext).create(ViagemApi::class.java)
    }

    suspend fun carregar() {
        carregando = true
        erro = null
        try {
            val lista = conferirResposta(api.rotas(mapOf("pageSize" to "100"))).data
            rotas.clear()
            rotas.addAll(lista)
            semConexao = false
            ViagemRepository.salvarRotasLocais(lista)
        } catch (e: IOException) {
            semConexao = true
            erro = "Sem conexão com o servidor."
            // Leitura offline: sem nada carregado nesta sessão, mostra a cópia do aparelho.
            if (rotas.isEmpty()) {
                rotas.addAll(
                    ViagemRepository.listarRotasLocais().map {
                        RotaDto(it.id, it.nome, it.origem, it.destino, it.distanciaEstimadaKm)
                    }
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            erro = e.message ?: "Não foi possível carregar as rotas."
        } finally {
            carregando = false
        }
    }

    // As três operações lançam RotasApiException com a mensagem da API (ou IOException sem
    // conexão); a tela mostra a mensagem no formulário.
    suspend fun criar(dados: RotaRequestDto): RotaDto {
        val nova = conferirResposta(api.criarRota(dados.copy(syncVersion = null)))
        rotas.add(nova)
        ordenar()
        ViagemRepository.salvarRotaLocal(nova)
        return nova
    }

    suspend fun editar(atual: RotaDto, dados: RotaRequestDto): RotaDto {
        // syncVersion 0 = rota vinda da cópia do aparelho, sem a versão: edita sem conferir.
        val resposta = api.editarRota(atual.id, dados.copy(syncVersion = atual.syncVersion.takeIf { it > 0 }))
        if (!resposta.isSuccessful) {
            val falha = lerErro(resposta)
            if (falha.codigo == "VERSION_CONFLICT") {
                // Outra pessoa mudou a rota depois que a lista foi carregada. A lista passa a
                // ter a versão nova e quem está editando decide: salvar de novo grava a sua
                // versão por cima; cancelar mantém a da outra pessoa.
                carregar()
                throw RotasApiException(
                    falha.codigo,
                    "Outra pessoa alterou esta rota enquanto você editava. Salve de novo para gravar a sua versão ou cancele para manter a dela.",
                )
            }
            throw falha
        }
        val editada = resposta.body() ?: throw RotasApiException(null, "A API retornou uma resposta vazia.")
        val i = rotas.indexOfFirst { it.id == editada.id }
        if (i >= 0) rotas[i] = editada else rotas.add(editada)
        ordenar()
        ViagemRepository.salvarRotaLocal(editada)
        return editada
    }

    suspend fun remover(rota: RotaDto) {
        val resposta = api.removerRota(rota.id)
        // 404: já não existe no servidor; some daqui também.
        if (!resposta.isSuccessful && resposta.code() != 404) throw lerErro(resposta)
        rotas.removeAll { it.id == rota.id }
        ViagemRepository.removerRotaLocal(rota.id)
    }

    // Já existe outra rota com a mesma origem e o mesmo destino?
    fun parExiste(origem: String, destino: String, ignorarId: String?): Boolean =
        rotas.any {
            it.id != ignorarId &&
                it.origem.trim().equals(origem.trim(), ignoreCase = true) &&
                it.destino.trim().equals(destino.trim(), ignoreCase = true)
        }

    fun limparEstado() {
        rotas.clear()
        carregando = false
        semConexao = false
        erro = null
    }

    // Mesma ordem da API (por nome), para a rota nova não pular de lugar ao recarregar.
    private fun ordenar() {
        val ordenadas = rotas.sortedBy { it.nome.lowercase() }
        rotas.clear()
        rotas.addAll(ordenadas)
    }

    private fun <T> conferirResposta(response: Response<T>): T {
        if (!response.isSuccessful) throw lerErro(response)
        return response.body() ?: throw RotasApiException(null, "A API retornou uma resposta vazia.")
    }

    // A API responde { error: { code, message, details } }; em erro de validação, o
    // detalhe diz qual campo está errado.
    private fun lerErro(response: Response<*>): RotasApiException {
        val corpo = runCatching {
            gson.fromJson(response.errorBody()?.string(), ErrorResponseDto::class.java)
        }.getOrNull()
        val detalhe = corpo?.error?.details?.firstOrNull()?.let { d ->
            listOfNotNull(d["campo"], d["mensagem"]).joinToString(": ").ifBlank { null }
        }
        val mensagem = detalhe ?: corpo?.error?.message ?: "Erro ${response.code()} ao comunicar com a API."
        return RotasApiException(corpo?.error?.code, mensagem)
    }
}
