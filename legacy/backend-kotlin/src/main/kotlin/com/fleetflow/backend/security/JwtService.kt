package com.fleetflow.backend.security

import com.fleetflow.backend.exception.InvalidTokenException
import com.fleetflow.backend.model.User
import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Service
class JwtService(
    private val jwtProperties: JwtProperties
) {

    private val secretKey: SecretKey by lazy {
        val secretBytes = jwtProperties.secret.toByteArray(StandardCharsets.UTF_8)
        Keys.hmacShaKeyFor(secretBytes)
    }

    fun generateAccessToken(user: User): String {
        val now = Date()
        val expiryDate = Date(now.time + jwtProperties.accessTokenExpirationSeconds * 1000)

        return Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(user.id)
            .claim("email", user.email)
            .claim("role", user.role.name)
            .claim("googleId", user.googleId)
            .claim("name", user.name)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(secretKey)
            .compact()
    }

    fun validateAndExtractClaims(token: String): Claims {
        return try {
            Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (ex: JwtException) {
            throw InvalidTokenException("Token JWT inválido ou expirado.")
        } catch (ex: IllegalArgumentException) {
            throw InvalidTokenException("Formato de token JWT inválido.")
        }
    }

    fun getUserIdFromToken(token: String): String {
        return validateAndExtractClaims(token).subject
    }

    fun getAccessTokenExpirationSeconds(): Long {
        return jwtProperties.accessTokenExpirationSeconds
    }
}
