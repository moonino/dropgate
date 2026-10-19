package dropgate.order.service

data class OrderPage(val items: List<OrderWithDropName>, val nextCursor: String?)
