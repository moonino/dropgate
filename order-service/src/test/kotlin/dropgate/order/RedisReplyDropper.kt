package dropgate.order

import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class RedisReplyDropper(private val targetHost: String, private val targetPort: Int) : AutoCloseable {
    private val listener = ServerSocket(0)
    private val workers = Executors.newVirtualThreadPerTaskExecutor()
    private val connections = ConcurrentHashMap.newKeySet<Socket>()
    private val closed = AtomicBoolean()
    private val replyDropRequested = AtomicBoolean()
    val port: Int get() = listener.localPort

    init {
        workers.submit {
            while (!closed.get()) {
                try {
                    forward(listener.accept())
                } catch (exception: IOException) {
                    if (!closed.get()) throw exception
                }
            }
        }
    }

    fun dropNextReply() {
        replyDropRequested.set(true)
    }

    private fun forward(client: Socket) {
        val upstream = Socket()
        upstream.connect(InetSocketAddress(targetHost, targetPort), CONNECT_TIMEOUT_MILLIS)
        connections.add(client)
        connections.add(upstream)
        relayCommands(client, upstream)
        relayReplies(client, upstream)
    }

    private fun relayCommands(client: Socket, upstream: Socket) {
        workers.submit {
            try {
                client.getInputStream().transferTo(upstream.getOutputStream())
            } catch (_: IOException) {
                return@submit
            } finally {
                closeConnection(client, upstream)
            }
        }
    }

    private fun relayReplies(client: Socket, upstream: Socket) {
        workers.submit {
            try {
                val buffer = ByteArray(REPLY_BUFFER_BYTES)
                val replies = upstream.getInputStream()
                var size = replies.read(buffer)
                while (size >= 0) {
                    if (replyDropRequested.compareAndSet(true, false)) return@submit
                    client.getOutputStream().write(buffer, 0, size)
                    size = replies.read(buffer)
                }
            } catch (_: IOException) {
                return@submit
            } finally {
                closeConnection(client, upstream)
            }
        }
    }

    private fun closeConnection(client: Socket, upstream: Socket) {
        client.close()
        upstream.close()
        connections.remove(client)
        connections.remove(upstream)
    }

    override fun close() {
        closed.set(true)
        listener.close()
        connections.forEach(Socket::close)
        workers.shutdownNow()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 1_000
        const val REPLY_BUFFER_BYTES = 8_192
    }
}
