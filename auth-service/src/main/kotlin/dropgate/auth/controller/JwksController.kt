package dropgate.auth.controller

import dropgate.auth.service.JwksResponse
import dropgate.auth.service.JwtTokenService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class JwksController(private val tokens: JwtTokenService) {
    @GetMapping("/.well-known/jwks.json")
    fun getPublicKeys(): JwksResponse = tokens.getPublicKeys()
}
