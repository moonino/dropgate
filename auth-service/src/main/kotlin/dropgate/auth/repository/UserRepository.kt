package dropgate.auth.repository

import dropgate.auth.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.Optional
import java.util.UUID

interface UserRepository : JpaRepository<UserEntity, UUID> {
    @Modifying(clearAutomatically = true)
    @Query(
        value = """
        INSERT INTO auth.users (id, kakao_id, nickname, role, created_at, last_login_at)
        VALUES (:id, :kakaoId, :nickname, 'USER', :at, :at)
        ON CONFLICT (kakao_id) DO UPDATE SET last_login_at = EXCLUDED.last_login_at
    """,
        nativeQuery = true,
    )
    fun upsert(@Param("id") id: UUID, @Param("kakaoId") kakaoId: Long, @Param("nickname") nickname: String, @Param("at") at: Instant)

    fun findByKakaoId(kakaoId: Long): Optional<UserEntity>
}
