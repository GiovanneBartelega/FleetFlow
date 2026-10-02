package com.fleetflow.backend.dto

data class ErrorResponse(
    val success: Boolean = false,
    val error: ErrorDetails,
    val traceId: String
)

data class ErrorDetails(
    val code: String,
    val message: String,
    val details: Map<String, List<String>>? = null
)
