package dropgate.common.error

import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

class GlobalExceptionHandlerTest {
    private val mvc = MockMvcBuilders.standaloneSetup(FailingController()).setControllerAdvice(GlobalExceptionHandler()).build()

    @Test
    fun `예외의 오류 코드를 상태와 오류 응답 본문으로 바꾼다`() {
        mvc.get("/failures/FORBIDDEN").andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("FORBIDDEN") }
            jsonPath("$.message") { value(ErrorCode.FORBIDDEN.message) }
            jsonPath("$.errors") { isEmpty() }
            header { doesNotExist(HttpHeaders.RETRY_AFTER) }
        }
    }

    @Test
    fun `503 오류 응답에는 Retry-After 2초를 붙인다`() {
        mvc.get("/failures/STOCK_UNAVAILABLE").andExpect {
            status { isServiceUnavailable() }
            header { string(HttpHeaders.RETRY_AFTER, "2") }
        }
    }

    @Test
    fun `필드 오류를 errors에 담는다`() {
        mvc.get("/failures/VALIDATION_FAILED").andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[0].field") { value("name") }
            jsonPath("$.errors[0].message") { value("이름은 100자 이하여야 합니다") }
        }
    }

    @RestController
    private class FailingController {
        @GetMapping("/failures/{code}")
        fun fail(@PathVariable("code") code: ErrorCode): Unit = throw DropgateException(code, "테스트 실패", fieldErrorsOf(code))

        private fun fieldErrorsOf(code: ErrorCode) = if (code == ErrorCode.VALIDATION_FAILED) listOf(FieldErrorResponse("name", "이름은 100자 이하여야 합니다")) else emptyList()
    }
}
