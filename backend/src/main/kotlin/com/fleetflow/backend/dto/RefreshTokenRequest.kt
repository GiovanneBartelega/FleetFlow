package com.fleetflow.backend.dto

import jakarta.validation.constraints.NotBlank

data class RefreshTokenRequest(
    @field:NotBlank(message = "O refreshToken é obrigatório.")
    val refreshToken: String
)
