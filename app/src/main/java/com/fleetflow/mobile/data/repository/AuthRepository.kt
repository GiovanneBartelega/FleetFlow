package com.fleetflow.mobile.data.repository

import android.content.Context
import com.fleetflow.mobile.data.api.ApiClient
import com.fleetflow.mobile.data.auth.TokenManager
import com.fleetflow.mobile.data.model.GoogleLoginRequestDto
import com.fleetflow.mobile.data.model.LogoutRequestDto
import com.fleetflow.mobile.data.model.UpdateRoleRequestDto
import com.fleetflow.mobile.data.model.UserResponseDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository(private val context: Context) {

    private val authApi = ApiClient.getAuthApi(context)
    private val userApi = ApiClient.getUserApi(context)
    private val tokenManager = TokenManager(context)

    suspend fun loginWithGoogle(idToken: String): Result<UserResponseDto> = withContext(Dispatchers.IO) {
        try {
            val response = authApi.googleLogin(GoogleLoginRequestDto(idToken))
            if (response.isSuccessful && response.body() != null) {
                val authData = response.body()!!
                tokenManager.saveTokens(authData.accessToken, authData.refreshToken)
                tokenManager.saveUserInfo(
                    authData.user.id,
                    authData.user.name,
                    authData.user.email,
                    authData.user.role
                )
                Result.success(authData.user)
            } else {
                Result.failure(Exception("Falha no login com Google: ${response.code()} ${response.message()}"))
            }
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    suspend fun getCurrentUser(): Result<UserResponseDto> = withContext(Dispatchers.IO) {
        try {
            val response = userApi.getMe()
            if (response.isSuccessful && response.body() != null) {
                val user = response.body()!!
                tokenManager.saveUserInfo(user.id, user.name, user.email, user.role)
                Result.success(user)
            } else if (response.code() == 403) {
                Result.failure(Exception("Você não possui permissão para acessar este recurso."))
            } else {
                Result.failure(Exception("Erro ao buscar dados do usuário: ${response.code()}"))
            }
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    suspend fun getUsers(): Result<List<UserResponseDto>> = withContext(Dispatchers.IO) {
        try {
            val response = userApi.getUsers()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else if (response.code() == 403) {
                Result.failure(Exception("Você não possui permissão para listar usuários."))
            } else {
                Result.failure(Exception("Erro ao listar usuários: ${response.code()}"))
            }
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    suspend fun approveUser(id: String): Result<UserResponseDto> = withContext(Dispatchers.IO) {
        try {
            val response = userApi.approveUser(id)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else if (response.code() == 403) {
                Result.failure(Exception("Você não possui permissão para aprovar usuários."))
            } else {
                Result.failure(Exception("Erro ao aprovar usuário: ${response.code()}"))
            }
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    suspend fun updateUserRole(id: String, role: String): Result<UserResponseDto> = withContext(Dispatchers.IO) {
        try {
            val response = userApi.updateUserRole(id, UpdateRoleRequestDto(role))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else if (response.code() == 403) {
                Result.failure(Exception("Você não possui permissão para alterar o perfil de usuários."))
            } else {
                Result.failure(Exception("Erro ao alterar perfil do usuário: ${response.code()}"))
            }
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    suspend fun logout(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val refreshToken = tokenManager.getRefreshToken()
            if (!refreshToken.isNullOrBlank()) {
                authApi.logout(LogoutRequestDto(refreshToken))
            }
            tokenManager.clearTokens()
            Result.success(true)
        } catch (ex: Exception) {
            tokenManager.clearTokens()
            Result.success(true)
        }
    }

    suspend fun isLoggedIn(): Boolean {
        val token = tokenManager.getAccessToken()
        return !token.isNullOrBlank()
    }
}
