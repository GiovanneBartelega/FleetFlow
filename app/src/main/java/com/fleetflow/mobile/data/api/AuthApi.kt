package com.fleetflow.mobile.data.api

import com.fleetflow.mobile.data.model.AuthResponseDto
import com.fleetflow.mobile.data.model.DevLoginRequestDto
import com.fleetflow.mobile.data.model.GoogleLoginRequestDto
import com.fleetflow.mobile.data.model.MeResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {

    @POST("api/auth/google")
    suspend fun googleLogin(
        @Body request: GoogleLoginRequestDto
    ): Response<AuthResponseDto>

    // Só funciona se a API estiver com DEV_LOGIN=true (ambiente local de testes).
    @POST("api/auth/dev-login")
    suspend fun devLogin(
        @Body request: DevLoginRequestDto
    ): Response<AuthResponseDto>

    @GET("api/auth/me")
    suspend fun me(): Response<MeResponseDto>
}
