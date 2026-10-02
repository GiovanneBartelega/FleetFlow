package com.fleetflow.backend.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fleetflow.backend.dto.GoogleLoginRequest
import com.fleetflow.backend.dto.UpdateRoleRequest
import com.fleetflow.backend.model.Role
import com.fleetflow.backend.service.UserService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

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
    fun `should get authenticated user details with valid JWT`() {
        val loginRequest = GoogleLoginRequest(idToken = "mock-id-token:user-me:pedro@fleetflow.com:Pedro Gestor")
        val loginResult = mockMvc.post("/api/auth/google") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(loginRequest)
        }.andReturn()

        val responseJson = objectMapper.readTree(loginResult.response.contentAsString)
        val accessToken = responseJson.get("accessToken").asText()

        mockMvc.get("/api/users/me") {
            header("Authorization", "Bearer $accessToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.email") { value("pedro@fleetflow.com") }
            jsonPath("$.name") { value("Pedro Gestor") }
            jsonPath("$.role") { value("ADMINISTRATOR") }
        }
    }

    @Test
    fun `should return 401 Unauthorized when Authorization header is missing`() {
        mockMvc.get("/api/users/me").andExpect {
            status { isUnauthorized() }
            jsonPath("$.success") { value(false) }
            jsonPath("$.error.code") { value("UNAUTHORIZED") }
        }
    }

    @Test
    fun `driver should receive 403 Forbidden when trying to list users`() {
        // First user: Admin
        val adminLogin = GoogleLoginRequest(idToken = "mock-id-token:admin:admin@fleetflow.com:Admin")
        mockMvc.post("/api/auth/google") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(adminLogin)
        }

        // Second user: Driver (gets DRIVER role)
        val driverLogin = GoogleLoginRequest(idToken = "mock-id-token:driver:driver@fleetflow.com:Driver")
        val driverResult = mockMvc.post("/api/auth/google") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(driverLogin)
        }.andReturn()

        val driverToken = objectMapper.readTree(driverResult.response.contentAsString).get("accessToken").asText()

        // Driver tries to GET /api/users
        mockMvc.get("/api/users") {
            header("Authorization", "Bearer $driverToken")
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.success") { value(false) }
            jsonPath("$.error.code") { value("FORBIDDEN") }
        }
    }

    @Test
    fun `admin should be able to list users, approve pending user, and update role`() {
        // 1. Admin login
        val adminResult = mockMvc.post("/api/auth/google") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(GoogleLoginRequest(idToken = "mock-id-token:admin:admin@fleetflow.com:Admin"))
        }.andReturn()
        val adminToken = objectMapper.readTree(adminResult.response.contentAsString).get("accessToken").asText()

        // 2. Second user login (PENDING / DRIVER)
        val driverResult = mockMvc.post("/api/auth/google") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(GoogleLoginRequest(idToken = "mock-id-token:driver2:driver2@fleetflow.com:Driver 2"))
        }.andReturn()
        val driverUserJson = objectMapper.readTree(driverResult.response.contentAsString).get("user")
        val driverId = driverUserJson.get("id").asText()

        // 3. Admin lists users
        mockMvc.get("/api/users") {
            header("Authorization", "Bearer $adminToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$") { isArray() }
        }

        // 4. Admin approves driver2
        mockMvc.patch("/api/users/$driverId/approve") {
            header("Authorization", "Bearer $adminToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("ACTIVE") }
        }

        // 5. Admin updates driver2 role to FINANCIAL
        val updateRoleReq = UpdateRoleRequest(role = Role.FINANCIAL)
        mockMvc.patch("/api/users/$driverId/role") {
            header("Authorization", "Bearer $adminToken")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(updateRoleReq)
        }.andExpect {
            status { isOk() }
            jsonPath("$.role") { value("FINANCIAL") }
        }
    }
}
