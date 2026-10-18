package dropgate.common.authentication

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpMethod
import org.springframework.web.util.ServletRequestPathUtils
import org.springframework.web.util.pattern.PathPatternParser

class PublicEndpoint(private val method: HttpMethod, pathPattern: String) {
    private val pattern = PathPatternParser.defaultInstance.parse(pathPattern)

    fun matches(request: HttpServletRequest): Boolean = method.matches(request.method) && pattern.matches(ServletRequestPathUtils.parse(request).pathWithinApplication())
}
