# Implementation Plan - Authorization and User Management

Implement comprehensive role-based access control (RBAC) across both backend (Spring Boot) and Android (Jetpack Compose), supporting four roles (`ADMINISTRATOR`, `FLEET_MANAGER`, `FINANCIAL`, `DRIVER`) and user status management (`ACTIVE`, `INACTIVE`, `PENDING`).

## User Review Required

> [!IMPORTANT]
> - Backend enforces strict authorization checks returning standardized HTTP 403 (`ForbiddenException`) for unauthorized actions.
> - Android centrally handles HTTP 403 without triggering token refresh or logout.
> - Real API replaces mock user data in `UsuariosScreen`.

## Open Questions

- None. All requirements align with existing architecture.

## Proposed Changes

### Backend

#### [MODIFY] [UserService.kt](file:///C:/Users/Bruna/Documents/FleetFlow-main/backend/src/main/kotlin/com/fleetflow/backend/service/UserService.kt)
- Add methods:
  - `getAllUsers(): List<User>`
  - `approveUser(id: String): User`
  - `updateUserRole(id: String, newRole: Role): User`
- Ensure validation for pending status and existing users.

#### [MODIFY] [UserController.kt](file:///C:/Users/Bruna/Documents/FleetFlow-main/backend/src/main/kotlin/com/fleetflow/backend/controller/UserController.kt)
- Add endpoints:
  - `GET /api/users` (Accessible by `ADMINISTRATOR`, `FLEET_MANAGER`, `FINANCIAL`)
  - `PATCH /api/users/{id}/approve` (Accessible by `ADMINISTRATOR`, `FLEET_MANAGER`)
  - `PATCH /api/users/{id}/role` (Accessible by `ADMINISTRATOR`)
- Add method security or service-layer authorization checks throwing `ForbiddenException` (HTTP 403) with standardized error format.

#### [NEW] [Exceptions.kt additions](file:///C:/Users/Bruna/Documents/FleetFlow-main/backend/src/main/kotlin/com/fleetflow/backend/exception/Exceptions.kt) / [GlobalExceptionHandler.kt](file:///C:/Users/Bruna/Documents/FleetFlow-main/backend/src/main/kotlin/com/fleetflow/backend/exception/GlobalExceptionHandler.kt)
- Ensure `ForbiddenException` maps correctly to HTTP 403 with standardized `ErrorResponse`.

### Android

#### [NEW] [AuthorizationManager.kt](file:///C:/Users/Bruna/Documents/FleetFlow-main/app/src/main/java/com/fleetflow/mobile/data/auth/AuthorizationManager.kt)
- Centralized permission policy checking (`canManageUsers`, `canApproveUsers`, `canChangeUserRole`).

#### [MODIFY] [UserApi.kt](file:///C:/Users/Bruna/Documents/FleetFlow-main/app/src/main/java/com/fleetflow/mobile/data/api/UserApi.kt)
- Add endpoints for listing users, approving users, and updating roles.

#### [MODIFY] [AuthRepository.kt](file:///C:/Users/Bruna/Documents/FleetFlow-main/app/src/main/java/com/fleetflow/mobile/data/repository/AuthRepository.kt) / [UserRepository.kt](file:///C:/Users/Bruna/Documents/FleetFlow-main/app/src/main/java/com/fleetflow/mobile/data/repository/UserRepository.kt)
- Implement user management repository methods with robust error handling (especially handling HTTP 403 without triggering refresh or logout).

#### [MODIFY] [UsuariosScreen.kt](file:///C:/Users/Bruna/Documents/FleetFlow-main/app/src/main/java/ui/screens/UsuariosScreen.kt)
- Connect to backend user management API, display loading, empty states, error handling, approval actions, and role changes based on user permissions.

## Verification Plan

### Automated Tests
- Backend unit and integration tests (`UserServiceTest`, `UserControllerTest`, `AuthControllerTest`) covering all roles, 403 responses, approvals, and role updates.

### Manual Verification
- Verify Android UI respects user roles and handles HTTP 403 responses correctly.
