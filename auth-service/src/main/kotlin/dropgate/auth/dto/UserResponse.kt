package dropgate.auth.dto

import dropgate.auth.entity.UserEntity
import java.util.UUID

data class UserResponse(val id: UUID, val nickname: String, val role: String) {
    companion object {
        fun createFrom(user: UserEntity): UserResponse = UserResponse(user.id, user.nickname, user.role)
    }
}
