/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.files

import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Timer
import kotlin.concurrent.schedule
import kotlin.concurrent.thread

/**
 * One app per user: the first launch holds a lock file in the user's app data folder ([dir]) and listens on a
 * Unix domain socket beside it (JDK 16+, Windows 10 1803+ and Linux; a file in the folder, no network port). A
 * later launch finds the lock taken, sends its files over the socket ([InstanceProtocol]) and exits; the running
 * app queues them and brings its window forward.
 *
 * When neither works (folder not writable, sockets unsupported, socket path too long or not encodable, the running
 * app not answering) the launch runs on its own ([Role.Standalone]): files always open, only the hand-over is lost.
 */
class SingleInstance(
    private val dir: Path,
    private val log: (String) -> Unit = {},
) {
    sealed interface Role {
        /** This process is the running app; [server] receives later launches until closed. */
        class Primary(
            val server: Server,
        ) : Role

        /** The files went to the running app; this process should exit. */
        data object HandedOver : Role

        /** No single instance this time; open the files here. */
        data class Standalone(
            val reason: String,
        ) : Role
    }

    /** The listening side; [close] stops it and releases the lock (the process exit does both anyway). */
    class Server internal constructor(
        private val lock: FileLock,
        private val channel: ServerSocketChannel,
        private val socket: Path,
    ) : AutoCloseable {
        override fun close() {
            runCatching { channel.close() }
            runCatching { Files.deleteIfExists(socket) }
            runCatching { lock.channel().close() }
        }
    }

    private val lockFile: Path = dir.resolve("instance.lock")
    private val socketFile: Path = dir.resolve("instance.sock")

    /**
     * Becomes the running app or hands [paths] (absolute) to it. [onRequest] runs on a background thread for every
     * later launch, with its paths (possibly empty: only bring the window forward).
     */
    fun claim(
        paths: List<String>,
        onRequest: (List<String>) -> Unit,
    ): Role {
        val lock =
            try {
                Files.createDirectories(dir)
                tryLock()
            } catch (e: IOException) {
                return Role.Standalone("lock: ${e::class.simpleName}")
            }
        return if (lock != null) listen(lock, onRequest) else handOver(paths)
    }

    private fun tryLock(): FileLock? {
        val channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        val lock =
            try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null // held by this JVM already (tests run both sides in one process)
            }
        if (lock == null) channel.close()
        return lock
    }

    private fun listen(
        lock: FileLock,
        onRequest: (List<String>) -> Unit,
    ): Role {
        val channel =
            try {
                // The lock says no other app listens: a socket file left by a crashed one is stale.
                Files.deleteIfExists(socketFile)
                ServerSocketChannel.open(StandardProtocolFamily.UNIX).bind(UnixDomainSocketAddress.of(socketFile))
            } catch (e: Exception) {
                // UnsupportedOperationException (old Windows), InvalidPathException (socket path not encodable),
                // IOException (path too long for a socket address, folder not writable).
                lock.channel().close()
                return Role.Standalone("socket: ${e::class.simpleName}")
            }
        thread(name = "fai-single-instance", isDaemon = true) {
            while (channel.isOpen) {
                val client =
                    try {
                        channel.accept()
                    } catch (_: IOException) {
                        break // closed
                    }
                thread(name = "fai-single-instance-request", isDaemon = true) { serve(client, onRequest) }
            }
        }
        return Role.Primary(Server(lock, channel, socketFile))
    }

    private fun serve(
        client: SocketChannel,
        onRequest: (List<String>) -> Unit,
    ) {
        client.use {
            withDeadline(it) {
                try {
                    val paths = InstanceProtocol.read(Channels.newInputStream(it))
                    onRequest(paths)
                    Channels.newOutputStream(it).apply {
                        write(InstanceProtocol.ACCEPTED)
                        flush()
                    }
                } catch (e: IOException) {
                    log("single instance: request rejected (${e.message})")
                }
            }
        }
    }

    private fun handOver(paths: List<String>): Role {
        // The running app may still be starting (lock taken, socket not bound yet): retry for a short while.
        var last = "no answer"
        repeat(CONNECT_ATTEMPTS) { attempt ->
            if (attempt > 0) Thread.sleep(CONNECT_PAUSE_MS)
            try {
                SocketChannel.open(UnixDomainSocketAddress.of(socketFile)).use { channel ->
                    val accepted =
                        withDeadline(channel) {
                            InstanceProtocol.write(paths, Channels.newOutputStream(channel))
                            Channels.newInputStream(channel).read() == InstanceProtocol.ACCEPTED
                        }
                    if (accepted) return Role.HandedOver
                    last = "refused"
                }
            } catch (e: Exception) {
                last = e::class.simpleName.orEmpty()
            }
        }
        return Role.Standalone("hand-over: $last")
    }

    companion object {
        private const val CONNECT_ATTEMPTS = 20
        private const val CONNECT_PAUSE_MS = 100L

        /** A peer that stops sending or answering is cut off after this long. */
        private const val DEADLINE_MS = 3_000L

        private val watchdog by lazy { Timer("fai-single-instance-deadline", true) }

        /** Runs [block] on [channel]; closes the channel (failing a blocked read) when it takes too long. */
        private fun <T> withDeadline(
            channel: SocketChannel,
            block: () -> T,
        ): T {
            val task = watchdog.schedule(DEADLINE_MS) { runCatching { channel.close() } }
            try {
                return block()
            } finally {
                task.cancel()
            }
        }
    }
}
