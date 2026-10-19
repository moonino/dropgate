package dropgate.common.error

import org.springframework.context.MessageSourceResolvable
import org.springframework.core.MethodParameter
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.FieldError
import org.springframework.validation.method.ParameterValidationResult
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
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
    fun handleMissingParameter(exception: MissingServletRequestParameterException): ResponseEntity<ErrorResponse> = rejectInput(listOf(FieldErrorResponse(exception.parameterName, MISSING_VALUE_MESSAGE)))

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(exception: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> = rejectInput(listOf(FieldErrorResponse(exception.name, INVALID_FORMAT_MESSAGE)))

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(exception: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> = rejectInput(listOf(FieldErrorResponse(readBodyPath(exception), INVALID_FORMAT_MESSAGE)))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleInvalidBody(exception: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> = rejectInput(exception.bindingResult.allErrors.map { FieldErrorResponse(it.readFieldOr(it.objectName), it.defaultMessage.orEmpty()) })

    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleInvalidParameter(exception: HandlerMethodValidationException): ResponseEntity<ErrorResponse> = rejectInput(exception.parameterValidationResults.flatMap { it.toFieldErrorResponses() })

    private fun rejectInput(errors: List<FieldErrorResponse>): ResponseEntity<ErrorResponse> = ErrorCode.VALIDATION_FAILED.toResponseEntity(errors)

    private fun readBodyPath(exception: HttpMessageNotReadableException): String = (exception.cause as? JacksonException)?.path.orEmpty()
        .joinToString("") { if (it.propertyName == null) "[${it.index}]" else ".${it.propertyName}" }
        .removePrefix(".")
        .ifEmpty { BODY_FIELD }

    private fun ParameterValidationResult.toFieldErrorResponses(): List<FieldErrorResponse> {
        val parameterName = methodParameter.readRequestName()
        return resolvableErrors.map { FieldErrorResponse(it.readFieldOr(parameterName), it.defaultMessage.orEmpty()) }
    }

    private fun MethodParameter.readRequestName(): String {
        val requestParam = AnnotatedElementUtils.getMergedAnnotation(parameter, RequestParam::class.java)
        val pathVariable = AnnotatedElementUtils.getMergedAnnotation(parameter, PathVariable::class.java)
        return sequenceOf(requestParam?.name, pathVariable?.name, parameterName)
            .filterNotNull()
            .firstOrNull(String::isNotEmpty) ?: "arg$parameterIndex"
    }

    private fun MessageSourceResolvable.readFieldOr(fallback: String): String = if (this is FieldError) field else fallback
}
