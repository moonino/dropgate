package dropgate.order

import dropgate.order.repository.DropStockRepository
import dropgate.order.service.DropStatusTransitionScheduler
import dropgate.order.service.DropStatusTransitionService
import dropgate.order.service.DropStockInitializationService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import java.net.ServerSocket
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID

class StockInitializationClaimTest : DropApiIntegrationTest() {
    @Autowired
    lateinit var initializations: DropStockInitializationService

    @Autowired
    lateinit var transitions: DropStatusTransitionService

    @Autowired
    lateinit var stocks: DropStockRepository

    @Autowired
    lateinit var redis: StringRedisTemplate

    @Autowired
    lateinit var connections: LettuceConnectionFactory

    @Test
    fun `스케줄러는 OPEN 전이 뒤 권한을 영속화하고 Redis를 초기화한다`() {
        val dropId = insertDrop()

        tick()

        assertThat(findDropStatus(dropId)).isEqualTo("OPEN")
        assertThat(isClaimed(dropId)).isTrue()
        assertThat(redis.opsForValue().get("drop:$dropId:stock")).isEqualTo("10")
        assertThat(redis.opsForHash<String, String>().get("drop:$dropId:meta", "status")).isEqualTo("OPEN")
    }

    @Test
    fun `아직 열리지 않은 드롭은 권한과 Redis 키가 없다`() {
        val dropId = insertDrop(openAt = OPEN_AT.plusSeconds(1))

        tick()

        assertThat(isClaimed(dropId)).isFalse()
        assertThat(redis.hasKey("drop:$dropId:stock")).isFalse()
        assertThat(redis.hasKey("drop:$dropId:meta")).isFalse()
    }

    @Test
    fun `여러 호출이 경합해도 초기화 권한은 한 번만 커밋된다`() {
        val dropId = insertDrop(status = "OPEN")

        val claimed = ConcurrentRequests.run(4) { initializations.claimOpenDrops().map { it.id } }.flatten()

        assertThat(claimed).containsExactly(dropId)
        assertThat(isClaimed(dropId)).isTrue()
    }

    @Test
    fun `초기화 뒤 Redis 키가 유실돼도 재생성하지 않는다`() {
        val dropId = insertDrop()
        tick()
        redis.delete(listOf("drop:$dropId:stock", "drop:$dropId:meta"))

        tick()

        assertThat(isClaimed(dropId)).isTrue()
        assertThat(redis.hasKey("drop:$dropId:stock")).isFalse()
        assertThat(redis.hasKey("drop:$dropId:meta")).isFalse()
    }

    @Test
    fun `최초 Redis 실패는 DB 전이와 다른 드롭을 막지 않고 권한을 재시도하지 않는다`() {
        val dropIds = listOf(insertDrop(), insertDrop())
        val port = ServerSocket(0).use { it.localPort }
        val unavailable = LettuceConnectionFactory(RedisStandaloneConfiguration("127.0.0.1", port), connections.clientConfiguration)
        unavailable.afterPropertiesSet()
        try {
            tick(DropStockRepository(StringRedisTemplate(unavailable)))
        } finally {
            unavailable.destroy()
        }

        tick()

        dropIds.forEach { dropId ->
            assertThat(findDropStatus(dropId)).isEqualTo("OPEN")
            assertThat(isClaimed(dropId)).isTrue()
            assertThat(redis.hasKey("drop:$dropId:stock")).isFalse()
            assertThat(redis.hasKey("drop:$dropId:meta")).isFalse()
        }
    }

    private fun tick(repository: DropStockRepository = stocks) {
        DropStatusTransitionScheduler(transitions, initializations, repository, Clock.fixed(OPEN_AT, ZoneOffset.UTC)).transitionDueDrops()
    }

    private fun isClaimed(dropId: UUID): Boolean = jdbc.sql("SELECT stock_initialization_claimed FROM orders.drops WHERE id = :id")
        .param("id", dropId).query(Boolean::class.java).single()
}
