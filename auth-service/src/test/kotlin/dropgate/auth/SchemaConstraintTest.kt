package dropgate.auth

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.OffsetDateTime
import java.util.UUID

@SpringBootTest(classes = [AuthServiceApplication::class])
@Import(PostgresTestcontainersConfiguration::class)
class SchemaConstraintTest : JwtIntegrationTest() {
    @Autowired
    lateinit var jdbc: JdbcClient

    @BeforeEach
    fun clearUsers() {
        jdbc.sql("DELETE FROM auth.users").update()
    }

    @Test
    fun `kakao_id가 같은 사용자를 두 번 넣을 수 없다`() {
        insertUser(kakaoId = 1L)

        assertThatThrownBy { insertUser(kakaoId = 1L) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `role은 USER와 ADMIN만 허용한다`() {
        assertThatThrownBy { insertUser(kakaoId = 2L, role = "ROOT") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `role을 주지 않으면 USER가 된다`() {
        val id = insertUser(kakaoId = 3L)

        val role = jdbc.sql("SELECT role FROM auth.users WHERE id = :id").param("id", id).query(String::class.java).single()
        assertThat(role).isEqualTo("USER")
    }

    @Test
    fun `nickname 없이 사용자를 넣을 수 없다`() {
        assertThatThrownBy {
            jdbc.sql("INSERT INTO auth.users (id, kakao_id) VALUES (:id, :kakaoId)")
                .param("id", UUID.randomUUID())
                .param("kakaoId", 4L)
                .update()
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `created_at과 last_login_at은 넣은 시각으로 채워진다`() {
        val before = OffsetDateTime.now().minusMinutes(1)
        val id = insertUser(kakaoId = 5L)

        val row =
            jdbc.sql("SELECT created_at, last_login_at FROM auth.users WHERE id = :id")
                .param("id", id)
                .query { rs, _ -> rs.getObject("created_at", OffsetDateTime::class.java) to rs.getObject("last_login_at", OffsetDateTime::class.java) }
                .single()
        assertThat(row.first).isAfter(before)
        assertThat(row.second).isAfter(before)
    }

    private fun insertUser(
        kakaoId: Long,
        role: String? = null,
    ): UUID {
        val id = UUID.randomUUID()
        if (role == null) {
            jdbc.sql("INSERT INTO auth.users (id, kakao_id, nickname) VALUES (:id, :kakaoId, :nickname)")
                .param("id", id)
                .param("kakaoId", kakaoId)
                .param("nickname", "tester")
                .update()
        } else {
            jdbc.sql("INSERT INTO auth.users (id, kakao_id, nickname, role) VALUES (:id, :kakaoId, :nickname, :role)")
                .param("id", id)
                .param("kakaoId", kakaoId)
                .param("nickname", "tester")
                .param("role", role)
                .update()
        }
        return id
    }
}
