package com.fleetflow.backend.service

import com.fleetflow.backend.dto.AuthResponse
import com.fleetflow.backend.dto.UserResponse
import com.fleetflow.backend.exception.UserNotFoundException
import com.fleetflow.backend.security.GoogleTokenVerifierService
import com.fleetflow.backend.security.JwtService
import org.springframework.stereotype.Service

@Service
class AuthenticationService(
    private val googleTokenVerifierService: GoogleTokenVerifierService,
    private val userService: UserService,
    private val jwtService: JwtService,
    private val refreshTokenService: RefreshTokenService
) {

    fun loginWithGoogle(idToken: String): AuthResponse {
        val googlePayload = googleTokenVerifierService.verify(idToken)
        val user = userService.findOrCreateUser(
            googleId = googlePayload.googleId,
            email = googlePayload.email,
            name = googlePayload.name,
            photoUrl = googlePayload.picture
        )

        val accessToken = jwtService.generateAccessToken(user)
        val refreshToken = refreshTokenService.createRefreshToken(user.id)

        return AuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            tokenType = "Bearer",
            expiresIn = jwtService.getAccessTokenExpirationSeconds(),
            user = UserResponse.from(user)
        )
    }

    fun refreshToken(rawRefreshToken: String): AuthResponse {
        val userId = refreshTokenService.verifyAndRotateToken(rawRefreshToken)
        val user = userService.findUserById(userId)
            ?: throw UserNotFoundException("Usuário associado ao token não foi encontrado.")

        val newAccessToken = jwtService.generateAccessToken(user)
        val newRefreshToken = refreshTokenService.createRefreshToken(user.id)

        return AuthResponse(
            accessToken = newAccessToken,
            refreshToken = newRefreshToken,
            tokenType = "Bearer",
            expiresIn = jwtService.getAccessTokenExpirationSeconds(),
            user = UserResponse.from(user)
        )
    }

    fun logout(rawRefreshToken: String) {
        refreshTokenService.revokeToken(rawRefreshToken)
    }
}
