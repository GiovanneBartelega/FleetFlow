package com.fleetflow.mobile.data.api

import com.fleetflow.mobile.data.model.AuthResponseDto
import com.fleetflow.mobile.data.model.GoogleLoginRequestDto
import com.fleetflow.mobile.data.model.LogoutRequestDto
import com.fleetflow.mobile.data.model.RefreshTokenRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {

    @POST("api/auth/google")
    suspend fun googleLogin(
        @Body request: GoogleLoginRequestDto
    ): Response<AuthResponseDto>

    @POST("api/auth/refresh")
    suspend fun refreshToken(
        @Body request: RefreshTokenRequestDto
    ): Response<AuthResponseDto>

    @POST("api/auth/logout")
    suspend fun logout(
        @Body request: LogoutRequestDto
    ): Response<Map<String, Any>>
}
