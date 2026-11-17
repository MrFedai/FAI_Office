// PoC WP6: UI process. Binds the engine service (own process), opens documents one after another, shows each
// first screen, then unbinds so the engine process ends. Throwaway (MASTER_SPEC section 15).
//
// Auto mode (used by run.sh): <files>/auto lists document names, one per line. Phases are logged (tag POC) so
// run.sh can sample memory: PHASE_SAMPLE (engine up, last document open) and PHASE_UNBOUND (engine gone).
package org.faioffice.poc.lok

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.SharedMemory
import android.os.SystemClock
import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

class MainActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var image: ImageView
    private lateinit var status: TextView
    private lateinit var files: File
    private var engine: Messenger? = null
    private var docs = listOf<String>()
    private var next = 0
    private var requestAt = 0L
    private val results = linkedMapOf<String, String>()

    private fun report(k: String, v: Any) {
        results[k] = "$v"
        Log.i(TAG, "POC_RESULT $k $v")
    }

    private val replies = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(m: Message) {
            if (m.what == MSG_OPENED) shown(m.data)
        }
    })

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            engine = Messenger(binder)
            report("bind_ms", SystemClock.uptimeMillis() - requestAt)
            openNext()
        }
        override fun onServiceDisconnected(name: ComponentName) { engine = null }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        files = getExternalFilesDir(null)!!
        status = TextView(this).apply { text = "LOK PoC" }
        image = ImageView(this).apply { adjustViewBounds = true }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(status)
            addView(image)
        })
        val auto = File(files, "auto")
        if (auto.exists()) {
            docs = auto.readLines().map { it.trim() }.filter { it.isNotEmpty() }
            main.postDelayed({ start() }, 1000)
        }
    }

    private fun start() {
        requestAt = SystemClock.uptimeMillis()
        bindService(Intent(this, EngineService::class.java), conn, Context.BIND_AUTO_CREATE)
    }

    private fun openNext() {
        val name = docs[next]
        status.text = "Opening $name"
        requestAt = if (next == 0) requestAt else SystemClock.uptimeMillis() // first one includes engine start
        val m = Message.obtain(null, MSG_OPEN)
        m.data = Bundle().apply {
            putString("path", File(files, name).path)
            putInt("width", resources.displayMetrics.widthPixels)
        }
        m.replyTo = replies
        engine!!.send(m)
    }

    private fun shown(d: Bundle) {
        val name = docs[next]
        val err = d.getString("error")
        if (err != null) {
            report("open_${next + 1}_$name", "ERROR $err")
        } else {
            val shm = d.getParcelable("shm", SharedMemory::class.java)!!
            val w = d.getInt("w")
            val h = d.getInt("h")
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val buf = shm.mapReadOnly()
            bmp.copyPixelsFromBuffer(buf)
            SharedMemory.unmap(buf)
            shm.close()
            image.setImageBitmap(bmp)
            if (next == docs.size - 1) saveScreen(bmp, name)
        }
        // Report after the frame that shows it has been drawn.
        window.decorView.post {
            val total = SystemClock.uptimeMillis() - requestAt
            if (err == null) report("open_${next + 1}_$name",
                "total ${total} ms, unpack ${d.getLong("unpackMs")} ms, engine init ${d.getLong("initMs")} ms, load ${d.getLong("loadMs")} ms, " +
                    "first screen ${d.getLong("paintMs")} ms, parts ${d.getInt("parts")}")
            next++
            if (next < docs.size) openNext() else finishRun()
        }
    }

    private fun saveScreen(bmp: Bitmap, name: String) {
        File(files, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun finishRun() {
        status.text = "Done; sampling memory"
        Log.i(TAG, "PHASE_SAMPLE")
        main.postDelayed({
            unbindService(conn)
            main.postDelayed({
                Log.i(TAG, "PHASE_UNBOUND")
                main.postDelayed({
                    File(files, "results.txt").writeText(results.entries.joinToString("\n") { "${it.key} ${it.value}" })
                    Log.i(TAG, "POC_DONE")
                }, 4000)
            }, 3000)
        }, 6000)
    }
}
