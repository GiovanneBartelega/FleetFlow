package com.fleetflow.backend.exception

import com.fleetflow.backend.dto.ErrorDetails
import com.fleetflow.backend.dto.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.UUID

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(FleetFlowException::class)
    fun handleFleetFlowException(ex: FleetFlowException): ResponseEntity<ErrorResponse> {
        val traceId = UUID.randomUUID().toString()
        logger.warn("FleetFlowException [{}]: {}", traceId, ex.message)
        val response = ErrorResponse(
            success = false,
            error = ErrorDetails(code = ex.code, message = ex.message),
            traceId = traceId
        )
        return ResponseEntity.status(ex.status).body(response)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val traceId = UUID.randomUUID().toString()
        val fieldErrors = mutableMapOf<String, MutableList<String>>()

        ex.bindingResult.fieldErrors.forEach { fieldError ->
            val fieldName = fieldError.field
            val errorMessage = fieldError.defaultMessage ?: "Valor inválido"
            fieldErrors.computeIfAbsent(fieldName) { mutableListOf() }.add(errorMessage)
        }

        val response = ErrorResponse(
            success = false,
            error = ErrorDetails(
                code = "VALIDATION_ERROR",
                message = "Existem dados inválidos.",
                details = fieldErrors
            ),
            traceId = traceId
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
        val traceId = UUID.randomUUID().toString()
        logger.error("Internal Server Error [{}]: ", traceId, ex)
        val response = ErrorResponse(
            success = false,
            error = ErrorDetails(
                code = "INTERNAL_SERVER_ERROR",
                message = "Ocorreu um erro interno no servidor."
            ),
            traceId = traceId
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response)
    }
}
