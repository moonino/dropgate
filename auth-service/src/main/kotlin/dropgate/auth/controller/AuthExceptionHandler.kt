package dropgate.auth.controller

import dropgate.auth.client.KakaoUnavailableException
import dropgate.auth.repository.InvalidStateException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class AuthExceptionHandler {
    @ExceptionHandler(InvalidStateException::class)
    fun handleState(exception: InvalidStateException): ResponseEntity<ErrorResponse> = createValidationResponse("state", exception.message.orEmpty())

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParameter(exception: MissingServletRequestParameterException): ResponseEntity<ErrorResponse> = createValidationResponse(exception.parameterName, "필수 로그인 값이 없습니다")

    @ExceptionHandler(KakaoUnavailableException::class)
    fun handleKakao(exception: KakaoUnavailableException): ResponseEntity<ErrorResponse> = ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ErrorResponse("KAKAO_UNAVAILABLE", exception.message.orEmpty()))

    private fun createValidationResponse(field: String, message: String): ResponseEntity<ErrorResponse> = ResponseEntity.badRequest().body(ErrorResponse("VALIDATION_FAILED", message, listOf(FieldErrorResponse(field, message))))
}

data class ErrorResponse(val code: String, val message: String, val errors: List<FieldErrorResponse> = emptyList())

data class FieldErrorResponse(val field: String, val message: String)
