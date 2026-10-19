package dropgate.order.service

import dropgate.order.entity.DropEntity

data class DropPage(val items: List<DropEntity>, val nextCursor: String?)
