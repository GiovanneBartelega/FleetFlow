package com.fleetflow.mobile.data.repository

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fleetflow.mobile.data.api.ApiClient
import com.fleetflow.mobile.data.api.FrotaApi
import com.fleetflow.mobile.data.local.AppDatabase
import com.fleetflow.mobile.data.local.FrotaDao
import com.fleetflow.mobile.data.local.paraDto
import com.fleetflow.mobile.data.local.paraEntity
import com.fleetflow.mobile.data.model.ConcluirOrdemRequestDto
import com.fleetflow.mobile.data.model.ErrorResponseDto
import com.fleetflow.mobile.data.model.OrdemServicoDto
import com.fleetflow.mobile.data.model.OrdemServicoRequestDto
import com.fleetflow.mobile.data.model.VeiculoDto
import com.fleetflow.mobile.data.model.VeiculoRequestDto
import com.google.gson.Gson
import retrofit2.Response
import java.io.IOException

class FrotaApiException(message: String) : Exception(message)

// Fonte dos dados da frota para as telas.
// Caminho de leitura: API -> Room -> tela. Toda lista que chega da API é gravada no Room,
// e a tela sempre mostra o que está no Room. Sem internet, a última cópia continua na tela.
// Gravações (cadastrar, concluir, excluir) ainda exigem conexão; a fila offline vem depois.
object FrotaRepository {
    private lateinit var api: FrotaApi
    private lateinit var dao: FrotaDao

    val veiculos = mutableStateListOf<VeiculoDto>()
    val ordens = mutableStateListOf<OrdemServicoDto>()
    var carregando by mutableStateOf(false); private set

    // true quando a última tentativa de atualizar falhou por falta de conexão.
    var semConexao by mutableStateOf(false); private set

    // Quando a cópia mostrada foi gravada (milissegundos), para o aviso de "dados salvos".
    var salvoEm by mutableStateOf<Long?>(null); private set

    fun init(context: Context) {
        api = ApiClient.getRetrofit(context.applicationContext).create(FrotaApi::class.java)
        dao = AppDatabase.get(context.applicationContext).frotaDao()
    }

    private fun <T> checked(response: Response<T>): T {
        if (!response.isSuccessful) {
            val error = runCatching { Gson().fromJson(response.errorBody()?.string(), ErrorResponseDto::class.java) }.getOrNull()
            val detalhe = error?.error?.details?.firstOrNull()?.get("mensagem")
            throw FrotaApiException(detalhe ?: error?.error?.message ?: "Erro ${response.code()}")
        }
        return response.body() ?: throw FrotaApiException("Resposta vazia")
    }

    private fun checkedEmpty(response: Response<*>) { if (!response.isSuccessful) checked(response) }

    // incluirOrdens = false para o motorista: a API não libera manutenção para esse perfil.
    suspend fun carregar(incluirOrdens: Boolean = true) {
        carregando = true
        try {
            val agora = System.currentTimeMillis()
            val listaVeiculos = checked(api.veiculos(mapOf("pageSize" to "100"))).data
            dao.substituirVeiculos(listaVeiculos.map { it.paraEntity(agora) })
            if (incluirOrdens) {
                val listaOrdens = checked(api.ordens(mapOf("pageSize" to "100"))).data
                dao.substituirOrdens(listaOrdens.map { it.paraEntity(agora) })
            }
            semConexao = false
        } catch (e: IOException) {
            // Sem conexão não é erro para a tela: ela mostra a cópia do Room com um aviso.
            semConexao = true
        } finally {
            mostrarCopiaLocal(incluirOrdens)
            carregando = false
        }
    }

    private suspend fun mostrarCopiaLocal(incluirOrdens: Boolean) {
        veiculos.clear()
        veiculos.addAll(dao.listarVeiculos().map { it.paraDto() })
        ordens.clear()
        if (incluirOrdens) ordens.addAll(dao.listarOrdens().map { it.paraDto() })
        salvoEm = dao.ultimaGravacao()
    }

    suspend fun salvarVeiculo(atual: VeiculoDto?, body: VeiculoRequestDto) {
        if (atual == null) checked(api.criarVeiculo(body))
        else checked(api.editarVeiculo(atual.id, body.copy(syncVersion = atual.syncVersion)))
        carregar()
    }

    suspend fun excluirVeiculo(item: VeiculoDto) { checkedEmpty(api.removerVeiculo(item.id)); carregar() }
    suspend fun criarOrdem(body: OrdemServicoRequestDto) { checked(api.criarOrdem(body)); carregar() }
    suspend fun concluirOrdem(item: OrdemServicoDto, data: String) {
        checked(api.concluir(item.id, ConcluirOrdemRequestDto(data, item.syncVersion))); carregar()
    }
    suspend fun excluirOrdem(item: OrdemServicoDto) { checkedEmpty(api.removerOrdem(item.id)); carregar() }

    // Ao sair da conta: limpa a tela e a cópia local, para o próximo usuário não ver
    // a frota de quem saiu.
    suspend fun limpar() {
        clear()
        if (::dao.isInitialized) dao.limparTudo()
    }

    fun clear() {
        veiculos.clear()
        ordens.clear()
        semConexao = false
        salvoEm = null
    }
}
