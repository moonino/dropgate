package dropgate.settlement

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SettlementBatchApplication

fun main(args: Array<String>) {
    runApplication<SettlementBatchApplication>(*args)
}
