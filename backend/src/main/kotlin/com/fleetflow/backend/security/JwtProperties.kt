package com.fleetflow.backend.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class JwtProperties(
    @Value("\${jwt.secret}")
    val secret: String,

    @Value("\${jwt.access-token-expiration:900}")
    val accessTokenExpirationSeconds: Long,

    @Value("\${jwt.refresh-token-expiration:604800}")
    val refreshTokenExpirationSeconds: Long
)
