package dropgate.common.error

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

private val ERROR_CODE_TABLE_ROW = Regex("""^\| (\d{3}) \| ([A-Z_]+) \|.*""")

class ErrorCodeTest {
    @Test
    fun `오류 코드는 API 명세 오류 코드표와 상태까지 같다`() {
        val specification = Files.readAllLines(Path.of("..", "docs", "design", "05-API-명세.md"))
            .dropWhile { it != "## 오류 코드표" }
            .drop(1)
            .takeWhile { !it.startsWith("## ") }
            .mapNotNull { ERROR_CODE_TABLE_ROW.matchEntire(it) }
            .map { it.groupValues[2] to it.groupValues[1].toInt() }
        assertThat(ErrorCode.entries.map { it.name to it.status.value() }).containsExactlyElementsOf(specification)
    }
}
