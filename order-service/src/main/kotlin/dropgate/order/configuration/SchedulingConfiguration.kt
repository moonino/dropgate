package dropgate.order.configuration

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnBooleanProperty("dropgate.drops.status-scheduler.enabled", matchIfMissing = true)
class SchedulingConfiguration
