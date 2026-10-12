package com.fleetflow.backend.service

import com.fleetflow.backend.exception.InvalidTokenException
import com.fleetflow.backend.exception.TokenRevokedException
import com.fleetflow.backend.model.RefreshToken
import com.fleetflow.backend.security.JwtProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class RefreshTokenService(
    private val jwtProperties: JwtProperties
) {
    private val logger = LoggerFactory.getLogger(RefreshTokenService::class.java)

    // Store hashed refresh tokens in memory for security
    private val tokensByHash = ConcurrentHashMap<String, RefreshToken>()

    fun createRefreshToken(userId: String): String {
        val rawToken = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString()
        val tokenHash = hashToken(rawToken)
        val expiresAt = Instant.now().plusSeconds(jwtProperties.refreshTokenExpirationSeconds)

        val refreshToken = RefreshToken(
            id = UUID.randomUUID().toString(),
            tokenHash = tokenHash,
            userId = userId,
            expiresAt = expiresAt,
            createdAt = Instant.now(),
            revoked = false
        )

        tokensByHash[tokenHash] = refreshToken
        logger.info("Created refresh token ID: {} for user ID: {}", refreshToken.id, userId)
        return rawToken
    }

    fun verifyAndRotateToken(rawToken: String): String {
        val tokenHash = hashToken(rawToken)
        val storedToken = tokensByHash[tokenHash]
            ?: throw InvalidTokenException("Refresh token não encontrado ou inválido.")

        if (storedToken.revoked) {
            logger.warn("Attempted reuse of revoked refresh token ID: {}", storedToken.id)
            throw TokenRevokedException("Refresh token foi revogado ou já utilizado.")
        }

        if (storedToken.expiresAt.isBefore(Instant.now())) {
            logger.warn("Attempted use of expired refresh token ID: {}", storedToken.id)
            throw InvalidTokenException("Refresh token expirado.")
        }

        // Revoke the old token (Rotation)
        tokensByHash[tokenHash] = storedToken.copy(revoked = true)
        logger.info("Revoked refresh token ID: {} due to rotation", storedToken.id)

        return storedToken.userId
    }

    fun revokeToken(rawToken: String) {
        val tokenHash = hashToken(rawToken)
        val storedToken = tokensByHash[tokenHash]
        if (storedToken != null && !storedToken.revoked) {
            tokensByHash[tokenHash] = storedToken.copy(revoked = true)
            logger.info("Successfully revoked refresh token ID: {}", storedToken.id)
        }
    }

    private fun hashToken(rawToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(rawToken.toByteArray(StandardCharsets.UTF_8))
        return HexFormat.of().formatHex(hashBytes)
    }

    fun clearAll() {
        tokensByHash.clear()
    }
}
