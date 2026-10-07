package dropgate.auth.service

import dropgate.auth.client.KakaoUserResponse
import dropgate.auth.repository.UserRepository
import dropgate.common.UuidV7Generator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

private const val NICKNAME_SUFFIX_LENGTH = 6

@Service
class UserService(private val users: UserRepository, private val identifiers: UuidV7Generator, private val clock: Clock) {
    @Transactional
    fun storeUser(kakao: KakaoUserResponse): UserResponse {
        val nickname = kakao.nickname.ifBlank { "user${kakao.id.toString().takeLast(NICKNAME_SUFFIX_LENGTH)}" }
        users.upsert(identifiers.generate(), kakao.id, nickname, clock.instant())
        val stored = users.findByKakaoId(kakao.id).orElseThrow { IllegalStateException("저장된 카카오 사용자를 찾을 수 없습니다: ${kakao.id}") }
        return UserResponse(stored.id, stored.nickname, stored.role)
    }
}

data class UserResponse(val id: UUID, val nickname: String, val role: String)
