package dropgate.order.service

import java.util.UUID

interface PurchaseFlow {
    fun purchase(dropId: UUID, userId: UUID): OrderWithDropName

    fun cancel(orderId: UUID, userId: UUID): OrderWithDropName
}
