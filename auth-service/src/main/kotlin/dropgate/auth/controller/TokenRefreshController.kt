package dropgate.auth.controller

import dropgate.auth.dto.RefreshTokenRequest
import dropgate.auth.dto.TokenPairResponse
import dropgate.auth.service.TokenRefreshService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class TokenRefreshController(private val tokens: TokenRefreshService) {
    @PostMapping("/auth/token/refresh")
    fun refresh(@RequestBody request: RefreshTokenRequest): TokenPairResponse = createTokenPairResponse(tokens.refresh(request.refreshToken))
}
