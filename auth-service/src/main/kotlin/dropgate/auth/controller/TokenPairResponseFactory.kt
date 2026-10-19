package dropgate.auth.controller

import dropgate.auth.dto.TokenPairResponse
import dropgate.auth.service.IssuedTokens

private const val BEARER_TOKEN_TYPE = "Bearer"

internal fun createTokenPairResponse(issued: IssuedTokens): TokenPairResponse = TokenPairResponse(issued.accessToken, issued.refreshToken, issued.accessTokenTtl.seconds, BEARER_TOKEN_TYPE)
