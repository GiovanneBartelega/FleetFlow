package com.fleetflow.mobile.data.api

import com.fleetflow.mobile.data.model.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.QueryMap

interface FrotaApi {
    @GET("api/veiculos") suspend fun veiculos(@QueryMap query: Map<String, String>): Response<VeiculosPageDto>
    @POST("api/veiculos") suspend fun criarVeiculo(@Body body: VeiculoRequestDto): Response<VeiculoDto>
    @PATCH("api/veiculos/{id}") suspend fun editarVeiculo(@Path("id") id: String, @Body body: VeiculoRequestDto): Response<VeiculoDto>
    @DELETE("api/veiculos/{id}") suspend fun removerVeiculo(@Path("id") id: String): Response<Unit>

    @GET("api/ordens-servico") suspend fun ordens(@QueryMap query: Map<String, String>): Response<OrdensPageDto>
    @POST("api/ordens-servico") suspend fun criarOrdem(@Body body: OrdemServicoRequestDto): Response<OrdemServicoDto>
    @POST("api/ordens-servico/{id}/concluir") suspend fun concluir(@Path("id") id: String, @Body body: ConcluirOrdemRequestDto): Response<OrdemServicoDto>
    @DELETE("api/ordens-servico/{id}") suspend fun removerOrdem(@Path("id") id: String): Response<Unit>
}
