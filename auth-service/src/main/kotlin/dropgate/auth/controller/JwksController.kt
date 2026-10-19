package dropgate.auth.controller

import dropgate.auth.dto.JwkResponse
import dropgate.auth.dto.JwksResponse
import dropgate.auth.service.JwtTokenService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class JwksController(private val tokens: JwtTokenService) {
    @GetMapping("/.well-known/jwks.json")
    fun getPublicKeys(): JwksResponse = JwksResponse(tokens.getPublicKeys().map(JwkResponse::createFrom))
}
