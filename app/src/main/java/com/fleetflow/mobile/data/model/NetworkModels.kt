package com.fleetflow.mobile.data.model

data class GoogleLoginRequestDto(
    val idToken: String
)

data class RefreshTokenRequestDto(
    val refreshToken: String
)

data class LogoutRequestDto(
    val refreshToken: String
)

data class UpdateRoleRequestDto(
    val role: String
)

data class AuthResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val user: UserResponseDto
)

data class UserResponseDto(
    val id: String,
    val googleId: String,
    val name: String,
    val email: String,
    val photoUrl: String?,
    val role: String,
    val status: String
)

data class ErrorResponseDto(
    val success: Boolean,
    val error: ErrorDetailsDto?,
    val traceId: String?
)

data class ErrorDetailsDto(
    val code: String,
    val message: String,
    val details: Map<String, List<String>>?
)
