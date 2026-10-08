package dropgate.auth.controller

import dropgate.auth.service.AccessTokenDecoder
import dropgate.auth.service.UserResponse
import dropgate.auth.service.UserService
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
class CurrentUserController(private val tokens: AccessTokenDecoder, private val users: UserService) {
    @GetMapping("/auth/me")
    fun getCurrentUser(@RequestHeader(value = HttpHeaders.AUTHORIZATION, defaultValue = "") authorization: String): UserResponse = users.getUser(tokens.decodeUserId(authorization))
}
