package dropgate.order.controller

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

private const val CLOSE_AT_FIELD = "closeAt"

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [CloseAfterOpenValidator::class])
annotation class CloseAfterOpen(
    val message: String = "마감 시각은 오픈 시각보다 늦어야 합니다",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class CloseAfterOpenValidator : ConstraintValidator<CloseAfterOpen, CreateDropRequest> {
    override fun isValid(request: CreateDropRequest, context: ConstraintValidatorContext): Boolean {
        val openAt = request.openAt ?: return true
        val closeAt = request.closeAt ?: return true
        if (closeAt.truncateToStoredPrecision().isAfter(openAt.truncateToStoredPrecision())) return true
        context.disableDefaultConstraintViolation()
        context.buildConstraintViolationWithTemplate(context.defaultConstraintMessageTemplate)
            .addPropertyNode(CLOSE_AT_FIELD)
            .addConstraintViolation()
        return false
    }
}
