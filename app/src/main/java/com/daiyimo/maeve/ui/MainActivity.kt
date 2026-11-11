package com.daiyimo.maeve.ui

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import com.daiyimo.maeve.MaeveApp
import com.daiyimo.maeve.i18n.AppLanguage
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.FlutterEngineCache
import io.flutter.embedding.engine.dart.DartExecutor

/** Every app entry opens the Flutter shell, including the historical component name. */
open class MainActivity : FlutterActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    override fun provideFlutterEngine(context: Context): FlutterEngine {
        FlutterEngineCache.getInstance().get(ENGINE_ID)?.let { return it }
        return FlutterEngine(applicationContext).also { engine ->
            engine.plugins.add(MaeveUiPlugin())
            engine.plugins.add(MaeveSettingsPlugin())
            engine.plugins.add(MaeveEntryPlugin())
            engine.plugins.add(MaeveChatPlugin())
            engine.dartExecutor.executeDartEntrypoint(DartExecutor.DartEntrypoint.createDefault())
            FlutterEngineCache.getInstance().put(ENGINE_ID, engine)
        }
    }

    override fun shouldDestroyEngineWithHost(): Boolean = false

    override fun onStart() {
        super.onStart()
        (application as MaeveApp).updates.autoCheckInBackground()
        (application as MaeveApp).mcp.ensureRunning()
    }

    override fun onResume() {
        super.onResume()
        MaeveUiPlugin.current?.pushStatus()
    }

    companion object {
        const val EXTRA_OPEN_CONVERSATION = "open_conversation"
        // Kept as an intent contract; app launches no longer select a Compose page.
        const val EXTRA_VISUAL_SPEC = "visual_spec"
        private const val ENGINE_ID = "maeve-main"
    }
}
