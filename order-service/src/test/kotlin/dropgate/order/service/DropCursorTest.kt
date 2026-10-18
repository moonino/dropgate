package dropgate.order.service

import dropgate.common.error.DropgateException
import dropgate.common.error.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import java.util.UUID

class DropCursorTest {
    @Test
    fun `인코딩한 커서를 디코딩하면 같은 오픈 시각과 식별자가 나온다`() {
        val cursor = DropCursor(Instant.parse("2030-11-01T10:00:00.123456Z"), UUID.randomUUID())

        assertThat(DropCursor.decode(cursor.encode())).isEqualTo(cursor)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "broken!", "bm90LWEtY3Vyc29y", "MjAzMC0xMS0wMVQxMDowMDowMFosbm90LWEtdXVpZA"])
    fun `읽을 수 없는 커서는 cursor 필드의 VALIDATION_FAILED 예외로 거부한다`(encoded: String) {
        assertThatThrownBy { DropCursor.decode(encoded) }.isInstanceOfSatisfying(DropgateException::class.java) {
            assertThat(it.errorCode).isEqualTo(ErrorCode.VALIDATION_FAILED)
            assertThat(it.errors.map { error -> error.field }).containsExactly("cursor")
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["0000-12-31T23:59:59.999Z", "+10000-01-01T00:00:00Z", "+300000-01-01T00:00:00Z"])
    fun `저장할 수 없는 오픈 시각을 담은 커서는 cursor 필드의 VALIDATION_FAILED 예외로 거부한다`(openAt: String) {
        val encoded = DropCursor(Instant.parse(openAt), UUID.randomUUID()).encode()

        assertThatThrownBy { DropCursor.decode(encoded) }.isInstanceOfSatisfying(DropgateException::class.java) {
            assertThat(it.errorCode).isEqualTo(ErrorCode.VALIDATION_FAILED)
            assertThat(it.errors.map { error -> error.field }).containsExactly("cursor")
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["0001-01-01T00:00:00Z", "9999-12-31T23:59:59.999Z"])
    fun `저장할 수 있는 범위 끝의 오픈 시각을 담은 커서는 받아들인다`(openAt: String) {
        val cursor = DropCursor(Instant.parse(openAt), UUID.randomUUID())

        assertThat(DropCursor.decode(cursor.encode())).isEqualTo(cursor)
    }

    @Test
    fun `커서는 URL에 그대로 넣을 수 있는 문자만 쓴다`() {
        val encoded = DropCursor(Instant.parse("2030-11-01T10:00:00Z"), UUID.randomUUID()).encode()

        assertThat(encoded).matches("[A-Za-z0-9_-]+")
    }
}
