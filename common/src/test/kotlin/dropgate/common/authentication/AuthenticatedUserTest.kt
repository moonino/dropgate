package dropgate.common.authentication

import dropgate.common.error.DropgateException
import dropgate.common.error.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class AuthenticatedUserTest {
    @Test
    fun `ADMIN이 아니면 FORBIDDEN으로 거부한다`() {
        val exception = assertThrows<DropgateException> { AuthenticatedUser(UUID.randomUUID(), Role.USER).requireAdmin() }
        assertThat(exception.errorCode).isEqualTo(ErrorCode.FORBIDDEN)
    }

    @Test
    fun `ADMIN이면 통과한다`() {
        assertDoesNotThrow { AuthenticatedUser(UUID.randomUUID(), Role.ADMIN).requireAdmin() }
    }
}
