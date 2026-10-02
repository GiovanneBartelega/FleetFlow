package com.fleetflow.backend.controller

import com.fleetflow.backend.dto.AuthResponse
import com.fleetflow.backend.dto.GoogleLoginRequest
import com.fleetflow.backend.dto.LogoutRequest
import com.fleetflow.backend.dto.RefreshTokenRequest
import com.fleetflow.backend.service.AuthenticationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Autenticação", description = "Endpoints para login com Google, refresh de tokens e logout")
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authenticationService: AuthenticationService
) {

    @Operation(summary = "Login com Google ID Token", description = "Valida o ID Token do Google, registra/localiza o usuário e retorna o Access Token JWT e Refresh Token.")
    @PostMapping("/google")
    fun googleLogin(
        @Valid @RequestBody request: GoogleLoginRequest
    ): ResponseEntity<AuthResponse> {
        val response = authenticationService.loginWithGoogle(request.idToken)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "Renovar Access Token", description = "Utiliza um Refresh Token válido para gerar um novo Access Token JWT e um novo Refresh Token (com rotação).")
    @PostMapping("/refresh")
    fun refreshToken(
        @Valid @RequestBody request: RefreshTokenRequest
    ): ResponseEntity<AuthResponse> {
        val response = authenticationService.refreshToken(request.refreshToken)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "Logout de Usuário", description = "Revoga o Refresh Token fornecido.")
    @PostMapping("/logout")
    fun logout(
        @Valid @RequestBody request: LogoutRequest
    ): ResponseEntity<Map<String, Any>> {
        authenticationService.logout(request.refreshToken)
        return ResponseEntity.ok(
            mapOf("success" to true, "message" to "Logout realizado com sucesso.")
        )
    }
}
