package com.fleetflow.backend.exception

import org.springframework.http.HttpStatus

open class FleetFlowException(
    val code: String,
    override val message: String,
    val status: HttpStatus = HttpStatus.BAD_REQUEST
) : RuntimeException(message)

class InvalidTokenException(
    message: String = "Token inválido ou expirado."
) : FleetFlowException("INVALID_TOKEN", message, HttpStatus.UNAUTHORIZED)

class TokenRevokedException(
    message: String = "Refresh token foi revogado ou já utilizado."
) : FleetFlowException("TOKEN_REVOKED", message, HttpStatus.UNAUTHORIZED)

class UserNotFoundException(
    message: String = "Usuário não encontrado."
) : FleetFlowException("USER_NOT_FOUND", message, HttpStatus.NOT_FOUND)

class UnauthorizedException(
    message: String = "Acesso não autorizado."
) : FleetFlowException("UNAUTHORIZED", message, HttpStatus.UNAUTHORIZED)

class ForbiddenException(
    message: String = "Acesso negado para o recurso solicitado."
) : FleetFlowException("FORBIDDEN", message, HttpStatus.FORBIDDEN)
