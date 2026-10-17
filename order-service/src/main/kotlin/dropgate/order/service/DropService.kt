package dropgate.order.service

import dropgate.common.UuidV7Generator
import dropgate.order.entity.DropEntity
import dropgate.order.entity.DropStatus
import dropgate.order.repository.DropRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class DropService(private val drops: DropRepository, private val identifiers: UuidV7Generator) {
    @Transactional
    fun createDrop(command: CreateDropCommand, adminId: UUID): DropResponse {
        val drop = DropEntity(
            id = identifiers.generate(),
            name = command.name,
            totalQuantity = command.totalQuantity,
            remainingQuantity = command.totalQuantity,
            openAt = command.openAt,
            closeAt = command.closeAt,
            status = DropStatus.SCHEDULED,
            admissionRatePerSec = command.admissionRatePerSec,
            createdBy = adminId,
        )
        return DropResponse.createWithoutStock(drops.save(drop))
    }
}
