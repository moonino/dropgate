package dropgate.order

import dropgate.order.service.DropStatusTransitionScheduler
import dropgate.order.service.OutboxRelayScheduler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.scheduling.config.ScheduledTaskHolder

@SpringBootTest(properties = ["dropgate.drops.status-scheduler.enabled=true"])
@Import(PostgresTestcontainersConfiguration::class)
class DropStatusSchedulingTest {
    @Autowired
    lateinit var taskHolders: List<ScheduledTaskHolder>

    @Test
    fun `스케줄러를 켜면 드롭 상태 전이를 주기 작업으로 등록한다`() {
        val tasks = taskHolders.flatMap { it.scheduledTasks }.map { it.task.runnable.toString() }

        assertThat(tasks).anyMatch { it.contains(DropStatusTransitionScheduler::class.java.name) }
        assertThat(tasks).noneMatch { it.contains(OutboxRelayScheduler::class.java.name) }
    }
}
