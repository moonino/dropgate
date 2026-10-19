package dropgate.order

import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

class StockInitializationMigrationTest {
    @Test
    fun `기존 드롭은 초기화 권한을 사용한 상태로 전환하고 새 드롭만 권한을 받는다`() {
        PostgreSQLContainer("postgres:16-alpine").use { postgres ->
            postgres.start()
            configureMigrations(postgres).target("4").load().migrate()

            DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
                val existingDropId = UUID.randomUUID()
                insertOpenDrop(connection, existingDropId)

                configureMigrations(postgres).load().migrate()

                val newDropId = UUID.randomUUID()
                insertOpenDrop(connection, newDropId)

                assertThat(readClaims(connection)).containsExactlyInAnyOrderEntriesOf(
                    mapOf(existingDropId to true, newDropId to false),
                )
            }
        }
    }

    private fun configureMigrations(postgres: PostgreSQLContainer) = Flyway.configure()
        .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        .schemas("orders")
        .locations("classpath:db/migration")

    private fun insertOpenDrop(connection: Connection, dropId: UUID) {
        connection.prepareStatement(
            """
            INSERT INTO orders.drops
                (id, name, total_quantity, remaining_quantity, open_at, close_at, status, created_by)
            VALUES (?, 'migration', 10, 9, now(), now() + interval '1 hour', 'OPEN', gen_random_uuid())
            """,
        ).use { statement ->
            statement.setObject(1, dropId)
            statement.executeUpdate()
        }
    }

    private fun readClaims(connection: Connection): Map<UUID, Boolean> = connection.createStatement().use { statement ->
        statement.executeQuery("SELECT id, stock_initialization_claimed FROM orders.drops").use { rows ->
            buildMap {
                while (rows.next()) {
                    put(
                        rows.getObject("id", UUID::class.java),
                        rows.getObject("stock_initialization_claimed", Boolean::class.javaObjectType),
                    )
                }
            }
        }
    }
}
