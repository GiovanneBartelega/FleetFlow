package com.fleetflow.mobile.data.repository

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fleetflow.mobile.data.api.ApiClient
import com.fleetflow.mobile.data.api.FrotaApi
import com.fleetflow.mobile.data.model.ErrorResponseDto
import com.fleetflow.mobile.data.model.ConcluirOrdemRequestDto
import com.fleetflow.mobile.data.model.OrdemServicoDto
import com.fleetflow.mobile.data.model.OrdemServicoRequestDto
import com.fleetflow.mobile.data.model.VeiculoDto
import com.fleetflow.mobile.data.model.VeiculoRequestDto
import com.google.gson.Gson
import retrofit2.Response

class FrotaApiException(message: String) : Exception(message)

object FrotaRepository {
    private lateinit var api: FrotaApi
    val veiculos = mutableStateListOf<VeiculoDto>()
    val ordens = mutableStateListOf<OrdemServicoDto>()
    var carregando by mutableStateOf(false); private set

    fun init(context: Context) {
        api = ApiClient.getRetrofit(context.applicationContext).create(FrotaApi::class.java)
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

    suspend fun carregar() {
        carregando = true
        try {
            veiculos.clear(); veiculos.addAll(checked(api.veiculos(mapOf("pageSize" to "100"))).data)
            ordens.clear(); ordens.addAll(checked(api.ordens(mapOf("pageSize" to "100"))).data)
        } finally { carregando = false }
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
    fun clear() { veiculos.clear(); ordens.clear() }
}
