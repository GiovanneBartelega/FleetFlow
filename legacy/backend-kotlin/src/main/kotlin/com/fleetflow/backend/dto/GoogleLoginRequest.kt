package com.fleetflow.backend.dto

import jakarta.validation.constraints.NotBlank

data class GoogleLoginRequest(
    @field:NotBlank(message = "O idToken é obrigatório.")
    val idToken: String
)
