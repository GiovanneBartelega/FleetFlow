package com.fleetflow.mobile.data.api

import com.fleetflow.mobile.data.model.UpdateRoleRequestDto
import com.fleetflow.mobile.data.model.UserResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path

interface UserApi {

    @GET("api/users/me")
    suspend fun getMe(): Response<UserResponseDto>

    @GET("api/users")
    suspend fun getUsers(): Response<List<UserResponseDto>>

    @PATCH("api/users/{id}/approve")
    suspend fun approveUser(
        @Path("id") id: String
    ): Response<UserResponseDto>

    @PATCH("api/users/{id}/role")
    suspend fun updateUserRole(
        @Path("id") id: String,
        @Body request: UpdateRoleRequestDto
    ): Response<UserResponseDto>
}
