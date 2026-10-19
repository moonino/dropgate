package dropgate.order.event

class OrderEventStreamUnavailableException(message: String, cause: Throwable) : RuntimeException(message, cause)
