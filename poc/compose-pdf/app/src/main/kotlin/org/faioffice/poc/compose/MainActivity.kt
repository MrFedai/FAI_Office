// PoC WP8: Jetpack Compose shell showing PDFium-rendered pages, same scenario and result keys as ../flutter-pdf.
// Throwaway (MASTER_SPEC section 15): a measuring harness, not product UI.
//
// Auto mode (used by run.sh): if <files>/auto exists, the app opens <files>/<pdf>, waits, scrolls through the
// whole document at a fixed speed, then writes <files>/results.txt and logs "POC_DONE" (tag POC).
package org.faioffice.poc.compose

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics
import android.view.ViewTreeObserver
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

const val TAG = "POC"

object Pdfium {
    external fun open(path: String): Int
    external fun pageSizes(): DoubleArray
    external fun render(index: Int, bitmap: Bitmap): Boolean
}

/** PDFium is not thread-safe: every call goes through this one background thread. */
val pdfThread = Executors.newSingleThreadExecutor { Thread(it, "pdfium") }.asCoroutineDispatcher()

val results = linkedMapOf<String, String>()
fun report(k: String, v: Any) {
    results[k] = "$v"
    Log.i(TAG, "POC_RESULT $k $v")
}

fun rssMb(): Double {
    val line = File("/proc/self/status").readLines().first { it.startsWith("VmRSS:") }
    return line.split(Regex("\\s+"))[1].toDouble() / 1024
}

fun pct(xs: List<Double>, p: Double): String {
    if (xs.isEmpty()) return "-"
    val s = xs.sorted()
    return "%.1f".format(java.util.Locale.ROOT, s[Math.round((s.size - 1) * p).toInt()])
}

class MainActivity : ComponentActivity() {
    private val frameTotalsMs = mutableListOf<Double>()
    private val frameDrawMs = mutableListOf<Double>()
    @Volatile var recordingFrames = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val files = getExternalFilesDir(null)!! // also creates the dir run.sh pushes into
        // First frame on screen, measured from process start with Android's own clock (same as the Flutter PoC).
        val decor = window.decorView
        decor.viewTreeObserver.addOnDrawListener(object : ViewTreeObserver.OnDrawListener {
            var done = false
            override fun onDraw() {
                if (done) return
                done = true
                decor.post {
                    Log.i(TAG, "first_frame_ms_from_process_start ${SystemClock.uptimeMillis() - Process.getStartUptimeMillis()}")
                }
            }
        })
        val metricsThread = HandlerThread("frame-metrics").apply { start() }
        window.addOnFrameMetricsAvailableListener({ _: Window, m: FrameMetrics, _: Int ->
            if (recordingFrames) synchronized(frameTotalsMs) {
                frameTotalsMs += m.getMetric(FrameMetrics.TOTAL_DURATION) / 1e6
                frameDrawMs += (m.getMetric(FrameMetrics.DRAW_DURATION) + m.getMetric(FrameMetrics.SYNC_DURATION) +
                    m.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION)) / 1e6
            }
        }, Handler(metricsThread.looper))

        val auto = File(files, "auto")
        val pdfName = auto.takeIf { it.exists() }?.readText()?.trim()?.ifEmpty { null } ?: "long-100p.pdf"
        setContent {
            MaterialTheme {
                var open by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    if (auto.exists()) {
                        delay(1500)
                        report("rss_home_mb", "%.1f".format(java.util.Locale.ROOT, rssMb()))
                        open = true
                    }
                }
                if (open) Viewer(File(files, pdfName), auto.exists(), files)
                else Home(pdfName, File(files, pdfName).exists()) { open = true }
            }
        }
    }

    fun frames(): Pair<List<Double>, List<Double>> = synchronized(frameTotalsMs) { frameTotalsMs.toList() to frameDrawMs.toList() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Home(pdf: String, present: Boolean, onOpen: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Compose PoC") }) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(onClick = onOpen, enabled = present) { Text("Open $pdf") }
            Spacer(Modifier.height(16.dp))
            if (!present) Text("Missing $pdf (push it with run.sh)")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Viewer(file: File, auto: Boolean, files: File) {
    val activity = androidx.activity.compose.LocalActivity.current as MainActivity
    val clock = remember { SystemClock.uptimeMillis() }
    var sizes by remember { mutableStateOf<List<Pair<Double, Double>>?>(null) }
    val images = remember { mutableStateMapOf<Int, ImageBitmap>() }
    val pending = remember { mutableSetOf<Int>() }
    val renderMs = remember { mutableListOf<Double>() }
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val gapPx = with(LocalDensity.current) { 8.dp.toPx() }

    LaunchedEffect(Unit) {
        withContext(pdfThread) {
            val t = SystemClock.elapsedRealtimeNanos()
            System.loadLibrary("pdfbridge")
            report("worker_start_ms", (SystemClock.elapsedRealtimeNanos() - t) / 1_000_000)
            val o = SystemClock.elapsedRealtimeNanos()
            val n = Pdfium.open(file.path)
            check(n > 0) { "PDFium open failed: $n" }
            val raw = Pdfium.pageSizes()
            report("pages", n)
            report("document_open_ms", "%.1f".format(java.util.Locale.ROOT, (SystemClock.elapsedRealtimeNanos() - o) / 1e6))
            sizes = List(n) { raw[2 * it] to raw[2 * it + 1] }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(file.name) }) }, containerColor = Color(0xFFE0E0E0)) { pad ->
        val s = sizes
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        BoxWithConstraints(Modifier.fillMaxSize().padding(pad)) {
            val widthPx = constraints.maxWidth
            val heights = remember(widthPx) { s.map { (w, h) -> (widthPx * h / w).toFloat() } }
            val density = LocalDensity.current

            fun centerPage() = state.firstVisibleItemIndex
            fun request(i: Int) {
                if (i !in s.indices || images.containsKey(i) || !pending.add(i)) return
                scope.launch {
                    val bmp = withContext(pdfThread) {
                        val b = Bitmap.createBitmap(widthPx, heights[i].toInt(), Bitmap.Config.ARGB_8888)
                        val t = SystemClock.elapsedRealtimeNanos()
                        Pdfium.render(i, b)
                        renderMs += (SystemClock.elapsedRealtimeNanos() - t) / 1e6
                        b.prepareToDraw() // upload to a GPU texture ahead of drawing, like Flutter's decode
                        b
                    }
                    pending.remove(i)
                    images[i] = bmp.asImageBitmap()
                    while (images.size > 12) {
                        val far = images.keys.maxBy { kotlin.math.abs(it - centerPage()) }
                        images.remove(far)
                    }
                    if (i == 0 && "first_page_visible_ms" !in results) {
                        withFrameNanos { }
                        report("first_page_visible_ms", SystemClock.uptimeMillis() - clock)
                        report("first_page_render_ms", "%.1f".format(java.util.Locale.ROOT, renderMs.first()))
                        report("rss_first_page_mb", "%.1f".format(java.util.Locale.ROOT, rssMb()))
                        if (auto) scope.launch { autoScroll(activity, state, s.size, heights, gapPx, images, renderMs, widthPx, files) }
                    }
                }
            }

            // Prefetch about one screen ahead and behind, like the Flutter PoC's cache extent.
            LaunchedEffect(state) {
                snapshotFlow { state.firstVisibleItemIndex to (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) }
                    .collect { (first, last) -> for (i in first - 2..last + 2) request(i) }
            }

            LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
                items(s.size) { i ->
                    val hDp = with(density) { (heights[i] + gapPx).toDp() }
                    Box(Modifier.fillMaxWidth().height(hDp).padding(bottom = 8.dp).background(Color.White)) {
                        images[i]?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
                    }
                }
            }
        }
    }
}

