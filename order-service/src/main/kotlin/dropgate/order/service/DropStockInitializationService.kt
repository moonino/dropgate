package dropgate.order.service

import dropgate.order.entity.DropEntity
import dropgate.order.repository.DropRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation.REQUIRES_NEW
import org.springframework.transaction.annotation.Transactional

@Service
class DropStockInitializationService(private val drops: DropRepository) {
    @Transactional(propagation = REQUIRES_NEW)
    fun claimOpenDrops(): List<DropEntity> = drops.findOpenDropsAwaitingStockInitialization().filter { drops.claimStockInitialization(it.id) == 1 }
}
