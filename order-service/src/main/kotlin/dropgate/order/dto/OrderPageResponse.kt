package dropgate.order.dto

data class OrderPageResponse(val items: List<OrderResponse>, val nextCursor: String?)
