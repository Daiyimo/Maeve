package com.daiyimo.maeve.ui

import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity

/**
 * Opt-in Flutter workbench while Compose pages migrate screen by screen.
 * Launch with: adb shell am start -n com.daiyimo.maeve/.ui.FlutterShellActivity
 */
class FlutterShellActivity : FlutterActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
}
