package dropgate.common.authentication

import dropgate.common.error.DropgateException
import dropgate.common.error.ErrorCode
import org.springframework.core.MethodParameter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

class AuthenticatedUserArgumentResolver : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean = parameter.parameterType == AuthenticatedUser::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): AuthenticatedUser = webRequest.getAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST) as? AuthenticatedUser
        ?: throw DropgateException(ErrorCode.UNAUTHENTICATED, "요청에 인증된 사용자가 없습니다: ${parameter.executable.name}")
}
