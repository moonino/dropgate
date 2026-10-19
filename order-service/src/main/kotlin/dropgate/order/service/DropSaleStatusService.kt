package dropgate.order.service

import dropgate.order.entity.DropStatus
import dropgate.order.repository.DropRepository
import dropgate.order.repository.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation.READ_COMMITTED
import org.springframework.transaction.annotation.Propagation.REQUIRES_NEW
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class DropSaleStatusService(private val drops: DropRepository, private val orders: OrderRepository) {
    @Transactional(propagation = REQUIRES_NEW, isolation = READ_COMMITTED)
    fun synchronizeSaleStatus(dropId: UUID) {
        val drop = drops.findForUpdate(dropId).orElseThrow { IllegalStateException("판매 상태를 동기화할 드롭이 없습니다: $dropId") }
        val confirmed = orders.countConfirmedByDropId(dropId)
        val total = drop.totalQuantity.toLong()
        val status = when {
            confirmed == total -> DropStatus.SOLD_OUT
            confirmed < total -> DropStatus.OPEN
            else -> throw IllegalStateException("드롭 $dropId 의 확정 주문 $confirmed 개가 총수량 $total 개를 넘었습니다")
        }
        drops.updateSaleStatusIfSelling(dropId, status)
    }
}
