package dropgate.order

import dropgate.order.repository.DropRepository
import dropgate.order.repository.DropStockRepository
import io.lettuce.core.event.command.CommandListener
import io.lettuce.core.event.command.CommandStartedEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import java.util.concurrent.CopyOnWriteArrayList

class DropStockResponseTest : DropApiIntegrationTest() {
    @Autowired
    lateinit var drops: DropRepository

    @Autowired
    lateinit var stocks: DropStockRepository

    @Autowired
    lateinit var redis: StringRedisTemplate

    @Autowired
    lateinit var connections: LettuceConnectionFactory

    @Test
    fun `OPEN 초기화 뒤 단건은 총수량을 돌려준다`() {
        val id = insertDrop(status = "OPEN")
        stocks.initialize(drops.findById(id).orElseThrow())

        http.get().uri("/api/drops/$id").exchange().expectStatus().isOk.expectBody().jsonPath("$.remainingQuantity").isEqualTo(10)
    }

    @Test
    fun `목록은 키 없는 항목과 수량 0을 입력 순서에 맞춘다`() {
        val open = insertDrop(OPEN_AT, "OPEN")
        insertDrop(OPEN_AT.plusSeconds(1))
        val soldOut = insertDrop(OPEN_AT.plusSeconds(2), "SOLD_OUT")
        redis.opsForValue().multiSet(mapOf("drop:$open:stock" to "7", "drop:$soldOut:stock" to "0"))

        http.get().uri("/api/drops").exchange().expectStatus().isOk.expectBody()
            .jsonPath("$.items[0].remainingQuantity").isEqualTo(7)
            .jsonPath("$.items[1].remainingQuantity").isEmpty
            .jsonPath("$.items[2].remainingQuantity").isEqualTo(0)
    }

    @Test
    fun `목록은 커서 계산용 항목을 빼고 응답 항목만 MGET 한 번으로 읽는다`() {
        val first = insertDrop(OPEN_AT)
        val second = insertDrop(OPEN_AT.plusSeconds(1))
        val next = insertDrop(OPEN_AT.plusSeconds(2))

        val commands = recordReadCommands { http.get().uri("/api/drops?limit=2").exchange().expectStatus().isOk }

        assertThat(commands).hasSize(1)
        assertThat(commands.single()).startsWith("MGET ").contains("drop:$first:stock", "drop:$second:stock").doesNotContain(next.toString())
    }

    @Test
    fun `드롭 등록은 Redis 읽기 명령을 보내지 않는다`() {
        val body = """{"name":"드롭","totalQuantity":1,"openAt":"2030-11-01T10:00:00Z","closeAt":"2030-11-01T11:00:00Z"}"""

        val commands = recordReadCommands {
            postDrop(body, issueAdminToken()).expectStatus().isCreated
        }

        assertThat(commands).isEmpty()
    }

    private fun recordReadCommands(read: () -> Unit): List<String> {
        val commands = CopyOnWriteArrayList<String>()
        val listener = object : CommandListener {
            override fun commandStarted(event: CommandStartedEvent) {
                val name = event.command.type.toString()
                if (name == "GET" || name == "MGET") commands.add("$name ${event.command.args.toCommandString()}")
            }
        }
        val client = connections.requiredNativeClient
        client.addListener(listener)
        connections.resetConnection()
        try {
            read()
        } finally {
            client.removeListener(listener)
            connections.resetConnection()
        }
        return commands
    }
}
