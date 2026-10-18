package dropgate.common.error

import org.springframework.http.ResponseEntity

data class ErrorResponse(val code: String, val message: String, val errors: List<FieldErrorResponse>)

data class FieldErrorResponse(val field: String, val message: String)

fun DropgateException.toResponseEntity(): ResponseEntity<ErrorResponse> = errorCode.toResponseEntity(errors.map { FieldErrorResponse(it.field, it.message) })

fun ErrorCode.toResponseEntity(errors: List<FieldErrorResponse>): ResponseEntity<ErrorResponse> = ResponseEntity.status(status)
    .headers(createHeaders())
    .body(ErrorResponse(name, message, errors))
