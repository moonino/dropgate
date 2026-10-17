package dropgate.common.error

class DropgateException(
    val errorCode: ErrorCode,
    reason: String,
    val errors: List<FieldErrorResponse> = emptyList(),
    cause: Throwable? = null,
) : RuntimeException(reason, cause)
