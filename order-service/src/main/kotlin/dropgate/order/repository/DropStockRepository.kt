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

@Repository
class DropStockRepository(private val redis: StringRedisTemplate, private val settings: OrderProperties) {
    private val initialization = DefaultRedisScript(ClassPathResource("redis/init.lua").getContentAsString(UTF_8), Long::class.java)
    private val reservation = DefaultRedisScript(ClassPathResource("redis/purchase.lua").getContentAsString(UTF_8), List::class.java)

    fun purchase(dropId: UUID, userId: UUID, now: Instant): StockReservation {
        val result = execute(
            "드롭 $dropId 사용자 $userId 구매",
            reservation,
            listOf(createKey(dropId, "stock"), createKey(dropId, "buyers"), createKey(dropId, "admit:$userId"), createKey(dropId, "meta")),
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
            listOf(createKey(drop.id, "stock"), createKey(drop.id, "meta")),
            drop.totalQuantity.toString(),
            drop.openAt.toEpochMilli().toString(),
            drop.closeAt.toEpochMilli().toString(),
            drop.admissionRatePerSec.toString(),
        )
    }

    private fun createKey(dropId: UUID, suffix: String): String = "drop:$dropId:$suffix"

    private fun <T : Any> execute(context: String, script: RedisScript<T>, keys: List<String>, vararg arguments: String): T {
        try {
            return checkNotNull(redis.execute(script, keys, *arguments)) { "Redis 재고 명령 결과가 없습니다: $context" }
        } catch (exception: DataAccessException) {
            throw DropgateException(ErrorCode.STOCK_UNAVAILABLE, "Redis 재고 명령에 실패했습니다: $context", cause = exception)
        }
    }
}
