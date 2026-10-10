package com.fleetflow.mobile.data.api

import com.fleetflow.mobile.data.model.PerfisResponseDto
import com.fleetflow.mobile.data.model.UpdateUsuarioRequestDto
import com.fleetflow.mobile.data.model.UserResponseDto
import com.fleetflow.mobile.data.model.UsuariosPageDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

interface UserApi {

    @GET("api/usuarios")
    suspend fun getUsuarios(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 100
    ): Response<UsuariosPageDto>

    @PATCH("api/usuarios/{id}")
    suspend fun updateUsuario(
        @Path("id") id: String,
        @Body request: UpdateUsuarioRequestDto
    ): Response<UserResponseDto>

    @GET("api/perfis")
    suspend fun getPerfis(): Response<PerfisResponseDto>
}
