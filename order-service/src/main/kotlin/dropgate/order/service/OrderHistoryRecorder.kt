package dropgate.order.service

import dropgate.order.entity.LedgerEntryType
import dropgate.order.entity.OrderEntity
import dropgate.order.entity.OrderLedgerEntity
import dropgate.order.entity.OutboxEntity
import dropgate.order.repository.OrderLedgerRepository
import dropgate.order.repository.OutboxRepository
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Instant

private const val ORDER_AGGREGATE_TYPE = "order"
private const val ORDER_CONFIRMED_EVENT = "order.confirmed"

@Component
class OrderHistoryRecorder(private val ledger: OrderLedgerRepository, private val outbox: OutboxRepository, private val jsonMapper: ObjectMapper) {
    fun recordConfirmed(order: OrderEntity, dropName: String) {
        record(order, dropName, LedgerEntryType.CONFIRMED, order.confirmedAt, ORDER_CONFIRMED_EVENT)
    }

    private fun record(order: OrderEntity, dropName: String, entryType: LedgerEntryType, occurredAt: Instant, eventType: String) {
        ledger.save(OrderLedgerEntity(order.id, order.dropId, order.userId, entryType, occurredAt))
        outbox.save(OutboxEntity(ORDER_AGGREGATE_TYPE, order.id, eventType, jsonMapper.writeValueAsString(OrderEventPayload.createFrom(order, dropName))))
    }
}
