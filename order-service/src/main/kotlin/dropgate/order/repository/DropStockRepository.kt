package dropgate.order.repository

import dropgate.common.error.DropgateException
import dropgate.common.error.ErrorCode
import dropgate.order.configuration.OrderProperties
import dropgate.order.entity.DropEntity
import org.springframework.core.io.ClassPathResource
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Repository
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import java.util.UUID

private const val MINIMUM_RESTORED_QUANTITY = 1L
private const val SALE_ALREADY_OPEN = 0L
private const val SALE_REOPENED = 1L

@Repository
class DropStockRepository(private val redis: StringRedisTemplate, private val settings: OrderProperties) {
    private val initialization = DefaultRedisScript(ClassPathResource("redis/init.lua").getContentAsString(UTF_8), Long::class.java)
    private val reservation = DefaultRedisScript(ClassPathResource("redis/purchase.lua").getContentAsString(UTF_8), List::class.java)
    private val compensation = DefaultRedisScript(ClassPathResource("redis/compensate.lua").getContentAsString(UTF_8), Long::class.java)
    private val cancellation = DefaultRedisScript(ClassPathResource("redis/cancel.lua").getContentAsString(UTF_8), Long::class.java)

    fun compensate(dropId: UUID, userId: UUID) {
        if (restore(dropId, userId, compensation) < MINIMUM_RESTORED_QUANTITY) throw createRestorationFailure(dropId, userId)
    }

    fun cancel(dropId: UUID, userId: UUID) {
        if (restore(dropId, userId, cancellation) !in SALE_ALREADY_OPEN..SALE_REOPENED) throw createRestorationFailure(dropId, userId)
    }

    fun purchase(dropId: UUID, userId: UUID, now: Instant): StockReservation {
        val result = execute(
            "드롭 $dropId 사용자 $userId 구매",
            reservation,
            listOf(createDropRedisKey(dropId, "stock"), createDropRedisKey(dropId, "buyers"), createDropRedisKey(dropId, "admit:$userId"), createDropRedisKey(dropId, "meta")),
            userId.toString(),
            now.toEpochMilli().toString(),
            if (settings.queue.enabled) "1" else "0",
        )
        val remaining = (result.first() as Long).toInt()
        return if (remaining >= 0) StockReservation.Reserved(remaining) else StockReservation.Rejected(ErrorCode.valueOf(result.last() as String))
    }

    fun initialize(drop: DropEntity) {
        execute(
            "드롭 ${drop.id} 초기화",
            initialization,
            listOf(createDropRedisKey(drop.id, "stock"), createDropRedisKey(drop.id, "meta")),
            drop.totalQuantity.toString(),
            drop.openAt.toEpochMilli().toString(),
            drop.closeAt.toEpochMilli().toString(),
            drop.admissionRatePerSec.toString(),
        )
    }

    private fun restore(dropId: UUID, userId: UUID, script: RedisScript<Long>): Long = execute(
        "드롭 $dropId 사용자 $userId 재고 복구",
        script,
        listOf(createDropRedisKey(dropId, "stock"), createDropRedisKey(dropId, "buyers"), createDropRedisKey(dropId, "meta")),
        userId.toString(),
    )

    private fun createRestorationFailure(dropId: UUID, userId: UUID): DropgateException = DropgateException(
        ErrorCode.STOCK_UNAVAILABLE,
        "드롭 $dropId 사용자 $userId 의 재고 복구 키가 없습니다",
    )

    private fun <T : Any> execute(context: String, script: RedisScript<T>, keys: List<String>, vararg arguments: String): T {
        try {
            return checkNotNull(redis.execute(script, keys, *arguments)) { "Redis 재고 명령 결과가 없습니다: $context" }
        } catch (exception: DataAccessException) {
            throw DropgateException(ErrorCode.STOCK_UNAVAILABLE, "Redis 재고 명령에 실패했습니다: $context", cause = exception)
        }
    }
}
