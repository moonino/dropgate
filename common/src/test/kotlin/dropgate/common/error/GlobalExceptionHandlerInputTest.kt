package dropgate.common.error

import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

private const val NAME_TOO_LONG_MESSAGE = "이름은 3자 이하여야 합니다"
private const val LIMIT_TOO_SMALL_MESSAGE = "limit은 1 이상이어야 합니다"

class GlobalExceptionHandlerInputTest {
    private val mvc = MockMvcBuilders.standaloneSetup(InputController()).setControllerAdvice(GlobalExceptionHandler()).build()

    @Test
    fun `필수 쿼리 파라미터가 없으면 그 이름을 errors에 담아 VALIDATION_FAILED로 답한다`() {
        mvc.get("/inputs/orders").andExpectValidationFailure("limit")
    }

    @Test
    fun `경로 변수 형식이 맞지 않으면 그 이름을 errors에 담아 VALIDATION_FAILED로 답한다`() {
        mvc.get("/inputs/drops/not-a-uuid").andExpectValidationFailure("dropId")
    }

    @Test
    fun `파라미터 제약을 어기면 제약 메시지를 errors에 담아 VALIDATION_FAILED로 답한다`() {
        mvc.get("/inputs/orders?limit=0").andExpectValidationFailure("limit").andExpect {
            jsonPath("$.errors[0].message") { value(LIMIT_TOO_SMALL_MESSAGE) }
        }
    }

    @Test
    fun `본문 필드 검증에 실패하면 필드와 제약 메시지를 errors에 담아 VALIDATION_FAILED로 답한다`() {
        postDrop("""{"name":"너무 긴 이름","quantity":1}""").andExpectValidationFailure("name").andExpect {
            jsonPath("$.errors[0].message") { value(NAME_TOO_LONG_MESSAGE) }
        }
    }

    @Test
    fun `본문 필드 타입이 맞지 않으면 그 필드를 errors에 담아 VALIDATION_FAILED로 답한다`() {
        postDrop("""{"name":"한정판","quantity":"many"}""").andExpectValidationFailure("quantity")
    }

    @Test
    fun `본문이 JSON이 아니면 body를 errors에 담아 VALIDATION_FAILED로 답한다`() {
        postDrop("{").andExpectValidationFailure("body")
    }

    private fun postDrop(body: String): ResultActionsDsl = mvc.post("/inputs/drops") {
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    private fun ResultActionsDsl.andExpectValidationFailure(field: String): ResultActionsDsl = andExpect {
        status { isBadRequest() }
        jsonPath("$.code") { value(ErrorCode.VALIDATION_FAILED.name) }
        jsonPath("$.message") { value(ErrorCode.VALIDATION_FAILED.message) }
        jsonPath("$.errors[0].field") { value(field) }
    }

    @RestController
    private class InputController {
        @GetMapping("/inputs/orders")
        fun readOrders(@RequestParam("limit") @Min(1, message = LIMIT_TOO_SMALL_MESSAGE) limit: Int): Int = limit

        @GetMapping("/inputs/drops/{dropId}")
        fun readDrop(@PathVariable("dropId") dropId: UUID): UUID = dropId

        @PostMapping("/inputs/drops")
        fun createDrop(@RequestBody @Valid request: DropInput): String = request.name
    }

    private data class DropInput(@field:Size(max = 3, message = NAME_TOO_LONG_MESSAGE) val name: String, val quantity: Int)
}
