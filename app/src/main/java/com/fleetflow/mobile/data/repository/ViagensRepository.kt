package com.fleetflow.mobile.data.repository

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fleetflow.mobile.data.api.ApiClient
import com.fleetflow.mobile.data.api.ViagemApi
import com.fleetflow.mobile.data.model.ErrorResponseDto
import com.fleetflow.mobile.data.model.ViagemDto
import com.google.gson.Gson
import retrofit2.Response
import java.io.IOException

class ViagensApiException(message: String) : Exception(message)

// Viagens como estão no servidor, para as telas que só leem: o vínculo do lançamento em
// Finanças, o detalhe da viagem e a contagem de viagens por rota. Só online e só em memória.
//
// Quem grava viagem no aparelho e cuida da fila de sincronização é o ViagemRepository
// (offline-first). Na entrega de 07–08/11 esta classe tinha uma tabela própria no Room e,
// a cada carga, apagava a tabela de viagens inteira: sumiam as viagens feitas sem internet
// que ainda não tinham subido. Por isso ela não grava mais nada no aparelho.
object ViagensRepository {

    private lateinit var api: ViagemApi

    // Cada item tem o id do servidor, que é o que a API aceita no vínculo do lançamento.
    val viagens = mutableStateListOf<ViagemDto>()

    var carregando by mutableStateOf(false)
        private set

    var semConexao by mutableStateOf(false)
        private set

    fun init(context: Context) {
        if (::api.isInitialized) return
        api = ApiClient.getRetrofit(context.applicationContext).create(ViagemApi::class.java)
    }

    // Sem conexão, mantém a última lista. Se a API recusar, lança ViagensApiException.
    suspend fun carregar() {
        carregando = true
        try {
            val todas = mutableListOf<ViagemDto>()
            var pagina = 1
            var totalPaginas: Int
            // A API devolve no máximo 100 por página.
            do {
                val resposta = conferirResposta(api.viagens(mapOf("page" to "$pagina", "pageSize" to "100")))
                todas += resposta.data
                totalPaginas = resposta.totalPages
                pagina++
            } while (pagina <= totalPaginas && pagina <= MAX_PAGINAS)
            viagens.clear()
            viagens.addAll(todas)
            semConexao = false
        } catch (e: IOException) {
            semConexao = true
        } finally {
            carregando = false
        }
    }

    fun limparEstado() {
        viagens.clear()
        carregando = false
        semConexao = false
    }

    private const val MAX_PAGINAS = 20

    private fun <T> conferirResposta(response: Response<T>): T {
        if (!response.isSuccessful) {
            val erro = runCatching {
                Gson().fromJson(response.errorBody()?.string(), ErrorResponseDto::class.java)
            }.getOrNull()
            throw ViagensApiException(erro?.error?.message ?: "Erro ${response.code()} ao carregar as viagens.")
        }
        return response.body() ?: throw ViagensApiException("A API retornou uma resposta vazia.")
    }
}
