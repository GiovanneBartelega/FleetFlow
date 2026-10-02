package com.fleetflow.mobile.data.api

import com.fleetflow.mobile.data.auth.TokenManager
import com.fleetflow.mobile.data.model.RefreshTokenRequestDto
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(
    private val tokenManager: TokenManager,
    private val authApiProvider: () -> AuthApi
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Prevent infinite refresh loop if refresh itself returned 401
        if (response.request.url.encodedPath.contains("/api/auth/refresh")) {
            return null
        }

        synchronized(this) {
            val currentRefreshToken = runBlocking { tokenManager.getRefreshToken() } ?: return null

            return try {
                val refreshResponse = runBlocking {
                    authApiProvider().refreshToken(RefreshTokenRequestDto(currentRefreshToken))
                }

                if (refreshResponse.isSuccessful && refreshResponse.body() != null) {
                    val newAuth = refreshResponse.body()!!
                    runBlocking {
                        tokenManager.saveTokens(newAuth.accessToken, newAuth.refreshToken)
                        tokenManager.saveUserInfo(
                            newAuth.user.id,
                            newAuth.user.name,
                            newAuth.user.email,
                            newAuth.user.role
                        )
                    }

                    response.request.newBuilder()
                        .header("Authorization", "Bearer ${newAuth.accessToken}")
                        .build()
                } else {
                    runBlocking { tokenManager.clearTokens() }
                    null
                }
            } catch (ex: Exception) {
                runBlocking { tokenManager.clearTokens() }
                null
            }
        }
    }
}
