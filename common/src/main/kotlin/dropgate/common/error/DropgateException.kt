package dropgate.common.error

class DropgateException(
    val errorCode: ErrorCode,
    reason: String,
    val errors: List<InvalidField> = emptyList(),
    cause: Throwable? = null,
) : RuntimeException(reason, cause)
