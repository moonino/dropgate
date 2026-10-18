package dropgate.common.authentication

import dropgate.common.error.DropgateException
import dropgate.common.error.ErrorCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.servlet.HandlerInterceptor

class AdminAuthorizationInterceptor : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val user = request.getAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE) as? AuthenticatedUser
            ?: throw DropgateException(ErrorCode.UNAUTHENTICATED, "관리자 API 요청에 인증된 사용자가 없습니다: ${request.requestURI}")
        user.requireAdmin()
        return true
    }
}
