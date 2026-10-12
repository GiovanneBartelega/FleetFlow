package com.fleetflow.backend.security

import com.fleetflow.backend.exception.InvalidTokenException
import com.fleetflow.backend.model.Role
import com.fleetflow.backend.model.User
import com.fleetflow.backend.model.UserStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class JwtServiceTest {

    private lateinit var jwtService: JwtService

    @BeforeEach
    fun setUp() {
        val properties = JwtProperties(
            secret = "secretKeySecretKeySecretKeySecretKey123456",
            accessTokenExpirationSeconds = 900,
            refreshTokenExpirationSeconds = 604800
        )
        jwtService = JwtService(properties)
    }

    @Test
    fun `should generate valid JWT access token and extract user ID`() {
        val user = User(
            id = UUID.randomUUID().toString(),
            googleId = "google-123",
            name = "João Silva",
            email = "joao@fleetflow.com",
            photoUrl = null,
            role = Role.ADMINISTRATOR,
            status = UserStatus.ACTIVE
        )

        val token = jwtService.generateAccessToken(user)
        assertNotNull(token)

        val extractedUserId = jwtService.getUserIdFromToken(token)
        assertEquals(user.id, extractedUserId)

        val claims = jwtService.validateAndExtractClaims(token)
        assertEquals("joao@fleetflow.com", claims["email"])
        assertEquals("ADMINISTRATOR", claims["role"])
    }

    @Test
    fun `should throw InvalidTokenException for malformed token`() {
        assertThrows<InvalidTokenException> {
            jwtService.validateAndExtractClaims("invalid.token.string")
        }
    }
}
