package com.fleetflow.mobile.data.api

import com.fleetflow.mobile.data.model.AbrirViagemRequestDto
import com.fleetflow.mobile.data.model.AtualizarRespostaChecklistDto
import com.fleetflow.mobile.data.model.FecharViagemRequestDto
import com.fleetflow.mobile.data.model.ItensChecklistDto
import com.fleetflow.mobile.data.model.RespostaChecklistDto
import com.fleetflow.mobile.data.model.RespostaChecklistRequestDto
import com.fleetflow.mobile.data.model.RespostasChecklistDto
import com.fleetflow.mobile.data.model.RotaDto
import com.fleetflow.mobile.data.model.RotaRequestDto
import com.fleetflow.mobile.data.model.RotasPageDto
import com.fleetflow.mobile.data.model.ViagemDto
import com.fleetflow.mobile.data.model.ViagensPageDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.QueryMap

// O cabeçalho Idempotency-Key leva o id da operação da fila. A API ainda não lê esse
// cabeçalho; ele já vai para que o reenvio fique seguro quando ela passar a ler.
interface ViagemApi {
    @GET("api/rotas") suspend fun rotas(@QueryMap query: Map<String, String>): Response<RotasPageDto>

    // Cadastro de rotas (Administrador e Gestor de Frota).
    @POST("api/rotas") suspend fun criarRota(@Body body: RotaRequestDto): Response<RotaDto>
    @PATCH("api/rotas/{id}") suspend fun editarRota(@Path("id") id: String, @Body body: RotaRequestDto): Response<RotaDto>
    @DELETE("api/rotas/{id}") suspend fun removerRota(@Path("id") id: String): Response<Unit>

    @GET("api/viagens") suspend fun viagens(@QueryMap query: Map<String, String>): Response<ViagensPageDto>
    @GET("api/viagens/{id}") suspend fun viagem(@Path("id") id: String): Response<ViagemDto>

    @POST("api/viagens/abrir")
    suspend fun abrir(@Header("Idempotency-Key") chave: String, @Body body: AbrirViagemRequestDto): Response<ViagemDto>

    @POST("api/viagens/{id}/fechar")
    suspend fun fechar(
        @Path("id") id: String,
        @Header("Idempotency-Key") chave: String,
        @Body body: FecharViagemRequestDto,
    ): Response<ViagemDto>

    @GET("api/itens-checklist") suspend fun itensChecklist(): Response<ItensChecklistDto>

    @POST("api/checklists-viagem")
    suspend fun responderChecklist(
        @Header("Idempotency-Key") chave: String,
        @Body body: RespostaChecklistRequestDto,
    ): Response<RespostaChecklistDto>

    @GET("api/checklists-viagem")
    suspend fun respostasChecklist(@QueryMap query: Map<String, String>): Response<RespostasChecklistDto>

    @PATCH("api/checklists-viagem/{id}")
    suspend fun atualizarRespostaChecklist(
        @Path("id") id: String,
        @Header("Idempotency-Key") chave: String,
        @Body body: AtualizarRespostaChecklistDto,
    ): Response<RespostaChecklistDto>
}
