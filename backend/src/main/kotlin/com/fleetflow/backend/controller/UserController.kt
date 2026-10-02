package com.fleetflow.backend.controller

import com.fleetflow.backend.dto.UpdateRoleRequest
import com.fleetflow.backend.dto.UserResponse
import com.fleetflow.backend.exception.ForbiddenException
import com.fleetflow.backend.model.Role
import com.fleetflow.backend.model.User
import com.fleetflow.backend.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@Tag(name = "Usuários", description = "Endpoints para gerenciamento de usuários e perfil autenticado")
@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService
) {

    @Operation(
        summary = "Obter dados do Usuário Logado",
        description = "Retorna os detalhes do usuário autenticado a partir do token Bearer JWT enviado no cabeçalho Authorization.",
        security = [SecurityRequirement(name = "bearerAuth")]
    )
    @GetMapping("/me")
    fun getAuthenticatedUser(
        @AuthenticationPrincipal currentUser: User
    ): ResponseEntity<UserResponse> {
        return ResponseEntity.ok(UserResponse.from(currentUser))
    }

    @Operation(
        summary = "Listar Usuários",
        description = "Retorna a lista de todos os usuários cadastrados. Acessível por ADMINISTRATOR, FLEET_MANAGER e FINANCIAL.",
        security = [SecurityRequirement(name = "bearerAuth")]
    )
    @GetMapping
    fun getAllUsers(
        @AuthenticationPrincipal currentUser: User
    ): ResponseEntity<List<UserResponse>> {
        if (currentUser.role == Role.DRIVER) {
            throw ForbiddenException("Acesso negado para listar usuários.")
        }
        val users = userService.getAllUsers()
        return ResponseEntity.ok(users.map { UserResponse.from(it) })
    }

    @Operation(
        summary = "Aprovar Usuário",
        description = "Aprova um usuário pendente. Acessível por ADMINISTRATOR e FLEET_MANAGER.",
        security = [SecurityRequirement(name = "bearerAuth")]
    )
    @PatchMapping("/{id}/approve")
    fun approveUser(
        @PathVariable id: String,
        @AuthenticationPrincipal currentUser: User
    ): ResponseEntity<UserResponse> {
        val approvedUser = userService.approveUser(id, currentUser)
        return ResponseEntity.ok(UserResponse.from(approvedUser))
    }

    @Operation(
        summary = "Alterar Perfil (Role) do Usuário",
        description = "Altera o perfil de um usuário. Acessível apenas por ADMINISTRATOR.",
        security = [SecurityRequirement(name = "bearerAuth")]
    )
    @PatchMapping("/{id}/role")
    fun updateUserRole(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateRoleRequest,
        @AuthenticationPrincipal currentUser: User
    ): ResponseEntity<UserResponse> {
        val updatedUser = userService.updateUserRole(id, request.role, currentUser)
        return ResponseEntity.ok(UserResponse.from(updatedUser))
    }
}
