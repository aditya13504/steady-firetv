package com.steady.tv

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.steady.tv.ui.C
import com.steady.tv.ui.HomeScreen
import com.steady.tv.ui.PairScreen
import com.steady.tv.ui.SessionScreen
import com.steady.tv.ui.SummaryScreen

class MainActivity : ComponentActivity() {
    private lateinit var app: AppController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep the screen on during a workout; the remote may sit untouched for many minutes.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // `adb shell am start -n com.steady.tv/.MainActivity --es server http://192.168.1.20:8787` on a real Fire TV.
        val server = intent.getStringExtra("server") ?: getPreferences(MODE_PRIVATE).getString("server", null) ?: BuildConfig.DEFAULT_SERVER
        getPreferences(MODE_PRIVATE).edit().putString("server", server).apply()
        app = AppController(lifecycleScope, Speech(this), server)
        app.start()
        setContent {
            Box(Modifier.fillMaxSize().background(C.night)) {
                Crossfade(app.screen, label = "screen") { screen ->
                    when (screen) {
                        Screen.HOME -> HomeScreen(app)
                        Screen.PAIR -> PairScreen(app)
                        Screen.SESSION -> SessionScreen(app)
                        Screen.SUMMARY -> SummaryScreen(app)
                    }
                }
            }
        }
    }

    /** During a session the remote drives the workout directly; nothing on screen needs focus. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val inSession = app.screen == Screen.SESSION
        if (!inSession || event.action != KeyEvent.ACTION_DOWN) {
            if (inSession && event.keyCode in SESSION_KEYS) return true
            if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_BACK && app.screen in setOf(Screen.PAIR, Screen.SUMMARY)) {
                app.backToHome()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_SPACE -> app.onSelect()
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_NEXT -> app.onNext()
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_F -> app.simulateFall()
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> if (app.alert != null) app.resolveAlert() else app.endSessionEarly()
            else -> return super.dispatchKeyEvent(event)
        }
        return true
    }

    override fun onDestroy() {
        app.stop()
        super.onDestroy()
    }

    companion object {
        private val SESSION_KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE
        )
    }
}
