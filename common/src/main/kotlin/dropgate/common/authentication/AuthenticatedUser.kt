package dropgate.common.authentication

import dropgate.common.error.DropgateException
import dropgate.common.error.ErrorCode
import java.util.UUID

data class AuthenticatedUser(val id: UUID, val role: Role) {
    fun requireAdmin() {
        if (role != Role.ADMIN) throw DropgateException(ErrorCode.FORBIDDEN, "ADMIN 역할이 필요합니다: 사용자 $id 역할 $role")
    }

    companion object {
        val REQUEST_ATTRIBUTE: String = AuthenticatedUser::class.java.name
    }
}
