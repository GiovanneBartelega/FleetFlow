package com.fleetflow.backend.service

import com.fleetflow.backend.model.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class UserServiceTest {

    private lateinit var userService: UserService

    @BeforeEach
    fun setUp() {
        userService = UserService()
        userService.clearAll()
    }

    @Test
    fun `first user registered should receive ADMINISTRATOR role`() {
        val firstUser = userService.findOrCreateUser(
            googleId = "google-1",
            email = "admin@fleetflow.com",
            name = "First Admin",
            photoUrl = null
        )

        assertEquals(Role.ADMINISTRATOR, firstUser.role)
    }

    @Test
    fun `second user registered should NOT receive ADMINISTRATOR role`() {
        val firstUser = userService.findOrCreateUser(
            googleId = "google-1",
            email = "admin@fleetflow.com",
            name = "First Admin",
            photoUrl = null
        )

        val secondUser = userService.findOrCreateUser(
            googleId = "google-2",
            email = "driver@fleetflow.com",
            name = "Second User",
            photoUrl = null
        )

        assertEquals(Role.ADMINISTRATOR, firstUser.role)
        assertEquals(Role.DRIVER, secondUser.role)
    }

    @Test
    fun `existing user should be retrieved and updated`() {
        val originalUser = userService.findOrCreateUser(
            googleId = "google-1",
            email = "old@fleetflow.com",
            name = "Old Name",
            photoUrl = null
        )

        val updatedUser = userService.findOrCreateUser(
            googleId = "google-1",
            email = "new@fleetflow.com",
            name = "New Name",
            photoUrl = "http://photo.com"
        )

        assertEquals(originalUser.id, updatedUser.id)
        assertEquals("new@fleetflow.com", updatedUser.email)
        assertEquals("New Name", updatedUser.name)
    }

    @Test
    fun `concurrent user creations should result in exactly one ADMINISTRATOR`() {
        val numberOfThreads = 10
        val executorService = Executors.newFixedThreadPool(numberOfThreads)

        for (i in 1..numberOfThreads) {
            executorService.submit {
                userService.findOrCreateUser(
                    googleId = "google-$i",
                    email = "user$i@fleetflow.com",
                    name = "User $i",
                    photoUrl = null
                )
            }
        }

        executorService.shutdown()
        executorService.awaitTermination(5, TimeUnit.SECONDS)

        val allUsers = userService.getAllUsers()
        assertEquals(numberOfThreads, allUsers.size)

        val admins = allUsers.filter { it.role == Role.ADMINISTRATOR }
        assertEquals(1, admins.size, "Exactly one user must be ADMINISTRATOR")
    }
}
