package com.fleetflow.mobile.data.api

import retrofit2.Response
import retrofit2.http.*
import okhttp3.RequestBody
import com.fleetflow.mobile.data.model.*

interface FinanceApi {
    @GET("api/categorias") suspend fun categorias(): Response<DataDto<CategoriaDto>>
    @POST("api/categorias") suspend fun criarCategoria(@Body body:Map<String,String>): Response<CategoriaDto>
    @PATCH("api/categorias/{id}") suspend fun editarCategoria(@Path("id") id:String,@Body body:Map<String,String>):Response<CategoriaDto>
    @DELETE("api/categorias/{id}") suspend fun removerCategoria(@Path("id") id:String):Response<Unit>
    @GET("api/formas-pagamento") suspend fun formas():Response<DataDto<FormaDto>>
    @POST("api/formas-pagamento") suspend fun criarForma(@Body body:Map<String,String>):Response<FormaDto>
    @PATCH("api/formas-pagamento/{id}") suspend fun editarForma(@Path("id") id:String,@Body body:Map<String,String>):Response<FormaDto>
    @DELETE("api/formas-pagamento/{id}") suspend fun removerForma(@Path("id") id:String):Response<Unit>
    @GET("api/movimentacoes") suspend fun movimentacoes(@QueryMap query:Map<String,String>):Response<MovPageDto>
    @GET("api/movimentacoes/saldo") suspend fun saldo():Response<SaldoDto>
    @POST("api/movimentacoes") suspend fun criar(@Body body:MovRequestDto):Response<MovDto>
    @PATCH("api/movimentacoes/{id}") suspend fun editar(@Path("id") id:String,@Body body:MovRequestDto):Response<MovDto>
    @DELETE("api/movimentacoes/{id}") suspend fun remover(@Path("id") id:String):Response<Unit>
    @POST("api/movimentacoes/{id}/pagar") suspend fun pagar(@Path("id") id:String,@Body body:Map<String,String>):Response<MovDto>
    @PUT("api/movimentacoes/{id}/comprovante") suspend fun comprovante(@Path("id") id:String,@Body bytes:RequestBody):Response<Map<String,String>>
    @POST("api/dividas") suspend fun divida(@Body body:DividaRequestDto):Response<Map<String,Any>>
}
