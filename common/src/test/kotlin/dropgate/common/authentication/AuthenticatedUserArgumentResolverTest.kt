package dropgate.common.authentication

import dropgate.common.error.GlobalExceptionHandler
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

class AuthenticatedUserArgumentResolverTest {
    private val mvc = MockMvcBuilders.standaloneSetup(CurrentUserController())
        .setControllerAdvice(GlobalExceptionHandler())
        .setCustomArgumentResolvers(AuthenticatedUserArgumentResolver())
        .build()

    @Test
    fun `필터가 담은 인증된 사용자를 컨트롤러 인자로 넘긴다`() {
        val user = AuthenticatedUser(UUID.randomUUID(), Role.ADMIN)
        mvc.get("/me") { requestAttr(AuthenticatedUser.REQUEST_ATTRIBUTE, user) }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(user.id.toString()) }
        }
    }

    @Test
    fun `인증된 사용자가 없는데 인자로 요구하면 401 UNAUTHENTICATED로 거부한다`() {
        mvc.get("/me").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("UNAUTHENTICATED") }
        }
    }

    @RestController
    private class CurrentUserController {
        @GetMapping("/me")
        fun getCurrentUser(user: AuthenticatedUser): Map<String, String> = mapOf("id" to user.id.toString())
    }
}
