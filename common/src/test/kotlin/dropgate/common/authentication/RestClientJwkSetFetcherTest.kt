package dropgate.common.authentication

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import dropgate.common.error.DropgateException
import dropgate.common.error.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.URI

class RestClientJwkSetFetcherTest {
    private val fetcher = RestClientJwkSetFetcher(RestClient.create(), URI.create(wiremock.baseUrl() + JWKS_PATH))

    @BeforeEach
    fun resetJwks() {
        wiremock.resetAll()
    }

    @Test
    fun `JWKS 응답 본문을 그대로 돌려준다`() {
        wiremock.stubFor(WireMock.get(JWKS_PATH).willReturn(WireMock.okJson("""{"keys":[]}""")))
        assertThat(fetcher.fetch()).isEqualTo("""{"keys":[]}""")
    }

    @Test
    fun `JWKS 서버가 실패하면 AUTH_UNAVAILABLE 예외에 주소를 남긴다`() {
        wiremock.stubFor(WireMock.get(JWKS_PATH).willReturn(WireMock.serverError()))
        val exception = assertThrows<DropgateException> { fetcher.fetch() }
        assertThat(exception.errorCode).isEqualTo(ErrorCode.AUTH_UNAVAILABLE)
        assertThat(exception).hasMessageContaining(JWKS_PATH)
    }

    @Test
    fun `JWKS 응답이 읽기 제한 시간을 넘으면 AUTH_UNAVAILABLE로 거부한다`() {
        wiremock.stubFor(WireMock.get(JWKS_PATH).willReturn(WireMock.okJson("""{"keys":[]}""").withFixedDelay(SLOW_RESPONSE_MILLIS)))
        val impatient = RestClientJwkSetFetcher(createRestClient(readTimeoutMillis = READ_TIMEOUT_MILLIS), URI.create(wiremock.baseUrl() + JWKS_PATH))
        val exception = assertThrows<DropgateException> { impatient.fetch() }
        assertThat(exception.errorCode).isEqualTo(ErrorCode.AUTH_UNAVAILABLE)
    }

    @Test
    fun `JWKS 응답 본문이 비어 있으면 AUTH_UNAVAILABLE로 거부한다`() {
        wiremock.stubFor(WireMock.get(JWKS_PATH).willReturn(WireMock.noContent()))
        val exception = assertThrows<DropgateException> { fetcher.fetch() }
        assertThat(exception.errorCode).isEqualTo(ErrorCode.AUTH_UNAVAILABLE)
    }

    private fun createRestClient(readTimeoutMillis: Int): RestClient = RestClient.builder()
        .requestFactory(SimpleClientHttpRequestFactory().apply { setReadTimeout(readTimeoutMillis) })
        .build()

    companion object {
        private const val JWKS_PATH = "/.well-known/jwks.json"
        private const val READ_TIMEOUT_MILLIS = 100
        private const val SLOW_RESPONSE_MILLIS = 1000
        private val wiremock = WireMockServer(0).apply { start() }

        @JvmStatic
        @AfterAll
        fun stopJwks() = wiremock.stop()
    }
}
