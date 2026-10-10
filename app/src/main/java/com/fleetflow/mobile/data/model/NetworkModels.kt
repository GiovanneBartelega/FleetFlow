package com.fleetflow.mobile.data.model

data class GoogleLoginRequestDto(
    val idToken: String
)

data class DevLoginRequestDto(
    val email: String
)

data class UpdateUsuarioRequestDto(
    val perfilId: String? = null,
    val status: String? = null,
    val syncVersion: Int? = null
)

data class PerfilDto(
    val id: String,
    val nome: String
)

data class AuthResponseDto(
    val token: String,
    val tokenType: String,
    val expiresIn: String,
    val usuario: UserResponseDto,
    val permissoes: Map<String, String?>
)

data class MeResponseDto(
    val usuario: UserResponseDto,
    val permissoes: Map<String, String?>
)

data class UserResponseDto(
    val id: String,
    val nome: String,
    val email: String,
    val fotoUrl: String?,
    val perfil: PerfilDto,
    val status: String,
    val categoriaCnh: String?,
    val validadeCnh: String?
)

data class UsuariosPageDto(
    val data: List<UserResponseDto>,
    val page: Int,
    val pageSize: Int,
    val total: Int,
    val totalPages: Int
)

data class PerfisResponseDto(
    val data: List<PerfilDto>
)

data class ErrorResponseDto(
    val error: ErrorDetailsDto?
)

data class ErrorDetailsDto(
    val code: String,
    val message: String,
    val details: List<Map<String, String>>?
)
