package com.fleetflow.backend.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class GoogleProperties(
    @Value("\${google.client-id}")
    val clientId: String
)
