package dropgate.auth.dto

import java.util.UUID

data class UserResponse(val id: UUID, val nickname: String, val role: String)
