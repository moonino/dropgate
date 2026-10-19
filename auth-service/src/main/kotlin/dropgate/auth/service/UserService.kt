package dropgate.auth.service

import dropgate.auth.client.KakaoUser
import dropgate.auth.entity.UserEntity
import dropgate.auth.repository.UserRepository
import dropgate.common.UuidV7Generator
import dropgate.common.error.DropgateException
import dropgate.common.error.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

private const val NICKNAME_SUFFIX_LENGTH = 6

@Service
class UserService(private val users: UserRepository, private val identifiers: UuidV7Generator, private val clock: Clock) {
    @Transactional(readOnly = true)
    fun getUser(id: UUID): UserEntity = users.findById(id).orElseThrow { DropgateException(ErrorCode.UNAUTHENTICATED, "토큰에 해당하는 사용자를 찾을 수 없습니다: $id") }

    @Transactional
    fun storeUser(kakao: KakaoUser): UserEntity {
        val nickname = kakao.nickname.ifBlank { "user${kakao.id.toString().takeLast(NICKNAME_SUFFIX_LENGTH)}" }
        users.upsert(identifiers.generate(), kakao.id, nickname, clock.instant())
        return users.findByKakaoId(kakao.id).orElseThrow { IllegalStateException("저장된 카카오 사용자를 찾을 수 없습니다: ${kakao.id}") }
    }
}
