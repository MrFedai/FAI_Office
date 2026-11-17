package org.faioffice.poc.pdf_poc

import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.renderer.FlutterUiDisplayListener

class MainActivity : FlutterActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Creates /sdcard/Android/data/<pkg>/files with the app's ownership, so run.sh can push the test PDF there.
        getExternalFilesDir(null)
        super.onCreate(savedInstanceState)
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        // First Flutter frame on screen, measured from process start with Android's own clock.
        flutterEngine.renderer.addIsDisplayingFlutterUiListener(object : FlutterUiDisplayListener {
            override fun onFlutterUiDisplayed() {
                val ms = SystemClock.uptimeMillis() - Process.getStartUptimeMillis()
                Log.i("POC", "first_flutter_frame_ms_from_process_start $ms")
                flutterEngine.renderer.removeIsDisplayingFlutterUiListener(this)
            }
            override fun onFlutterUiNoLongerDisplayed() {}
        })
    }
}
