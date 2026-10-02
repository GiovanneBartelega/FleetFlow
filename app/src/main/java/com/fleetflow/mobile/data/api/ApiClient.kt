package com.fleetflow.mobile.data.api

import android.content.Context
import com.fleetflow.mobile.data.auth.TokenManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    // Configurable base URL: 10.0.2.2 points to localhost (backend) from Android Emulator
    private const val DEFAULT_BASE_URL = "http://10.0.2.2:8080/"

    @Volatile
    private var retrofit: Retrofit? = null

    @Volatile
    private var authApi: AuthApi? = null

    @Volatile
    private var userApi: UserApi? = null

    fun getRetrofit(context: Context, baseUrl: String = DEFAULT_BASE_URL): Retrofit {
        return retrofit ?: synchronized(this) {
            retrofit ?: buildRetrofit(context.applicationContext, baseUrl).also { retrofit = it }
        }
    }

    fun getAuthApi(context: Context): AuthApi {
        return authApi ?: synchronized(this) {
            authApi ?: getRetrofit(context).create(AuthApi::class.java).also { authApi = it }
        }
    }

    fun getUserApi(context: Context): UserApi {
        return userApi ?: synchronized(this) {
            userApi ?: getRetrofit(context).create(UserApi::class.java).also { userApi = it }
        }
    }

    private fun buildRetrofit(context: Context, baseUrl: String): Retrofit {
        val tokenManager = TokenManager(context)

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(tokenManager))
            .addInterceptor(loggingInterceptor)
            .authenticator(TokenAuthenticator(tokenManager) { getAuthApi(context) })
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
