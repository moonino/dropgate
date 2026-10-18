package dropgate.order.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "order_ledger", schema = "orders")
class OrderLedgerEntity(
    @Column(name = "order_id", nullable = false) val orderId: UUID,
    @Column(name = "drop_id", nullable = false) val dropId: UUID,
    @Column(name = "user_id", nullable = false) val userId: UUID,
    @Enumerated(EnumType.STRING) @Column(name = "entry_type", nullable = false, length = 20) val entryType: LedgerEntryType,
    @Column(name = "occurred_at", nullable = false) val occurredAt: Instant,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
