package dropgate.order.service

import dropgate.order.entity.OrderEntity

data class OrderWithDropName(val order: OrderEntity, val dropName: String)
