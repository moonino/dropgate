package dropgate.common.authentication

import dropgate.common.error.GlobalExceptionHandler
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

class AdminAuthorizationInterceptorTest {
    private val mvc = MockMvcBuilders.standaloneSetup(AdminController())
        .setControllerAdvice(GlobalExceptionHandler())
        .addMappedInterceptors(arrayOf("/admin/**"), AdminAuthorizationInterceptor())
        .build()

    @Test
    fun `ADMIN 사용자의 요청은 관리자 핸들러까지 간다`() {
        postAsUser(Role.ADMIN, """{"name":"드롭"}""").andExpect { status { isOk() } }
    }

    @Test
    fun `USER 사용자는 본문 검증보다 먼저 403 FORBIDDEN으로 거부한다`() {
        postAsUser(Role.USER, """{"name":""}""").andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("FORBIDDEN") }
        }
    }

    @Test
    fun `인증된 사용자가 없으면 401 UNAUTHENTICATED로 거부한다`() {
        mvc.post("/admin/drops") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"드롭"}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("UNAUTHENTICATED") }
        }
    }

    private fun postAsUser(role: Role, body: String): ResultActionsDsl = mvc.post("/admin/drops") {
        requestAttr(AuthenticatedUser.REQUEST_ATTRIBUTE, AuthenticatedUser(UUID.randomUUID(), role))
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    @RestController
    private class AdminController {
        @PostMapping("/admin/drops")
        fun createDrop(@RequestBody @Valid request: AdminInput): String = request.name
    }

    private data class AdminInput(@field:NotBlank val name: String)
}
