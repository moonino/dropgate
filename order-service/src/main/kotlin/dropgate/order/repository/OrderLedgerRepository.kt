package dropgate.order.repository

import dropgate.order.entity.OrderLedgerEntity
import org.springframework.data.jpa.repository.JpaRepository

interface OrderLedgerRepository : JpaRepository<OrderLedgerEntity, Long>
