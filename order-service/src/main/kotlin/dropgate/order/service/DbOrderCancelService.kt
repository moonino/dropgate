package dropgate.order.service

import dropgate.order.repository.DropRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class DbOrderCancelService(
    private val cancelableOrders: CancelableOrderReader,
    private val drops: DropRepository,
    private val history: OrderHistoryRecorder,
) {
    @Transactional
    fun cancel(orderId: UUID, userId: UUID): OrderWithDropName {
        val cancelable = cancelableOrders.lockCancelableOrder(orderId, userId)
        drops.restoreOneUnitAndReopen(cancelable.order.dropId)
        cancelable.order.cancel(cancelable.canceledAt)
        history.recordCanceled(cancelable.order, cancelable.dropName)
        return OrderWithDropName(cancelable.order, cancelable.dropName)
    }
}
