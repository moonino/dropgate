package dropgate.order

import dropgate.order.repository.DropStockReader
import dropgate.order.repository.RemainingStock
import io.lettuce.core.event.command.CommandListener
import io.lettuce.core.event.command.CommandStartedEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

class DropStockReadTest : DropApiIntegrationTest() {
    @Autowired
    lateinit var stocks: DropStockReader

    @Autowired
    lateinit var redis: StringRedisTemplate

    @Autowired
    lateinit var connections: LettuceConnectionFactory

    @ParameterizedTest
    @ValueSource(ints = [0, 7])
    fun `단건은 0을 포함한 저장 수량을 돌려준다`(quantity: Int) {
        val id = UUID.randomUUID()
        redis.opsForValue().set("drop:$id:stock", quantity.toString(), Duration.ofMinutes(1))

        assertThat(stocks.readStock(id)).isEqualTo(RemainingStock.Known(quantity))
    }

    @Test
    fun `재고 키가 없으면 모름을 돌려준다`() {
        assertThat(stocks.readStock(UUID.randomUUID())).isEqualTo(RemainingStock.Unknown)
    }

    @Test
    fun `목록은 중간 누락과 수량 0을 입력 id 순서에 맞춘다`() {
        val ids = List(3) { UUID.randomUUID() }
        redis.opsForValue().multiSet(mapOf("drop:${ids[0]}:stock" to "7", "drop:${ids[2]}:stock" to "0"))

        assertThat(stocks.readStocks(ids)).containsExactlyEntriesOf(
            mapOf(ids[0] to RemainingStock.Known(7), ids[1] to RemainingStock.Unknown, ids[2] to RemainingStock.Known(0)),
        )
    }

    @Test
    fun `목록은 GET 없이 MGET 한 번으로 읽는다`() {
        val commands = recordReadCommands { stocks.readStocks(List(3) { UUID.randomUUID() }) }

        assertThat(commands).containsExactly("MGET")
    }

    @Test
    fun `빈 목록은 Redis 명령을 보내지 않는다`() {
        val commands = recordReadCommands { assertThat(stocks.readStocks(emptyList())).isEmpty() }

        assertThat(commands).isEmpty()
    }

    private fun recordReadCommands(read: () -> Unit): List<String> {
        val commands = CopyOnWriteArrayList<String>()
        val listener = object : CommandListener {
            override fun commandStarted(event: CommandStartedEvent) {
                val name = event.command.type.toString()
                if (name == "GET" || name == "MGET") commands.add(name)
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
