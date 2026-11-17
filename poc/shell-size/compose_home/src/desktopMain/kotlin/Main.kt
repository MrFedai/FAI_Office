// Linux (JVM): prints first_frame_ms_from_process_start and rss_mb, exits after 3 s if POC_EXIT is set.
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.system.exitProcess

fun rssMb() = File("/proc/self/status").readLines().first { it.startsWith("VmRSS:") }.split(Regex("\\s+"))[1].toDouble() / 1024

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "FAI Office PoC", state = rememberWindowState(width = 800.dp, height = 600.dp)) {
        App()
        LaunchedEffect(Unit) {
            withFrameNanos { }
            withFrameNanos { }
            val start = ProcessHandle.current().info().startInstant().get().toEpochMilli()
            println("first_frame_ms_from_process_start ${System.currentTimeMillis() - start}")
            Thread.sleep(2000)
            println("rss_mb %.1f".format(java.util.Locale.ROOT, rssMb()))
            if (System.getenv("POC_EXIT") != null) exitProcess(0)
        }
    }
}
