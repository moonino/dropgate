package dropgate.common.error

import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
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
private const val PAGE_SIZE_TOO_SMALL_MESSAGE = "page_size는 1 이상이어야 합니다"

class GlobalExceptionHandlerInputTest {
    private val mvc = MockMvcBuilders.standaloneSetup(InputController()).setControllerAdvice(GlobalExceptionHandler()).build()

    @Test
    fun `필수 쿼리 파라미터가 없으면 그 이름을 errors에 담아 VALIDATION_FAILED로 답한다`() {
        mvc.get("/inputs/orders").expectValidationFailure("page_size")
    }

    @Test
    fun `쿼리 파라미터 형식이 맞지 않으면 요청 이름을 errors에 담는다`() {
        mvc.get("/inputs/orders?page_size=invalid").expectValidationFailure("page_size")
    }

    @Test
    fun `경로 변수 형식이 맞지 않으면 그 이름을 errors에 담아 VALIDATION_FAILED로 답한다`() {
        mvc.get("/inputs/drops/not-a-uuid").expectValidationFailure("item_id")
    }

    @ParameterizedTest
    @CsvSource(
        "/inputs/orders?page_size=0, page_size",
        "/inputs/named-orders?page_size=0, page_size",
        "/inputs/default-orders?pageSize=0, pageSize",
        "/inputs/items/0, item_id",
        "/inputs/named-items/0, item_id",
        "/inputs/default-items/0, itemId",
    )
    fun `제약 위반 필드는 요청에 선언한 이름을 따른다`(uri: String, field: String) {
        mvc.get(uri).expectValidationFailure(field)
    }

    @Test
    fun `파라미터 제약 위반 메시지를 errors에 담는다`() {
        mvc.get("/inputs/orders?page_size=0").andExpect {
            jsonPath("$.errors[0].message") { value(PAGE_SIZE_TOO_SMALL_MESSAGE) }
        }
    }

    @Test
    fun `본문 필드 검증에 실패하면 필드와 제약 메시지를 errors에 담아 VALIDATION_FAILED로 답한다`() {
        postDrop("""{"name":"너무 긴 이름","quantity":1}""").expectValidationFailure("name").andExpect {
            jsonPath("$.errors[0].message") { value(NAME_TOO_LONG_MESSAGE) }
        }
    }

    @Test
    fun `본문 필드 타입이 맞지 않으면 그 필드를 errors에 담아 VALIDATION_FAILED로 답한다`() {
        postDrop("""{"name":"한정판","quantity":"many"}""").expectValidationFailure("quantity")
    }

    @Test
    fun `본문이 JSON이 아니면 body를 errors에 담아 VALIDATION_FAILED로 답한다`() {
        postDrop("{").expectValidationFailure("body")
    }

    private fun postDrop(body: String): ResultActionsDsl = mvc.post("/inputs/drops") {
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    private fun ResultActionsDsl.expectValidationFailure(field: String): ResultActionsDsl = andExpect {
        status { isBadRequest() }
        jsonPath("$.code") { value(ErrorCode.VALIDATION_FAILED.name) }
        jsonPath("$.message") { value(ErrorCode.VALIDATION_FAILED.message) }
        jsonPath("$.errors[0].field") { value(field) }
    }

    @RestController
    private class InputController {
        @GetMapping("/inputs/orders")
        fun readOrders(@RequestParam("page_size") @Min(1, message = PAGE_SIZE_TOO_SMALL_MESSAGE) pageSize: Int): Int = pageSize

        @GetMapping("/inputs/named-orders")
        fun readNamedOrders(@RequestParam(name = "page_size") @Min(1) pageSize: Int): Int = pageSize

        @GetMapping("/inputs/default-orders")
        fun readDefaultOrders(@RequestParam @Min(1) pageSize: Int): Int = pageSize

        @GetMapping("/inputs/items/{item_id}")
        fun readItem(@PathVariable("item_id") @Min(1) itemId: Int): Int = itemId

        @GetMapping("/inputs/named-items/{item_id}")
        fun readNamedItem(@PathVariable(name = "item_id") @Min(1) itemId: Int): Int = itemId

        @GetMapping("/inputs/default-items/{itemId}")
        fun readDefaultItem(@PathVariable @Min(1) itemId: Int): Int = itemId

        @GetMapping("/inputs/drops/{item_id}")
        fun readDrop(@PathVariable("item_id") itemId: UUID): UUID = itemId

        @PostMapping("/inputs/drops")
        fun createDrop(@RequestBody @Valid request: DropInput): String = request.name
    }

    private data class DropInput(@field:Size(max = 3, message = NAME_TOO_LONG_MESSAGE) val name: String, val quantity: Int)
}
