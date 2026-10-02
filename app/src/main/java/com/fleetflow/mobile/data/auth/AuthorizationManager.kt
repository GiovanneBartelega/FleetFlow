package com.fleetflow.mobile.data.auth

class AuthorizationManager(private val role: String?) {

    fun canManageUsers(): Boolean {
        val upperRole = role?.uppercase() ?: return false
        return upperRole == "ADMINISTRATOR" || upperRole == "FLEET_MANAGER" || upperRole == "FINANCIAL"
    }

    fun canApproveUsers(): Boolean {
        val upperRole = role?.uppercase() ?: return false
        return upperRole == "ADMINISTRATOR" || upperRole == "FLEET_MANAGER"
    }

    fun canChangeUserRole(): Boolean {
        val upperRole = role?.uppercase() ?: return false
        return upperRole == "ADMINISTRATOR"
    }
}
