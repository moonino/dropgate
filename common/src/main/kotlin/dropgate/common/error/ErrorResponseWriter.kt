package dropgate.common.error

import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import tools.jackson.databind.ObjectMapper

class ErrorResponseWriter(private val jsonMapper: ObjectMapper) {
    fun write(response: HttpServletResponse, exception: DropgateException) {
        val entity = exception.toResponseEntity()
        response.status = entity.statusCode.value()
        entity.headers.forEach { name, values -> values.forEach { response.addHeader(name, it) } }
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        response.outputStream.write(jsonMapper.writeValueAsBytes(entity.body))
    }
}
