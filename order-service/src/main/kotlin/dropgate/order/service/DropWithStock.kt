package dropgate.order.service

import dropgate.order.entity.DropEntity
import dropgate.order.repository.RemainingStock

data class DropWithStock(val drop: DropEntity, val stock: RemainingStock)
