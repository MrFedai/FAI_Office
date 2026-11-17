// PoC WP6: the office engine as a bound service in its own process (":engine"). Throwaway (MASTER_SPEC 15).
//
// Protocol (Messenger): OPEN {path, width} -> reply OPENED {shm, w, h, parts, type, initMs, loadMs, paintMs}.
// The engine keeps one document open. When the last client unbinds, the process kills itself: LibreOfficeKit
// cannot be torn down in-process (destroy() hangs, see RESULTS.md WP4), so ending the process is how its memory
// is returned.
package org.faioffice.poc.lok

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.SharedMemory
import android.os.SystemClock
import android.util.Log
import org.libreoffice.kit.Document
import org.libreoffice.kit.LibreOfficeKit
import org.libreoffice.kit.Office
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch

const val MSG_OPEN = 1
const val MSG_OPENED = 2
const val TAG = "POC"

class EngineService : Service() {
    private lateinit var messenger: Messenger
    private var office: Office? = null
    private var doc: Document? = null
    private var initMs = -1L

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "engine_process_start_to_service_ms ${SystemClock.uptimeMillis() - Process.getStartUptimeMillis()}")
        val ready = CountDownLatch(1)
        // One thread owns LibreOfficeKit for the life of the process; large stack for deep layout recursion.
        Thread(null, {
            Looper.prepare()
            messenger = Messenger(object : Handler(Looper.myLooper()!!) {
                override fun handleMessage(m: Message) {
                    if (m.what == MSG_OPEN) open(m)
                }
            })
            ready.countDown()
            Looper.loop()
        }, "lok", 64L shl 20).start()
        ready.await()
    }

    override fun onBind(intent: Intent): IBinder = messenger.binder

    override fun onUnbind(intent: Intent): Boolean {
        Log.i(TAG, "engine_unbound, ending process")
        Process.killProcess(Process.myPid())
        return false
    }

    /** Copies assets/unpack (UNO type registries, sofficerc, fonts.conf, bundled fonts) to the data dir once. */
    private fun unpackAssets(): Long {
        val marker = File(applicationInfo.dataDir, ".unpacked-${packageManager.getPackageInfo(packageName, 0).longVersionCode}-${File(applicationInfo.sourceDir).lastModified()}")
        if (marker.exists()) return 0
        val t = SystemClock.uptimeMillis()
        fun copy(asset: String, to: File) {
            val children = assets.list(asset) ?: emptyArray()
            if (children.isEmpty()) {
                to.parentFile?.mkdirs()
                assets.open(asset).use { i -> to.outputStream().use { o -> i.copyTo(o) } }
            } else children.forEach { copy("$asset/$it", File(to, it)) }
        }
        copy("unpack", File(applicationInfo.dataDir))
        marker.createNewFile()
        val ms = SystemClock.uptimeMillis() - t
        Log.i(TAG, "engine_unpack_ms $ms")
        return ms
    }

    private var unpackMs = 0L

    private fun ensureOffice(): Office {
        office?.let { return it }
        unpackMs = unpackAssets()
        val t = SystemClock.uptimeMillis()
        LibreOfficeKit.init(this)
        val o = Office(LibreOfficeKit.getLibreOfficeKitHandle())
        initMs = SystemClock.uptimeMillis() - t
        office = o
        return o
    }

    private fun open(m: Message) {
        val path = m.data.getString("path")!!
        val width = m.data.getInt("width")
        var init = 0L
        if (office == null) { ensureOffice(); init = initMs }
        doc?.destroy()
        doc = null
        val t0 = SystemClock.uptimeMillis()
        val d = office!!.documentLoad("file://$path")
        val t1 = SystemClock.uptimeMillis()
        val reply = Message.obtain(null, MSG_OPENED)
        val data = Bundle()
        if (d == null) {
            data.putString("error", office!!.error ?: "documentLoad returned null")
        } else {
            doc = d
            d.initializeForRendering()
            val type = d.documentType
            val dw = d.documentWidth
            val dh = d.documentHeight
            // First screen, as in the Linux PoC: 1080 px wide; Writer/Calc a phone-shaped window at the top,
            // Impress the whole first slide.
            val (tw, th, h) = when (type) {
                Document.DOCTYPE_PRESENTATION, Document.DOCTYPE_DRAWING -> Triple(dw, dh, (width * dh / dw).toInt())
                Document.DOCTYPE_SPREADSHEET -> Triple(width * 15L, 1920 * 15L * width / 1080, 1920 * width / 1080)
                else -> Triple(dw, dw * 1920 / 1080, 1920 * width / 1080)
            }
            val buf = ByteBuffer.allocateDirect(width * h * 4)
            d.paintTile(buf, width, h, 0, 0, tw.toInt(), th.toInt())
            val t2 = SystemClock.uptimeMillis()
            val shm = SharedMemory.create("tile", width * h * 4)
            val map = shm.mapReadWrite()
            buf.rewind()
            map.put(buf)
            SharedMemory.unmap(map)
            data.putParcelable("shm", shm)
            data.putInt("w", width)
            data.putInt("h", h)
            data.putInt("parts", d.parts)
            data.putInt("type", type)
            data.putLong("paintMs", t2 - t1)
        }
        data.putLong("initMs", init)
        data.putLong("unpackMs", if (init > 0) unpackMs else 0)
        data.putLong("loadMs", t1 - t0)
        reply.data = data
        m.replyTo.send(reply)
    }
}
