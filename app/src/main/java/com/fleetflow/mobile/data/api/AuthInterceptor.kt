package com.fleetflow.mobile.data.api

import com.fleetflow.mobile.data.auth.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath

        // Skip adding Authorization header for public authentication endpoints
        if (path.contains("/api/auth/google") || path.contains("/api/auth/dev-login")) {
            return chain.proceed(request)
        }

        val accessToken = runBlocking { tokenManager.getAccessToken() }
        val newRequest = if (!accessToken.isNullOrBlank()) {
            request.newBuilder()
                .header("Authorization", "Bearer $accessToken")
                .build()
        } else {
            request
        }

        return chain.proceed(newRequest)
    }
}
