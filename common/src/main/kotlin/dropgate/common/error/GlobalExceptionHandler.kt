package dropgate.common.error

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(DropgateException::class)
    fun handle(exception: DropgateException): ResponseEntity<ErrorResponse> = exception.toResponseEntity()
}
