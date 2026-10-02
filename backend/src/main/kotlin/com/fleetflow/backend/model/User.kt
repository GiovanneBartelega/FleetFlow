package com.fleetflow.backend.model

import java.time.Instant
import java.util.UUID

data class User(
    val id: String = UUID.randomUUID().toString(),
    val googleId: String,
    val name: String,
    val email: String,
    val photoUrl: String? = null,
    val role: Role,
    val status: UserStatus,
    val createdAt: Instant = Instant.now()
)
