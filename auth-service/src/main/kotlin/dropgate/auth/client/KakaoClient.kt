package dropgate.auth.client

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode

private const val NICKNAME_MAX_LENGTH = 50

@Component
class KakaoClient(
    private val kakaoRestClient: RestClient,
    @param:Value("\${dropgate.kakao.client-id}") private val clientId: String,
    @param:Value("\${dropgate.kakao.client-secret}") private val clientSecret: String,
    @param:Value("\${dropgate.kakao.redirect-uri}") private val redirectUri: String,
    @param:Value("\${dropgate.kakao.authorization-base-url:https://kauth.kakao.com}")
    private val authorizationBaseUrl: String,
    @param:Value("\${dropgate.kakao.token-base-url:https://kauth.kakao.com}")
    private val tokenBaseUrl: String,
    @param:Value("\${dropgate.kakao.user-base-url:https://kapi.kakao.com}")
    private val userBaseUrl: String,
) {
    fun createAuthorizationRequest(state: String): OAuth2AuthorizationRequest = OAuth2AuthorizationRequest
        .authorizationCode()
        .authorizationUri("$authorizationBaseUrl/oauth/authorize")
        .clientId(clientId)
        .redirectUri(redirectUri)
        .state(state)
        .build()

    fun fetchUser(code: String): KakaoUserResponse {
        require(code.isNotBlank()) { "카카오 인가 코드가 비어 있습니다" }
        try {
            val accessToken = requestAccessToken(code)
            return parseUser(requestUser(accessToken))
        } catch (cause: RuntimeException) {
            throw KakaoUnavailableException(cause)
        }
    }

    private fun requestAccessToken(code: String): String {
        val response = requireNotNull(
            kakaoRestClient
                .post()
                .uri("$tokenBaseUrl/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(createTokenForm(code))
                .retrieve()
                .body(JsonNode::class.java),
        )
        val accessToken = response.path("access_token").stringValue("")
        require(accessToken.isNotBlank()) { "카카오 액세스 토큰이 비어 있습니다" }
        return accessToken
    }

    private fun createTokenForm(code: String): LinkedMultiValueMap<String, String> = LinkedMultiValueMap<String, String>().apply {
        add("grant_type", "authorization_code")
        add("client_id", clientId)
        add("client_secret", clientSecret)
        add("redirect_uri", redirectUri)
        add("code", code)
    }

    private fun requestUser(accessToken: String): JsonNode = requireNotNull(
        kakaoRestClient
            .get()
            .uri("$userBaseUrl/v2/user/me")
            .headers { it.setBearerAuth(accessToken) }
            .retrieve()
            .body(JsonNode::class.java),
    )

    private fun parseUser(user: JsonNode): KakaoUserResponse = KakaoUserResponse(readKakaoId(user), readNickname(user))

    private fun readKakaoId(user: JsonNode): Long {
        val id = user.path("id")
        require(id.isIntegralNumber && id.canConvertToLong() && id.longValue() > 0) {
            "카카오 사용자 ID가 유효하지 않습니다"
        }
        return id.longValue()
    }

    private fun readNickname(user: JsonNode): String {
        val nicknameNode = user.path("kakao_account").path("profile").path("nickname")
        require(nicknameNode.isMissingNode || nicknameNode.isNull || nicknameNode.isString) {
            "카카오 닉네임 형식이 유효하지 않습니다"
        }
        val nickname = nicknameNode.stringValue("")
        require(nickname.codePointCount(0, nickname.length) <= NICKNAME_MAX_LENGTH) {
            "카카오 닉네임이 50자를 초과합니다"
        }
        return nickname
    }
}

data class KakaoUserResponse(val id: Long, val nickname: String)

class KakaoUnavailableException(cause: Throwable) : RuntimeException("카카오 로그인 서버에 연결할 수 없습니다", cause)
