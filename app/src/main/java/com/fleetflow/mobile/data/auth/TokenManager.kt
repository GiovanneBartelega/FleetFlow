package com.fleetflow.mobile.data.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fleetflow.mobile.data.model.UserResponseDto
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "fleetflow_tokens")

class TokenManager(private val context: Context) {

    companion object {
        val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
        val USER_ID_KEY = stringPreferencesKey("user_id")
        val USER_NAME_KEY = stringPreferencesKey("user_name")
        val USER_EMAIL_KEY = stringPreferencesKey("user_email")
        val USER_ROLE_KEY = stringPreferencesKey("user_role")
        val USUARIO_JSON_KEY = stringPreferencesKey("usuario_json")
        private val gson = Gson()
    }

    // Os dois tokens ficam gravados CRIPTOGRAFADOS (ver CryptoManager).
    // Quem lê por aqui recebe o token original. Se não der para decifrar,
    // recebe null, e o app pede o login de novo.
    val accessToken: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[ACCESS_TOKEN_KEY]?.let { CryptoManager.descriptografar(it) }
    }

    val refreshToken: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[REFRESH_TOKEN_KEY]?.let { CryptoManager.descriptografar(it) }
    }

    suspend fun getAccessToken(): String? = accessToken.first()

    suspend fun getRefreshToken(): String? = refreshToken.first()

    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        val accessCifrado = CryptoManager.criptografar(accessToken)
        val refreshCifrado = CryptoManager.criptografar(refreshToken)
        context.dataStore.edit { preferences ->
            preferences[ACCESS_TOKEN_KEY] = accessCifrado
            preferences[REFRESH_TOKEN_KEY] = refreshCifrado
        }
    }

    suspend fun saveUserInfo(id: String, name: String, email: String, role: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_ID_KEY] = id
            preferences[USER_NAME_KEY] = name
            preferences[USER_EMAIL_KEY] = email
            preferences[USER_ROLE_KEY] = role
        }
    }

    // Guarda o usuário inteiro (com o status) para abrir o app sem internet.
    suspend fun saveUsuario(usuario: UserResponseDto) {
        val json = gson.toJson(usuario)
        context.dataStore.edit { preferences ->
            preferences[USUARIO_JSON_KEY] = json
        }
    }

    suspend fun getUsuarioSalvo(): UserResponseDto? {
        val json = context.dataStore.data.map { it[USUARIO_JSON_KEY] }.first() ?: return null
        return runCatching { gson.fromJson(json, UserResponseDto::class.java) }.getOrNull()
    }

    suspend fun getUserName(): String? = context.dataStore.data.map { it[USER_NAME_KEY] }.first()

    suspend fun getUserEmail(): String? = context.dataStore.data.map { it[USER_EMAIL_KEY] }.first()

    suspend fun getUserRole(): String? = context.dataStore.data.map { it[USER_ROLE_KEY] }.first()

    suspend fun clearTokens() {
        context.dataStore.edit { preferences ->
            preferences.remove(ACCESS_TOKEN_KEY)
            preferences.remove(REFRESH_TOKEN_KEY)
            preferences.remove(USER_ID_KEY)
            preferences.remove(USER_NAME_KEY)
            preferences.remove(USER_EMAIL_KEY)
            preferences.remove(USER_ROLE_KEY)
            preferences.remove(USUARIO_JSON_KEY)
        }
    }
}
