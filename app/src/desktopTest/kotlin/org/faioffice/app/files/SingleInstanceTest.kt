/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.faioffice.app.files

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class InstanceProtocolTest {
    private fun roundTrip(paths: List<String>): List<String> {
        val out = ByteArrayOutputStream()
        InstanceProtocol.write(paths, out)
        return InstanceProtocol.read(ByteArrayInputStream(out.toByteArray()))
    }

    private fun request(build: DataOutputStream.() -> Unit): ByteArrayInputStream =
        ByteArrayInputStream(ByteArrayOutputStream().also { DataOutputStream(it).build() }.toByteArray())

    private fun DataOutputStream.header(count: Int) {
        write("FAIO".encodeToByteArray())
        writeByte(InstanceProtocol.VERSION)
        writeInt(count)
    }

    @Test
    fun pathsRoundTripInOrderWithTurkishLettersAndLongPaths() {
        val long = "C:\\" + "Çalışma ğüşıöç klasörü\\".repeat(20) + "Şubat raporu.docx"
        val paths = listOf("/home/kullanıcı/Belgeler/İçerik.odt", long, "C:\\a b\\c.pdf")
        assertEquals(paths, roundTrip(paths))
        assertEquals(emptyList(), roundTrip(emptyList()))
    }

    @Test
    fun malformedRequestsAreRejected() {
        val reject = { input: ByteArrayInputStream -> assertFailsWith<IOException> { InstanceProtocol.read(input) } }
        reject(request { write("HTTP".encodeToByteArray()) })
        reject(
            request {
                write("FAIO".encodeToByteArray())
                writeByte(InstanceProtocol.VERSION + 1)
                writeInt(0)
            },
        )
        reject(request { header(IncomingFiles.MAX_FILES + 1) })
        reject(request { header(-1) })
        reject(
            request {
                header(1)
                writeInt(InstanceProtocol.MAX_PATH_BYTES + 1)
            },
        )
        reject(
            request {
                header(1)
                writeInt(0)
            },
        )
        reject(
            request {
                header(2)
                writeInt(3)
                write("abc".encodeToByteArray())
            },
        )
        reject(
            request {
                header(1)
                writeInt(2)
                write(byteArrayOf(0xC3.toByte(), 0x28))
            },
        )
        reject(request { write("FA".encodeToByteArray()) })
    }

    @Test
    fun writerRefusesWhatTheReaderWouldReject() {
        assertFailsWith<IllegalArgumentException> {
            InstanceProtocol.write(List(IncomingFiles.MAX_FILES + 1) { "a" }, ByteArrayOutputStream())
        }
    }
}

/** Both sides over a real Unix domain socket in a temporary folder (Linux and the Windows CI runner). */
class SingleInstanceTest {
    private val dir: Path = Files.createTempDirectory("fai-inst-")
    private val opened = mutableListOf<SingleInstance.Server>()

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun cleanUp() {
        opened.forEach { it.close() }
        dir.deleteRecursively()
    }

    private fun primary(requests: LinkedBlockingQueue<List<String>>): SingleInstance.Role.Primary {
        val role = assertIs<SingleInstance.Role.Primary>(SingleInstance(dir).claim(emptyList()) { requests.put(it) })
        opened += role.server
        return role
    }

    @Test
    fun secondLaunchHandsItsFilesToTheFirst() {
        val requests = LinkedBlockingQueue<List<String>>()
        primary(requests)
        val files = listOf("/home/kullanıcı/Şubat raporu.docx", "C:\\Belgeler\\tablo.xlsx")
        assertEquals(SingleInstance.Role.HandedOver, SingleInstance(dir).claim(files) { error("not primary") })
        assertEquals(files, requests.poll(5, TimeUnit.SECONDS))
        // Without files a second launch only asks for the window.
        assertEquals(SingleInstance.Role.HandedOver, SingleInstance(dir).claim(emptyList()) { error("not primary") })
        assertEquals(emptyList(), requests.poll(5, TimeUnit.SECONDS))
    }

    @Test
    fun staleSocketFileFromACrashIsReplaced() {
        Files.createDirectories(dir)
        dir.resolve("instance.sock").writeText("left by a crashed app")
        val requests = LinkedBlockingQueue<List<String>>()
        primary(requests)
        assertEquals(SingleInstance.Role.HandedOver, SingleInstance(dir).claim(listOf("/a")) { })
        assertEquals(listOf("/a"), requests.poll(5, TimeUnit.SECONDS))
    }

    @Test
    fun afterTheRunningAppClosesTheNextLaunchTakesOver() {
        val first = primary(LinkedBlockingQueue())
        first.server.close()
        val requests = LinkedBlockingQueue<List<String>>()
        primary(requests)
        assertEquals(SingleInstance.Role.HandedOver, SingleInstance(dir).claim(listOf("/b")) { })
        assertEquals(listOf("/b"), requests.poll(5, TimeUnit.SECONDS))
    }

    @Test
    fun lockHeldButNobodyListeningRunsStandalone() {
        // Lock taken, socket never bound (a frozen app): the launch gives up after its retries and opens alone.
        val lock =
            java.nio.channels.FileChannel.open(
                dir.resolve("instance.lock"),
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.WRITE,
            )
        lock.use {
            it.lock()
            assertIs<SingleInstance.Role.Standalone>(SingleInstance(dir).claim(listOf("/c")) { })
        }
    }

    @Test
    fun unusableFolderRunsStandalone() {
        val file = dir.resolve("not-a-folder").also { it.writeText("x") }
        assertIs<SingleInstance.Role.Standalone>(SingleInstance(file.resolve("sub")).claim(emptyList()) { })
    }
}
