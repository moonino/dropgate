package dropgate.common.authentication

import dropgate.common.error.DropgateException
import dropgate.common.error.ErrorCode
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.net.URI

class RestClientJwkSetFetcher(private val restClient: RestClient, private val jwkSetUri: URI) : JwkSetFetcher {
    override fun fetch(): String {
        val body = try {
            restClient.get().uri(jwkSetUri).retrieve().body(String::class.java)
        } catch (exception: RestClientException) {
            throw DropgateException(ErrorCode.AUTH_UNAVAILABLE, "JWKS를 받을 수 없습니다: $jwkSetUri", cause = exception)
        }
        return body ?: throw DropgateException(ErrorCode.AUTH_UNAVAILABLE, "JWKS 응답 본문이 비어 있습니다: $jwkSetUri")
    }
}
