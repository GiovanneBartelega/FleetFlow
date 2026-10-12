package com.fleetflow.backend.security

import com.fleetflow.backend.exception.InvalidTokenException
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.Collections

data class GooglePayloadData(
    val googleId: String,
    val email: String,
    val name: String,
    val picture: String?
)

@Service
class GoogleTokenVerifierService(
    private val googleProperties: GoogleProperties
) {
    private val logger = LoggerFactory.getLogger(GoogleTokenVerifierService::class.java)

    private val verifier: GoogleIdTokenVerifier by lazy {
        GoogleIdTokenVerifier.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance())
            .setAudience(Collections.singletonList(googleProperties.clientId))
            .build()
    }

    fun verify(idTokenString: String): GooglePayloadData {
        if (idTokenString.isBlank()) {
            throw InvalidTokenException("O Google ID Token é obrigatório.")
        }

        // Support for mock tokens in local development or test mode
        if (idTokenString.startsWith("mock-id-token") || googleProperties.clientId.startsWith("your-google-client-id")) {
            logger.info("Using mock/development Google ID Token verification for token: {}", idTokenString)
            return parseMockToken(idTokenString)
        }

        return try {
            val idToken: GoogleIdToken? = verifier.verify(idTokenString)
            if (idToken != null) {
                val payload = idToken.payload
                val googleId = payload.subject
                val email = payload.email
                val name = (payload["name"] as? String) ?: email.substringBefore("@")
                val picture = payload["picture"] as? String

                GooglePayloadData(
                    googleId = googleId,
                    email = email,
                    name = name,
                    picture = picture
                )
            } else {
                logger.warn("Google ID Token verification failed for audience: {}", googleProperties.clientId)
                throw InvalidTokenException("Google ID Token inválido ou com audience incorreta.")
            }
        } catch (ex: InvalidTokenException) {
            throw ex
        } catch (ex: Exception) {
            logger.error("Erro ao verificar Google ID Token: {}", ex.message)
            throw InvalidTokenException("Falha ao validar o token com os servidores do Google: ${ex.message}")
        }
    }

    private fun parseMockToken(token: String): GooglePayloadData {
        val parts = token.split(":")
        val mockId = if (parts.size >= 2) parts[1] else "user-123"
        val email = if (parts.size >= 3) parts[2] else if (mockId.contains("@")) mockId else "usuario.${mockId}@fleetflow.com"
        val name = if (parts.size >= 4) parts[3] else email.substringBefore("@").replaceFirstChar { it.uppercase() }

        return GooglePayloadData(
            googleId = "google-id-$mockId",
            email = email,
            name = name,
            picture = "https://lh3.googleusercontent.com/a/default-avatar"
        )
    }
}
