package dropgate.order.configuration

import dropgate.common.authentication.AccessTokenVerifier
import dropgate.common.authentication.AdminAuthorizationInterceptor
import dropgate.common.authentication.AuthenticatedUserArgumentResolver
import dropgate.common.authentication.JwkSetCache
import dropgate.common.authentication.JwtAuthenticationFilter
import dropgate.common.authentication.PublicEndpoint
import dropgate.common.authentication.RestClientJwkSetFetcher
import dropgate.common.error.ErrorResponseWriter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.time.Clock

private const val JWKS_TIMEOUT_MILLIS = 3000

private const val ADMIN_PATH_PATTERN = "/api/admin/**"

private val ORDER_PUBLIC_ENDPOINTS = listOf(
    PublicEndpoint(HttpMethod.GET, "/api/drops"),
    PublicEndpoint(HttpMethod.GET, "/api/drops/{dropId}"),
    PublicEndpoint(HttpMethod.GET, "/actuator/health/**"),
)

@Configuration(proxyBeanMethods = false)
class AuthenticationConfiguration : WebMvcConfigurer {
    @Bean
    fun accessTokenVerifier(@Value("\${dropgate.auth.jwks-uri}") jwkSetUri: URI, clock: Clock): AccessTokenVerifier {
        val fetcher = RestClientJwkSetFetcher(createJwksRestClient(), jwkSetUri)
        return AccessTokenVerifier(JwkSetCache(fetcher, clock), clock)
    }

    @Bean
    fun jwtAuthenticationFilter(verifier: AccessTokenVerifier, jsonMapper: ObjectMapper): JwtAuthenticationFilter = JwtAuthenticationFilter(verifier, ORDER_PUBLIC_ENDPOINTS, ErrorResponseWriter(jsonMapper))

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(AuthenticatedUserArgumentResolver())
    }

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(AdminAuthorizationInterceptor()).addPathPatterns(ADMIN_PATH_PATTERN)
    }

    private fun createJwksRestClient(): RestClient = RestClient.builder().requestFactory(
        SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(JWKS_TIMEOUT_MILLIS)
            setReadTimeout(JWKS_TIMEOUT_MILLIS)
        },
    ).build()
}
