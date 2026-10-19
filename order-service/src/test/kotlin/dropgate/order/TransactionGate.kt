package dropgate.order

import org.springframework.transaction.ConfigurableTransactionManager
import org.springframework.transaction.TransactionExecution
import org.springframework.transaction.TransactionExecutionListener
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private const val GATE_TIMEOUT_SECONDS = 5L

class TransactionGate(private val manager: ConfigurableTransactionManager, private val point: Point) : AutoCloseable {
    enum class Point {
        BEFORE_BEGIN,
        AFTER_COMMIT,
    }

    private val previous = manager.transactionExecutionListeners.toList()
    private val executor = Executors.newSingleThreadExecutor()
    private val worker = AtomicReference<Thread>()
    private val armed = AtomicBoolean(true)
    private val reached = CountDownLatch(1)
    private val released = CountDownLatch(1)

    init {
        manager.setTransactionExecutionListeners(
            previous + object : TransactionExecutionListener {
                override fun beforeBegin(transaction: TransactionExecution) {
                    if (point == Point.BEFORE_BEGIN) pauseWorker()
                }

                override fun afterCommit(transaction: TransactionExecution, commitFailure: Throwable?) {
                    if (point == Point.AFTER_COMMIT && commitFailure == null) pauseWorker()
                }
            },
        )
    }

    fun <T> start(action: () -> T): Future<T> = executor.submit<T> {
        worker.set(Thread.currentThread())
        action()
    }

    fun awaitPaused() {
        check(reached.await(GATE_TIMEOUT_SECONDS, SECONDS)) { "예약 이후 DB 트랜잭션 경계에 도달하지 못했습니다" }
    }

    fun release() {
        released.countDown()
    }

    override fun close() {
        release()
        executor.shutdown()
        if (!executor.awaitTermination(GATE_TIMEOUT_SECONDS, SECONDS)) executor.shutdownNow()
        manager.setTransactionExecutionListeners(previous)
    }

    private fun pauseWorker() {
        if (worker.get() !== Thread.currentThread() || !armed.compareAndSet(true, false)) return
        reached.countDown()
        check(released.await(GATE_TIMEOUT_SECONDS, SECONDS)) { "DB 트랜잭션 경계의 대기를 풀지 않았습니다" }
    }
}
