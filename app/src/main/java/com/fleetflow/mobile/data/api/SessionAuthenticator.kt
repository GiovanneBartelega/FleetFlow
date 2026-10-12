package com.fleetflow.mobile.data.api

import android.content.Context
import com.fleetflow.mobile.BuildConfig
import com.fleetflow.mobile.data.auth.TokenManager
import com.fleetflow.mobile.data.model.RefreshRequestDto
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SessionAuthenticator(context:Context):Authenticator {
    private val manager=TokenManager(context)
    private val api=Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL)
        .client(OkHttpClient.Builder().build()).addConverterFactory(GsonConverterFactory.create()).build().create(AuthApi::class.java)
    override fun authenticate(route:Route?,response:Response):Request? {
        if(response.priorResponse!=null || response.request.url.encodedPath.contains("/auth/"))return null
        return synchronized(LOCK) {
            runBlocking {
                val current=manager.getAccessToken()
                if(current!=null && response.request.header("Authorization")!="Bearer $current")
                    return@runBlocking response.request.newBuilder().header("Authorization","Bearer $current").build()
                val refresh=manager.getRefreshToken()?:return@runBlocking null
                try {
                    val result=api.refresh(RefreshRequestDto(refresh))
                    val data=result.body()
                    if(!result.isSuccessful || data==null) {if(result.code() in listOf(401,403))manager.clearTokens();return@runBlocking null}
                    manager.saveToken(data.token);data.refreshToken?.let {manager.saveRefreshToken(it)}
                    response.request.newBuilder().header("Authorization","Bearer ${data.token}").build()
                } catch(e:Exception) {null}
            }
        }
    }
    companion object {private val LOCK=Any()}
}
