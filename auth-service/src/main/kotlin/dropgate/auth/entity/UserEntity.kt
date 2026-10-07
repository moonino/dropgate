package dropgate.auth.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users", schema = "auth")
class UserEntity(
    @Id val id: UUID,
    @Column(name = "kakao_id", nullable = false) val kakaoId: Long,
    @Column(nullable = false, length = 50) val nickname: String,
    @Column(nullable = false, length = 20) val role: String,
    @Column(name = "created_at", nullable = false) val createdAt: Instant,
    @Column(name = "last_login_at", nullable = false) val lastLoginAt: Instant,
)
