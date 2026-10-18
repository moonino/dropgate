package dropgate.order.service

import dropgate.order.repository.DropRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class DropStatusTransitionService(private val drops: DropRepository) {
    @Transactional
    fun transitionDueDrops(now: Instant) {
        drops.openDueDrops(now)
        drops.closeDueDrops(now)
    }

    @Transactional
    fun markSoldOutIfExhausted(dropId: UUID) {
        drops.markSoldOutIfExhausted(dropId)
    }
}
