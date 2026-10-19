package dropgate.auth.dto

data class TokenPairResponse(val accessToken: String, val refreshToken: String, val expiresIn: Long, val tokenType: String)
