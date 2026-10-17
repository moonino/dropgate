package dropgate.common.error

import org.springframework.context.MessageSourceResolvable
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.FieldError
import org.springframework.validation.ObjectError
import org.springframework.validation.method.ParameterValidationResult
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import tools.jackson.core.JacksonException

private const val BODY_FIELD = "body"

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(DropgateException::class)
    fun handle(exception: DropgateException): ResponseEntity<ErrorResponse> = exception.toResponseEntity()

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParameter(exception: MissingServletRequestParameterException): ResponseEntity<ErrorResponse> = rejectInput(exception, listOf(FieldErrorResponse(exception.parameterName, MISSING_VALUE_MESSAGE)))

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(exception: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> = rejectInput(exception, listOf(FieldErrorResponse(exception.name, INVALID_FORMAT_MESSAGE)))

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(exception: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> = rejectInput(exception, listOf(FieldErrorResponse(readBodyPath(exception), INVALID_FORMAT_MESSAGE)))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleInvalidBody(exception: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> = rejectInput(exception, exception.bindingResult.allErrors.map { it.toFieldErrorResponse() })

    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleInvalidParameter(exception: HandlerMethodValidationException): ResponseEntity<ErrorResponse> = rejectInput(exception, exception.parameterValidationResults.flatMap { it.toFieldErrorResponses() })

    private fun rejectInput(cause: Exception, errors: List<FieldErrorResponse>): ResponseEntity<ErrorResponse> = DropgateException(ErrorCode.VALIDATION_FAILED, "요청 입력이 올바르지 않습니다: ${cause.message}", errors, cause).toResponseEntity()

    private fun readBodyPath(exception: HttpMessageNotReadableException): String = (exception.cause as? JacksonException)?.path.orEmpty()
        .joinToString("") { if (it.propertyName == null) "[${it.index}]" else ".${it.propertyName}" }
        .removePrefix(".")
        .ifEmpty { BODY_FIELD }

    private fun ObjectError.toFieldErrorResponse(): FieldErrorResponse = FieldErrorResponse(if (this is FieldError) field else objectName, defaultMessage.orEmpty())

    private fun ParameterValidationResult.toFieldErrorResponses(): List<FieldErrorResponse> {
        val parameterName = methodParameter.parameterName ?: "arg${methodParameter.parameterIndex}"
        return resolvableErrors.map { FieldErrorResponse(it.fieldOr(parameterName), it.defaultMessage.orEmpty()) }
    }

    private fun MessageSourceResolvable.fieldOr(parameterName: String): String = if (this is FieldError) field else parameterName
}
