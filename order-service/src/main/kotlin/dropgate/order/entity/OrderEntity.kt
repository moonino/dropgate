package dropgate.order.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "orders", schema = "orders")
class OrderEntity(
    @Id val id: UUID,
    @Column(name = "drop_id", nullable = false) val dropId: UUID,
    @Column(name = "user_id", nullable = false) val userId: UUID,
    @Column(nullable = false) val quantity: Int,
    status: OrderStatus,
    @Column(name = "confirmed_at", nullable = false) val confirmedAt: Instant,
    canceledAt: Instant?,
) {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: OrderStatus = status
        protected set

    @Column(name = "canceled_at")
    var canceledAt: Instant? = canceledAt
        protected set

    companion object {
        const val SINGLE_ITEM_QUANTITY = 1

        fun confirm(id: UUID, dropId: UUID, userId: UUID, confirmedAt: Instant) = OrderEntity(id, dropId, userId, SINGLE_ITEM_QUANTITY, OrderStatus.CONFIRMED, confirmedAt, canceledAt = null)
    }
}
