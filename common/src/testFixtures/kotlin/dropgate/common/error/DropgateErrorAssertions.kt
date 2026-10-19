package dropgate.common.error

import org.assertj.core.api.AbstractThrowableAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.assertj.core.api.InstanceOfAssertFactories.throwable

fun assertThrowsDropgateError(expectedCode: ErrorCode, block: () -> Unit): AbstractThrowableAssert<*, DropgateException> {
    val exception = catchThrowable { block() }
    val failureDescription = "기대 오류 코드: $expectedCode, 실제 예외: $exception"
    return assertThat(exception).`as`(failureDescription)
        .asInstanceOf(throwable(DropgateException::class.java))
        .satisfies({ actual: DropgateException -> assertThat(actual.errorCode).`as`(failureDescription).isEqualTo(expectedCode) })
}
