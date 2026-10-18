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
@Table(name = "drops", schema = "orders")
class DropEntity(
    @Id val id: UUID,
    @Column(nullable = false, length = 100) val name: String,
    @Column(name = "total_quantity", nullable = false) val totalQuantity: Int,
    @Column(name = "remaining_quantity", nullable = false) val remainingQuantity: Int,
    @Column(name = "open_at", nullable = false) val openAt: Instant,
    @Column(name = "close_at", nullable = false) val closeAt: Instant,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) val status: DropStatus,
    @Column(name = "admission_rate_per_sec", nullable = false) val admissionRatePerSec: Int,
    @Column(name = "created_by", nullable = false) val createdBy: UUID,
)
