package com.fleetflow.mobile.data.auth

// Espelha a matriz de permissões por perfil que a API devolve em
// GET /api/auth/me (ver src/permissions.js no backend): "E" = ver e editar,
// "L" = somente leitura, ausente/null = sem acesso.
class AuthorizationManager(private val permissoes: Map<String, String?>) {

    private fun nivel(modulo: String): String? = permissoes[modulo]

    fun podeLer(modulo: String): Boolean = nivel(modulo) == "E" || nivel(modulo) == "L"

    fun podeEditar(modulo: String): Boolean = nivel(modulo) == "E"

    fun canManageUsers(): Boolean = podeEditar("usuarios")

    fun canApproveUsers(): Boolean = podeEditar("usuarios")

    fun canChangeUserRole(): Boolean = podeEditar("usuarios")
}
