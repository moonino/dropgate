package dropgate.auth.configuration

import dropgate.common.error.GlobalExceptionHandler
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration(proxyBeanMethods = false)
@Import(GlobalExceptionHandler::class)
class ErrorResponseConfiguration
