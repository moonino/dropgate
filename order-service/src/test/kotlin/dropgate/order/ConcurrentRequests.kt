package dropgate.order

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object ConcurrentRequests {
    private const val TIMEOUT_SECONDS = 60L

    fun <T> run(count: Int, request: (Int) -> T): List<T> {
        val startGate = CountDownLatch(1)
        Executors.newFixedThreadPool(count).use { executor ->
            val futures = List(count) { index ->
                executor.submit<T> {
                    startGate.await()
                    request(index)
                }
            }
            startGate.countDown()
            return futures.map { it.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) }
        }
    }
}
