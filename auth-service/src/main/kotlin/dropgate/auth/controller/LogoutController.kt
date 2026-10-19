package dropgate.auth.controller

import dropgate.auth.service.AccessTokenDecoder
import dropgate.auth.service.TokenLogoutService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class LogoutController(private val decoder: AccessTokenDecoder, private val tokens: TokenLogoutService) {
    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, defaultValue = "") authorization: String) = tokens.logout(decoder.decode(authorization))
}
