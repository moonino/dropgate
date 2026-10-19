package dropgate.order.service

import dropgate.common.UuidV7Generator
import dropgate.common.error.DropgateException
import dropgate.common.error.ErrorCode
import dropgate.order.entity.DropEntity
import dropgate.order.entity.DropStatus
import dropgate.order.repository.DropRepository
import dropgate.order.repository.DropStockReader
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class DropService(private val drops: DropRepository, private val identifiers: UuidV7Generator, private val stocks: DropStockReader) {
    @Transactional
    fun createDrop(command: CreateDropCommand, adminId: UUID): DropEntity {
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
        return drops.save(drop)
    }

    @Transactional(readOnly = true)
    fun getDrop(id: UUID): DropWithStock {
        val drop = drops.findById(id).orElseThrow { DropgateException(ErrorCode.DROP_NOT_FOUND, "드롭이 없습니다: $id") }
        return DropWithStock(drop, stocks.readStock(id))
    }

    @Transactional(readOnly = true)
    fun getFirstDrops(limit: Int): DropPage = toPage(drops.findListedDrops(limit + 1), limit)

    @Transactional(readOnly = true)
    fun getDropsAfter(cursor: DropCursor, limit: Int): DropPage = toPage(drops.findListedDropsAfter(cursor.openAt, cursor.id, limit + 1), limit)

    private fun toPage(fetched: List<DropEntity>, limit: Int): DropPage {
        val items = fetched.take(limit)
        val nextCursor = if (fetched.size > limit) DropCursor(items.last().openAt, items.last().id).encode() else null
        val quantities = stocks.readStocks(items.map { it.id })
        return DropPage(items.map { DropWithStock(it, quantities.getValue(it.id)) }, nextCursor)
    }
}
