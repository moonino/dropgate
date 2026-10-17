package dropgate.order.repository

import dropgate.order.entity.OrderEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface OrderRepository : JpaRepository<OrderEntity, UUID> {
    @Modifying
    @Query(
        value = """
        INSERT INTO orders.orders (id, drop_id, user_id, quantity, status, confirmed_at)
        VALUES (:id, :dropId, :userId, :quantity, 'CONFIRMED', :confirmedAt)
        ON CONFLICT (drop_id, user_id) DO NOTHING
    """,
        nativeQuery = true,
    )
    fun insertConfirmedUnlessPurchased(
        @Param("id") id: UUID,
        @Param("dropId") dropId: UUID,
        @Param("userId") userId: UUID,
        @Param("quantity") quantity: Int,
        @Param("confirmedAt") confirmedAt: Instant,
    ): Int
}
