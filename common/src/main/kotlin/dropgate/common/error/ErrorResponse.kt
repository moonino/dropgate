package dropgate.common.error

import org.springframework.http.ResponseEntity

data class ErrorResponse(val code: String, val message: String, val errors: List<FieldErrorResponse>)

data class FieldErrorResponse(val field: String, val message: String)

fun DropgateException.toResponseEntity(): ResponseEntity<ErrorResponse> = ResponseEntity.status(errorCode.status)
    .headers(errorCode.createHeaders())
    .body(ErrorResponse(errorCode.name, errorCode.message, errors.map { FieldErrorResponse(it.field, it.message) }))
