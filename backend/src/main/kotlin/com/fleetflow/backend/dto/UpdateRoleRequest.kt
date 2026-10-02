package com.fleetflow.backend.dto

import com.fleetflow.backend.model.Role
import jakarta.validation.constraints.NotNull

data class UpdateRoleRequest(
    @field:NotNull(message = "O perfil (role) é obrigatório.")
    val role: Role
)
