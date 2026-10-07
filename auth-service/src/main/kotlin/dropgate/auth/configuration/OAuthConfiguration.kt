package dropgate.auth.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.security.SecureRandom
import java.time.Clock

private const val KAKAO_TIMEOUT_MILLIS = 3000

@Configuration(proxyBeanMethods = false)
class OAuthConfiguration {
    @Bean
    fun kakaoRestClient(): RestClient = RestClient.builder().requestFactory(
        SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(KAKAO_TIMEOUT_MILLIS)
            setReadTimeout(KAKAO_TIMEOUT_MILLIS)
        },
    ).build()

    @Bean
    fun oauthClock(): Clock = Clock.systemUTC()

    @Bean
    fun oauthRandom(): SecureRandom = SecureRandom()
}
