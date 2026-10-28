package com.fleetflow.mobile.data.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fleetflow.mobile.data.model.UserResponseDto
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "fleetflow_tokens")

class TokenManager(private val context: Context) {

    companion object {
        val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
        val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        val USER_ID_KEY = stringPreferencesKey("user_id")
        val USER_NAME_KEY = stringPreferencesKey("user_name")
        val USER_EMAIL_KEY = stringPreferencesKey("user_email")
        val USER_PERFIL_KEY = stringPreferencesKey("user_perfil")
        val USER_STATUS_KEY = stringPreferencesKey("user_status")
        val PERMISSOES_KEY = stringPreferencesKey("permissoes")
        // Usuário completo do último acesso, criptografado como os tokens. É o que
        // permite abrir o app sem internet.
        val USUARIO_JSON_KEY = stringPreferencesKey("usuario_json")
        private val gson = Gson()
    }

    suspend fun saveUsuario(usuario: UserResponseDto) {
        context.dataStore.edit { it[USUARIO_JSON_KEY] = TokenCipher.encrypt(gson.toJson(usuario)) }
    }

    // null se nunca entrou, se saiu da conta ou se a chave do Keystore mudou.
    suspend fun getUsuarioSalvo(): UserResponseDto? {
        val json = TokenCipher.decrypt(context.dataStore.data.map { it[USUARIO_JSON_KEY] }.first()) ?: return null
        return runCatching { gson.fromJson(json, UserResponseDto::class.java) }.getOrNull()
    }

    val accessToken: Flow<String?> = context.dataStore.data.map { preferences ->
        TokenCipher.decrypt(preferences[ACCESS_TOKEN_KEY])
    }

    suspend fun getAccessToken(): String? = context.dataStore.data.map { TokenCipher.decrypt(it[ACCESS_TOKEN_KEY]) }.first()

    suspend fun getRefreshToken(): String? = context.dataStore.data.map {TokenCipher.decrypt(it[REFRESH_TOKEN_KEY])}.first()
    suspend fun saveRefreshToken(token:String) {context.dataStore.edit {it[REFRESH_TOKEN_KEY]=TokenCipher.encrypt(token)}}
    suspend fun saveToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[ACCESS_TOKEN_KEY] = TokenCipher.encrypt(token)
        }
    }

    suspend fun saveUserInfo(
        id: String,
        name: String,
        email: String,
        perfilNome: String,
        status: String,
        permissoes: Map<String, String?>
    ) {
        context.dataStore.edit { preferences ->
            preferences[USER_ID_KEY] = id
            preferences[USER_NAME_KEY] = name
            preferences[USER_EMAIL_KEY] = email
            preferences[USER_PERFIL_KEY] = perfilNome
            preferences[USER_STATUS_KEY] = status
            preferences[PERMISSOES_KEY] = gson.toJson(permissoes)
        }
    }

    suspend fun getUserName(): String? = context.dataStore.data.map { it[USER_NAME_KEY] }.first()

    suspend fun getUserEmail(): String? = context.dataStore.data.map { it[USER_EMAIL_KEY] }.first()

    suspend fun getUserPerfil(): String? = context.dataStore.data.map { it[USER_PERFIL_KEY] }.first()

    suspend fun getUserStatus(): String? = context.dataStore.data.map { it[USER_STATUS_KEY] }.first()

    suspend fun getPermissoes(): Map<String, String?> {
        val json = context.dataStore.data.map { it[PERMISSOES_KEY] }.first() ?: return emptyMap()
        val type = object : TypeToken<Map<String, String?>>() {}.type
        return gson.fromJson(json, type) ?: emptyMap()
    }

    suspend fun clearTokens() {
        context.dataStore.edit { preferences ->
            preferences.remove(REFRESH_TOKEN_KEY)
            preferences.remove(ACCESS_TOKEN_KEY)
            preferences.remove(USER_ID_KEY)
            preferences.remove(USER_NAME_KEY)
            preferences.remove(USER_EMAIL_KEY)
            preferences.remove(USER_PERFIL_KEY)
            preferences.remove(USER_STATUS_KEY)
            preferences.remove(PERMISSOES_KEY)
            preferences.remove(USUARIO_JSON_KEY)
        }
    }
}
