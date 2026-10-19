package dropgate.order.dto

import dropgate.order.service.StorableInstantRange
import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import java.time.Instant
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [StorableInstantValidator::class])
annotation class StorableInstant(
    val message: String = "저장할 수 있는 범위를 벗어난 시각입니다",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class StorableInstantValidator : ConstraintValidator<StorableInstant, Instant> {
    override fun isValid(value: Instant?, context: ConstraintValidatorContext): Boolean = value == null || StorableInstantRange.contains(value)
}
