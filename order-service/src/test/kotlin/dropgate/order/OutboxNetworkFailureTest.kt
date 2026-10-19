package dropgate.order

import dropgate.order.entity.OutboxEntity
import dropgate.order.entity.OutboxStatus
import dropgate.order.event.OrderEventStream
import dropgate.order.repository.OutboxRepository
import dropgate.order.service.OutboxRelayService
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import tools.jackson.databind.ObjectMapper
import java.net.ServerSocket
import java.time.Clock

class OutboxNetworkFailureTest : OrderApiIntegrationTest() {
    @Autowired
    lateinit var outbox: OutboxRepository

    @Autowired
    lateinit var relay: OutboxRelayService

    @Autowired
    lateinit var redis: StringRedisTemplate

    @Autowired
    lateinit var connections: LettuceConnectionFactory

    @Autowired
    lateinit var jsonMapper: ObjectMapper

    @Autowired
    lateinit var clock: Clock

    @Autowired
    lateinit var meters: MeterRegistry

    @BeforeEach
    fun clearEventStream() {
        redis.delete("orders.events")
    }

    @Test
    fun `Redis 서버 명령 오류는 일반 실패로 기록한다`() {
        insertPendingEvents()
        redis.opsForValue().set("orders.events", "wrongtype")

        relay.publishPendingEvents()

        assertThat(outbox.findAll().map { it.attempts }).containsOnly(1)
        assertThat(outbox.findAll().map { it.status }).containsOnly(OutboxStatus.PENDING)
    }

    @Test
    fun `연결 거절 뒤에도 시도 횟수를 보존하고 다음 실행에서 발행한다`() {
        insertPendingEvents()
        val port = ServerSocket(0).use { it.localPort }
        val unavailable = LettuceConnectionFactory(RedisStandaloneConfiguration("127.0.0.1", port), connections.clientConfiguration)
        unavailable.afterPropertiesSet()
        try {
            publishWith(OrderEventStream(StringRedisTemplate(unavailable), jsonMapper))
            assertThat(outbox.findAll().map { it.attempts }).containsOnly(0)
            assertThat(outbox.findAll().map { it.status }).containsOnly(OutboxStatus.PENDING)
        } finally {
            unavailable.destroy()
        }

        relay.publishPendingEvents()

        assertThat(outbox.findAll().map { it.status }).containsOnly(OutboxStatus.PUBLISHED)
        assertThat(redis.opsForStream<String, String>().size("orders.events")).isEqualTo(3)
    }

    @Test
    fun `연결 단절은 앞선 발행을 커밋하고 나머지 행을 건너뛴다`() {
        withDedicatedRedis { container, template ->
            verifyInterruptedBatch(template) { container.stop() }
        }
    }

    @Test
    fun `명령 타임아웃도 앞선 발행을 커밋하고 나머지 행을 건너뛴다`() {
        withDedicatedRedis { container, template ->
            val docker = DockerClientFactory.instance().client()
            try {
                verifyInterruptedBatch(template) { docker.pauseContainerCmd(container.containerId).exec() }
            } finally {
                docker.unpauseContainerCmd(container.containerId).exec()
            }
        }
    }

    private fun verifyInterruptedBatch(template: StringRedisTemplate, interrupt: () -> Unit) {
        insertPendingEvents()
        var attempted = 0
        val failures = mutableListOf<RuntimeException>()
        val publisher = object : OrderEventStream(template, jsonMapper) {
            override fun publish(event: OutboxEntity) {
                attempted += 1
                try {
                    super.publish(event)
                } catch (exception: RuntimeException) {
                    failures.add(exception)
                    throw exception
                }
                if (attempted == 1) interrupt()
            }
        }

        publishWith(publisher)

        val events = outbox.findAll()
        assertThat(events.count { it.status == OutboxStatus.PUBLISHED }).isEqualTo(1)
        assertThat(events.count { it.status == OutboxStatus.PENDING }).isEqualTo(2)
        assertThat(events.map { it.attempts }).withFailMessage(failures.joinToString { it.stackTraceToString() }).containsOnly(0)
        assertThat(attempted).isEqualTo(2)
    }

    private fun publishWith(publisher: OrderEventStream) {
        transactions.executeWithoutResult { OutboxRelayService(outbox, publisher, clock, meters).publishPendingEvents() }
    }

    private fun withDedicatedRedis(verify: (GenericContainer<Nothing>, StringRedisTemplate) -> Unit) {
        GenericContainer<Nothing>("redis:7.4-alpine").apply { withExposedPorts(6379) }.use { container ->
            container.start()
            val factory = LettuceConnectionFactory(RedisStandaloneConfiguration(container.host, container.getMappedPort(6379)), connections.clientConfiguration)
            factory.afterPropertiesSet()
            try {
                val template = StringRedisTemplate(factory)
                template.connectionFactory!!.connection.use { it.ping() }
                verify(container, template)
            } finally {
                factory.destroy()
            }
        }
    }

    private fun insertPendingEvents() {
        jdbc.sql(
            """
            INSERT INTO orders.outbox (aggregate_type, aggregate_id, event_type, payload)
            SELECT 'order', gen_random_uuid(), 'order.confirmed', '{"confirmedAt":"2030-11-01T10:00:00.123Z"}'::jsonb
            FROM generate_series(1, 3)
            """,
        ).update()
    }
}
