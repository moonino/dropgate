package dropgate.order

import org.springframework.jdbc.core.simple.JdbcClient

class OutboxPayload private constructor(private val jdbc: JdbcClient, private val eventId: Long) {
    fun readKeys(): List<String> = jdbc.sql("SELECT jsonb_object_keys(payload) FROM orders.outbox WHERE id = :id").param("id", eventId).query { rs, _ -> rs.getString(1) }.list()

    fun readText(field: String): String = jdbc.sql("SELECT payload ->> :field FROM orders.outbox WHERE id = :id")
        .param("field", field)
        .param("id", eventId)
        .query(String::class.java)
        .single()

    fun readNumber(field: String): Int = readText(field).toInt()

    fun isNull(field: String): Boolean = jdbc.sql("SELECT jsonb_typeof(payload -> :field) = 'null' FROM orders.outbox WHERE id = :id")
        .param("field", field)
        .param("id", eventId)
        .query(Boolean::class.java)
        .single()

    companion object {
        fun findByEventType(jdbc: JdbcClient, eventType: String): OutboxPayload {
            val eventId = jdbc.sql("SELECT id FROM orders.outbox WHERE event_type = :eventType").param("eventType", eventType).query(Long::class.java).single()
            return OutboxPayload(jdbc, eventId)
        }
    }
}
