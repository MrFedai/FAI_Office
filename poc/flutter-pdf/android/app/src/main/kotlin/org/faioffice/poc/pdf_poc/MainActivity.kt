package org.faioffice.poc.pdf_poc

import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity

class MainActivity : FlutterActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Creates /sdcard/Android/data/<pkg>/files with the app's ownership, so run.sh can push the test PDF there.
        getExternalFilesDir(null)
        super.onCreate(savedInstanceState)
    }
}
