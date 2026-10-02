package com.fleetflow.backend.service

import com.fleetflow.backend.exception.InvalidTokenException
import com.fleetflow.backend.exception.TokenRevokedException
import com.fleetflow.backend.security.JwtProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RefreshTokenServiceTest {

    private lateinit var refreshTokenService: RefreshTokenService

    @BeforeEach
    fun setUp() {
        val jwtProperties = JwtProperties(
            secret = "secretKeySecretKeySecretKeySecretKey123456",
            accessTokenExpirationSeconds = 900,
            refreshTokenExpirationSeconds = 604800
        )
        refreshTokenService = RefreshTokenService(jwtProperties)
    }

    @Test
    fun `should create, verify and rotate refresh token`() {
        val userId = "user-uuid-123"
        val rawToken = refreshTokenService.createRefreshToken(userId)
        assertNotNull(rawToken)

        val verifiedUserId = refreshTokenService.verifyAndRotateToken(rawToken)
        assertEquals(userId, verifiedUserId)

        // Attempting to reuse rotated token should throw TokenRevokedException
        assertThrows<TokenRevokedException> {
            refreshTokenService.verifyAndRotateToken(rawToken)
        }
    }

    @Test
    fun `should revoke refresh token`() {
        val userId = "user-uuid-456"
        val rawToken = refreshTokenService.createRefreshToken(userId)

        refreshTokenService.revokeToken(rawToken)

        assertThrows<TokenRevokedException> {
            refreshTokenService.verifyAndRotateToken(rawToken)
        }
    }

    @Test
    fun `should throw InvalidTokenException for non-existent token`() {
        assertThrows<InvalidTokenException> {
            refreshTokenService.verifyAndRotateToken("non-existent-refresh-token")
        }
    }
}
