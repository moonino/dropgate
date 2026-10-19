package dropgate.order

import dropgate.order.service.DropStatusTransitionScheduler
import dropgate.order.service.OutboxRelayScheduler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.scheduling.config.FixedDelayTask
import org.springframework.scheduling.config.ScheduledTaskHolder
import java.time.Duration

@SpringBootTest(properties = ["dropgate.outbox.relay.enabled=true"])
@Import(PostgresTestcontainersConfiguration::class)
class OutboxRelaySchedulingTest {
    @Autowired
    lateinit var context: ApplicationContext

    @Test
    fun `드롭 스케줄러를 꺼도 릴레이를 500밀리초 간격으로 등록한다`() {
        val tasks = context.getBeansOfType(ScheduledTaskHolder::class.java).values
            .flatMap { it.scheduledTasks }.map { it.task }
        val relayTasks = tasks.filter { it.runnable.toString().contains(OutboxRelayScheduler::class.java.name) }

        assertThat(relayTasks).hasSize(1)
        assertThat((relayTasks.single() as FixedDelayTask).intervalDuration).isEqualTo(Duration.ofMillis(500))
        assertThat(tasks).noneMatch { it.runnable.toString().contains(DropStatusTransitionScheduler::class.java.name) }
    }
}

@SpringBootTest(properties = ["dropgate.outbox.relay.enabled=false"])
@Import(PostgresTestcontainersConfiguration::class)
class OutboxRelayDisabledTest {
    @Autowired
    lateinit var context: ApplicationContext

    @Test
    fun `릴레이를 끄면 주기 작업을 등록하지 않는다`() {
        val tasks = context.getBeansOfType(ScheduledTaskHolder::class.java).values
            .flatMap { it.scheduledTasks }.map { it.task.runnable.toString() }

        assertThat(tasks).noneMatch { it.contains(OutboxRelayScheduler::class.java.name) }
    }
}
