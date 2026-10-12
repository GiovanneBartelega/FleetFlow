package com.fleetflow.backend.dto

import com.fleetflow.backend.model.Role
import com.fleetflow.backend.model.User
import com.fleetflow.backend.model.UserStatus

data class UserResponse(
    val id: String,
    val googleId: String,
    val name: String,
    val email: String,
    val photoUrl: String?,
    val role: Role,
    val status: UserStatus
) {
    companion object {
        fun from(user: User): UserResponse = UserResponse(
            id = user.id,
            googleId = user.googleId,
            name = user.name,
            email = user.email,
            photoUrl = user.photoUrl,
            role = user.role,
            status = user.status
        )
    }
}
