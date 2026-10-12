package com.fleetflow.backend.model

import java.time.Instant
import java.util.UUID

data class RefreshToken(
    val id: String = UUID.randomUUID().toString(),
    val tokenHash: String,
    val userId: String,
    val expiresAt: Instant,
    val createdAt: Instant = Instant.now(),
    val revoked: Boolean = false
)
