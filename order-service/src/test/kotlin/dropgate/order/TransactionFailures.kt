package dropgate.order

import dropgate.order.service.RedisOrderConfirmationService
import org.springframework.dao.TransientDataAccessResourceException
import org.springframework.transaction.ConfigurableTransactionManager
import org.springframework.transaction.TransactionExecution
import org.springframework.transaction.TransactionExecutionListener
import org.springframework.transaction.TransactionSystemException
import java.util.concurrent.atomic.AtomicBoolean

fun failNextCommit(manager: ConfigurableTransactionManager): AutoCloseable {
    val failed = AtomicBoolean(false)
    return installListener(
        manager,
        object : TransactionExecutionListener {
            override fun afterCommit(transaction: TransactionExecution, commitFailure: Throwable?) {
                if (isConfirmation(transaction) && commitFailure == null && failed.compareAndSet(false, true)) {
                    throw TransientDataAccessResourceException("실제 커밋 뒤 응답 실패")
                }
            }
        },
    )
}

fun failNextRollback(manager: ConfigurableTransactionManager, cause: RuntimeException): AutoCloseable = installListener(
    manager,
    object : TransactionExecutionListener {
        override fun beforeRollback(transaction: TransactionExecution) {
            if (isConfirmation(transaction)) throw TransactionSystemException("롤백 경계에서 감싼 실패", cause)
        }
    },
)

private fun isConfirmation(transaction: TransactionExecution): Boolean = transaction.transactionName == "${RedisOrderConfirmationService::class.java.name}.${RedisOrderConfirmationService::confirm.name}"

private fun installListener(manager: ConfigurableTransactionManager, listener: TransactionExecutionListener): AutoCloseable {
    val previous = manager.transactionExecutionListeners.toList()
    manager.setTransactionExecutionListeners(previous + listener)
    return AutoCloseable { manager.setTransactionExecutionListeners(previous) }
}
