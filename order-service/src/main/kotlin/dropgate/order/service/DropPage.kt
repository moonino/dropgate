package dropgate.order.service

data class DropPage(val items: List<DropWithStock>, val nextCursor: String?)
