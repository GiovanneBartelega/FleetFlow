package com.fleetflow.backend.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fleetflow.backend.dto.GoogleLoginRequest
import com.fleetflow.backend.service.UserService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var userService: UserService

    @BeforeEach
    fun setUp() {
        userService.clearAll()
    }

    @Test
    fun `should perform Google login and return JWT tokens and user details`() {
        val request = GoogleLoginRequest(idToken = "mock-id-token:user-1:user@fleetflow.com:João Admin")

        val resultAction = mockMvc.post("/api/auth/google") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }

        resultAction.andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { exists() }
            jsonPath("$.refreshToken") { exists() }
            jsonPath("$.tokenType") { value("Bearer") }
            jsonPath("$.user.role") { value("ADMINISTRATOR") }
            jsonPath("$.user.email") { value("user@fleetflow.com") }
            jsonPath("$.user.name") { value("João Admin") }
        }
    }

    @Test
    fun `should fail validation when idToken is blank`() {
        val request = GoogleLoginRequest(idToken = "")

        mockMvc.post("/api/auth/google") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.success") { value(false) }
            jsonPath("$.error.code") { value("VALIDATION_ERROR") }
            jsonPath("$.error.details.idToken") { exists() }
        }
    }
}
