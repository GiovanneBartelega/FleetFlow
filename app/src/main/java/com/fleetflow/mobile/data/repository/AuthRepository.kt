package com.fleetflow.mobile.data.repository

import android.content.Context
import com.fleetflow.mobile.data.api.ApiClient
import com.fleetflow.mobile.data.auth.TokenManager
import com.fleetflow.mobile.data.model.AuthResponseDto
import com.fleetflow.mobile.data.model.DevLoginRequestDto
import com.fleetflow.mobile.data.model.ErrorResponseDto
import com.fleetflow.mobile.data.model.GoogleLoginRequestDto
import com.fleetflow.mobile.data.model.PerfilDto
import com.fleetflow.mobile.data.model.UpdateUsuarioRequestDto
import com.fleetflow.mobile.data.model.UserResponseDto
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

// Erro de login com o código devolvido pela API (ex.: ACCOUNT_PENDING,
// ACCOUNT_BLOCKED, ACCOUNT_DISABLED), para a tela decidir para onde navegar.
class ApiException(val code: String?, message: String) : Exception(message)

class AuthRepository(private val context: Context) {

    private val authApi = ApiClient.getAuthApi(context)
    private val userApi = ApiClient.getUserApi(context)
    private val tokenManager = TokenManager(context)
    private val gson = Gson()

    private fun parseError(response: Response<*>): ApiException {
        val body = response.errorBody()?.string()
        val parsed = runCatching { gson.fromJson(body, ErrorResponseDto::class.java) }.getOrNull()
        val detail = parsed?.error
        return ApiException(detail?.code, detail?.message ?: "Erro ${response.code()}")
    }

    private suspend fun handleAuthResponse(response: Response<AuthResponseDto>): Result<UserResponseDto> {
        if (response.isSuccessful && response.body() != null) {
            val authData = response.body()!!
            tokenManager.saveToken(authData.token)
            authData.refreshToken?.let { tokenManager.saveRefreshToken(it) }
            tokenManager.saveUserInfo(
                authData.usuario.id,
                authData.usuario.nome,
                authData.usuario.email,
                authData.usuario.perfil.nome,
                authData.usuario.status,
                authData.permissoes
            )
            tokenManager.saveUsuario(authData.usuario)
            return Result.success(authData.usuario)
        }
        return Result.failure(parseError(response))
    }

    suspend fun loginWithGoogle(idToken: String): Result<UserResponseDto> = withContext(Dispatchers.IO) {
        try {
            handleAuthResponse(authApi.googleLogin(GoogleLoginRequestDto(idToken)))
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    // Login sem Google, só funciona com DEV_LOGIN=true na API (uso local/teste).
    suspend fun loginDev(email: String): Result<UserResponseDto> = withContext(Dispatchers.IO) {
        try {
            handleAuthResponse(authApi.devLogin(DevLoginRequestDto(email)))
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    suspend fun getCurrentUser(): Result<UserResponseDto> = withContext(Dispatchers.IO) {
        try {
            var response = authApi.me()
            if(response.code()==401) {
                val refresh=tokenManager.getRefreshToken()
                if(refresh!=null) {
                    val renewed=authApi.refresh(com.fleetflow.mobile.data.model.RefreshRequestDto(refresh))
                    if(renewed.isSuccessful) {handleAuthResponse(renewed);response=authApi.me()}
                }
            }
            if (response.isSuccessful && response.body() != null) {
                val me = response.body()!!
                tokenManager.saveUserInfo(
                    me.usuario.id,
                    me.usuario.nome,
                    me.usuario.email,
                    me.usuario.perfil.nome,
                    me.usuario.status,
                    me.permissoes
                )
                tokenManager.saveUsuario(me.usuario)
                Result.success(me.usuario)
            } else {
                Result.failure(parseError(response))
            }
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    suspend fun getUsers(): Result<List<UserResponseDto>> = withContext(Dispatchers.IO) {
        try {
            val response = userApi.getUsuarios()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.data)
            } else {
                Result.failure(parseError(response))
            }
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    suspend fun getPerfis(): Result<List<PerfilDto>> = withContext(Dispatchers.IO) {
        try {
            val response = userApi.getPerfis()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.data)
            } else {
                Result.failure(parseError(response))
            }
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    // Aprovar = sair de "AguardandoAprovacao" para "Ativo".
    suspend fun approveUser(id: String): Result<UserResponseDto> = withContext(Dispatchers.IO) {
        try {
            val response = userApi.updateUsuario(id, UpdateUsuarioRequestDto(status = "Ativo"))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(parseError(response))
            }
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    suspend fun updateUserPerfil(id: String, perfilId: String): Result<UserResponseDto> = withContext(Dispatchers.IO) {
        try {
            val response = userApi.updateUsuario(id, UpdateUsuarioRequestDto(perfilId = perfilId))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(parseError(response))
            }
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    // Revoga refresh no servidor e limpa credenciais locais ao sair.
    suspend fun logout(): Result<Boolean> = withContext(Dispatchers.IO) {
        tokenManager.getRefreshToken()?.let { runCatching { authApi.logout(com.fleetflow.mobile.data.model.RefreshRequestDto(it)) } }
        tokenManager.clearTokens()
        Result.success(true)
    }

    suspend fun isLoggedIn(): Boolean {
        val token = tokenManager.getAccessToken()
        return !token.isNullOrBlank()
    }

    suspend fun getStoredPermissoes(): Map<String, String?> = tokenManager.getPermissoes()

    // Usuário do último acesso, para abrir o app quando a API não responde.
    suspend fun getUsuarioSalvo(): UserResponseDto? = tokenManager.getUsuarioSalvo()
}
