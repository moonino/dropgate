package dropgate.common.authentication

import dropgate.common.error.ErrorCode
import dropgate.common.error.assertThrowsDropgateError
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.util.UUID

class AuthenticatedUserTest {
    @Test
    fun `ADMIN이 아니면 FORBIDDEN으로 거부한다`() {
        assertThrowsDropgateError(ErrorCode.FORBIDDEN) { AuthenticatedUser(UUID.randomUUID(), Role.USER).requireAdmin() }
    }

    @Test
    fun `ADMIN이면 통과한다`() {
        assertDoesNotThrow { AuthenticatedUser(UUID.randomUUID(), Role.ADMIN).requireAdmin() }
    }
}
