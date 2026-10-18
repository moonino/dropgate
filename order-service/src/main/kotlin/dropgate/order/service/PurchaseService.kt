package dropgate.order.service

import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import org.springframework.transaction.TransactionException
import java.util.UUID

private val log = LoggerFactory.getLogger(PurchaseService::class.java)

@Service
class PurchaseService(private val confirmations: OrderConfirmationService, private val dropTransitions: DropStatusTransitionService) {
    fun purchase(dropId: UUID, userId: UUID): OrderResponse {
        val confirmed = confirmations.confirm(dropId, userId)
        if (confirmed.stockExhausted) markSoldOutKeepingOrderConfirmed(dropId)
        return confirmed.order
    }

    private fun markSoldOutKeepingOrderConfirmed(dropId: UUID) {
        try {
            dropTransitions.markSoldOutIfExhausted(dropId)
        } catch (exception: DataAccessException) {
            logSkippedSoldOutTransition(dropId, exception)
        } catch (exception: TransactionException) {
            logSkippedSoldOutTransition(dropId, exception)
        }
    }

    private fun logSkippedSoldOutTransition(dropId: UUID, exception: RuntimeException) {
        log.warn("재고를 다 판 드롭 {} 의 SOLD_OUT 전이에 실패했습니다. 조건부 차감이 추가 판매를 막으므로 확정 주문은 그대로 돌려줍니다", dropId, exception)
    }
}
