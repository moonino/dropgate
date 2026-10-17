package dropgate.order.repository

import dropgate.order.entity.OrderEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.Optional
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrderEntity o WHERE o.id = :id")
    fun findForUpdate(@Param("id") id: UUID): Optional<OrderEntity>
}