suspend fun autoScroll(
    activity: MainActivity, state: androidx.compose.foundation.lazy.LazyListState, pages: Int, heights: List<Float>,
    gapPx: Float, images: Map<Int, ImageBitmap>, renderMs: List<Double>, widthPx: Int, files: File,
) {
    delay(1000)
    val hz = activity.display.refreshRate
    val msPerPage = 300
    val total = heights.sum() + gapPx * pages - state.layoutInfo.viewportSize.height
    var blank = 0
    var samples = 0
    var rssPeak = 0.0
    activity.recordingFrames = true
    kotlinx.coroutines.coroutineScope {
        val sampler = launch {
            while (true) {
                rssPeak = maxOf(rssPeak, rssMb())
                for (v in state.layoutInfo.visibleItemsInfo) {
                    samples++
                    if (!images.containsKey(v.index)) blank++
                }
                delay(50)
            }
        }
        state.animateScrollBy(total, tween(pages * msPerPage, easing = LinearEasing))
        sampler.cancel()
    }
    activity.recordingFrames = false
    delay(2000)
    val budget = 1000.0 / hz
    val (totals, draws) = activity.frames()
    report("display_hz", "%.0f".format(java.util.Locale.ROOT, hz))
    report("scroll_ms_per_page", msPerPage)
    report("scroll_frames", totals.size)
    report("scroll_janky_frames", "${totals.count { it > budget }} (budget ${"%.1f".format(java.util.Locale.ROOT, budget)} ms)")
    report("scroll_frame_total_p50_p90_p99_ms", "${pct(totals, .5)} / ${pct(totals, .9)} / ${pct(totals, .99)}")
    report("scroll_draw_sync_issue_p90_ms", pct(draws, .9))
    report("scroll_blank_visible_page_pct", "%.1f".format(java.util.Locale.ROOT, 100.0 * blank / samples))
    report("render_ms_avg_p90_max", "${"%.1f".format(java.util.Locale.ROOT, renderMs.average())} / ${pct(renderMs, .9)} / ${pct(renderMs, 1.0)}")
    report("render_width_px", widthPx)
    report("rss_scroll_peak_mb", "%.1f".format(java.util.Locale.ROOT, rssPeak))
    report("rss_after_scroll_mb", "%.1f".format(java.util.Locale.ROOT, rssMb()))
    File(files, "results.txt").writeText(results.entries.joinToString("\n") { "${it.key} ${it.value}" })
    Log.i(TAG, "POC_DONE")
}
