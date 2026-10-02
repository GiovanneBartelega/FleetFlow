package com.fleetflow.backend.service

import com.fleetflow.backend.exception.FleetFlowException
import com.fleetflow.backend.exception.ForbiddenException
import com.fleetflow.backend.exception.UserNotFoundException
import com.fleetflow.backend.model.Role
import com.fleetflow.backend.model.User
import com.fleetflow.backend.model.UserStatus
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class UserService {

    private val logger = LoggerFactory.getLogger(UserService::class.java)

    // In-memory storage for users
    private val usersById = ConcurrentHashMap<String, User>()
    private val userIdByGoogleId = ConcurrentHashMap<String, String>()

    fun findOrCreateUser(googleId: String, email: String, name: String, photoUrl: String?): User {
        val existingUserId = userIdByGoogleId[googleId]
        if (existingUserId != null) {
            val existingUser = usersById[existingUserId]
            if (existingUser != null) {
                // Update profile info if changed
                val updatedUser = existingUser.copy(
                    name = name,
                    email = email,
                    photoUrl = photoUrl ?: existingUser.photoUrl
                )
                usersById[existingUserId] = updatedUser
                return updatedUser
            }
        }

        // Thread-safe creation & First User Administrator Rule
        synchronized(this) {
            // Double check inside lock
            val doubleCheckUserId = userIdByGoogleId[googleId]
            if (doubleCheckUserId != null) {
                usersById[doubleCheckUserId]?.let { return it }
            }

            val isFirstUser = usersById.isEmpty()
            val assignedRole = if (isFirstUser) Role.ADMINISTRATOR else Role.DRIVER
            // First user is active immediately, subsequent users start as PENDING for approval
            val assignedStatus = if (isFirstUser) UserStatus.ACTIVE else UserStatus.PENDING

            val newUser = User(
                id = UUID.randomUUID().toString(),
                googleId = googleId,
                name = name,
                email = email,
                photoUrl = photoUrl,
                role = assignedRole,
                status = assignedStatus,
                createdAt = Instant.now()
            )

            usersById[newUser.id] = newUser
            userIdByGoogleId[googleId] = newUser.id

            logger.info(
                "Created new user ID: {}, email: {}, role: {}, status: {} (isFirstUser: {})",
                newUser.id, newUser.email, newUser.role, newUser.status, isFirstUser
            )

            return newUser
        }
    }

    fun findUserById(id: String): User? {
        return usersById[id]
    }

    fun findUserByGoogleId(googleId: String): User? {
        val userId = userIdByGoogleId[googleId] ?: return null
        return usersById[userId]
    }

    fun getAllUsers(): List<User> {
        return usersById.values.sortedByDescending { it.createdAt }
    }

    fun approveUser(targetUserId: String, operator: User): User {
        // Authorization: Only ADMINISTRATOR and FLEET_MANAGER can approve users
        if (operator.role != Role.ADMINISTRATOR && operator.role != Role.FLEET_MANAGER) {
            throw ForbiddenException("Apenas Administradores e Gestores de Frota podem aprovar usuários.")
        }

        val targetUser = usersById[targetUserId]
            ?: throw UserNotFoundException("Usuário não encontrado.")

        if (targetUser.status != UserStatus.PENDING) {
            throw FleetFlowException("USER_NOT_PENDING", "Apenas usuários com status PENDENTE podem ser aprovados.", HttpStatus.BAD_REQUEST)
        }

        val updatedUser = targetUser.copy(status = UserStatus.ACTIVE)
        usersById[targetUserId] = updatedUser
        logger.info("User ID: {} approved by operator ID: {}", targetUserId, operator.id)
        return updatedUser
    }

    fun updateUserRole(targetUserId: String, newRole: Role, operator: User): User {
        // Authorization: Only ADMINISTRATOR can change user roles
        if (operator.role != Role.ADMINISTRATOR) {
            throw ForbiddenException("Apenas Administradores podem alterar o perfil (role) de usuários.")
        }

        val targetUser = usersById[targetUserId]
            ?: throw UserNotFoundException("Usuário não encontrado.")

        val updatedUser = targetUser.copy(role = newRole)
        usersById[targetUserId] = updatedUser
        logger.info("User ID: {} role updated to: {} by operator ID: {}", targetUserId, newRole, operator.id)
        return updatedUser
    }

    fun clearAll() {
        usersById.clear()
        userIdByGoogleId.clear()
    }
}
