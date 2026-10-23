package com.steady.tv

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.steady.tv.data.HouseholdStore
import com.steady.tv.pose.ExerciseId
import com.steady.tv.ui.C
import com.steady.tv.ui.M
import com.steady.tv.ui.MainScreen
import com.steady.tv.ui.PairScreen
import com.steady.tv.ui.SessionScreen
import com.steady.tv.ui.SetupScreen
import com.steady.tv.ui.SplashScreen
import com.steady.tv.ui.SummaryScreen
import com.steady.tv.ui.WelcomeScreen
import java.io.File

class MainActivity : ComponentActivity() {
    private lateinit var app: AppController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep the screen on during a workout; the remote may sit untouched for many minutes.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val prefs = getPreferences(MODE_PRIVATE)
        // `--es server https://...` points a TV at another companion; otherwise the saved choice, then the build default.
        val server = intent.getStringExtra("server") ?: prefs.getString("server", null) ?: BuildConfig.DEFAULT_SERVER
        prefs.edit().putString("server", server).apply()
        // `--ei hour 9` pins the household clock for demos and recordings; `--ei hour -1` clears it.
        if (intent.hasExtra("hour")) prefs.edit().putInt("hour", intent.getIntExtra("hour", -1)).apply()
        val hour = prefs.getInt("hour", -1).takeIf { it in 0..23 }

        app = AppController(lifecycleScope, Speech(this), HouseholdStore(File(filesDir, "household.json")), server, hour)
        app.onServerChanged = { prefs.edit().putString("server", it).apply() }
        app.start()
        // Scripted first-run setup for automated tests: --es setup "Ravi,84,m,Priya,daughter,KNEE_PAIN;WALKING_AID"
        intent.getStringExtra("setup")?.split(",")?.takeIf { it.size >= 3 }?.let { f ->
            lifecycleScope.launch {
                kotlinx.coroutines.delay(AppController.SPLASH_MS + 300)
                val conditions = f.getOrNull(5)?.split(";")?.mapNotNull { n -> com.steady.tv.data.Condition.entries.firstOrNull { it.name == n.trim() } }?.toSet() ?: emptySet()
                app.addPerson(f[0], f[1].toInt(), f[2].startsWith("f"), "", f.getOrNull(3), f.getOrNull(4), conditions)
            }
        }
        intent.getStringExtra("session")?.let { spec ->
            val items = spec.split(",").mapNotNull { part ->
                val kv = part.split(":")
                runCatching { ExerciseId.valueOf(kv[0].trim()) }.getOrNull()?.let { id ->
                    com.steady.tv.data.PlanItem(id, kv.getOrNull(1)?.trim()?.toIntOrNull() ?: com.steady.tv.pose.Catalog.info(id).defaultTarget)
                }
            }
            lifecycleScope.launch {
                kotlinx.coroutines.delay(AppController.SPLASH_MS + 400)
                if (app.household == null) app.exploreDemo()
                app.beginCustom(items)
            }
        }
        intent.getStringExtra("demo")?.let { person ->
            app.autoDemo(person, intent.getStringExtra("exercise")?.let { runCatching { ExerciseId.valueOf(it) }.getOrNull() })
        }
        setContent {
            Box(Modifier.fillMaxSize().background(C.bg)) {
                AnimatedContent(
                    app.screen, label = "screen",
                    transitionSpec = { (fadeIn(tween(380, easing = M.emphasized)) + scaleIn(tween(420, easing = M.emphasized), initialScale = 0.97f)) togetherWith fadeOut(tween(200)) }
                ) { screen ->
                    when (screen) {
                        Screen.SPLASH -> SplashScreen()
                        Screen.WELCOME -> WelcomeScreen(app)
                        Screen.SETUP -> SetupScreen(app)
                        Screen.MAIN -> MainScreen(app)
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
        val down = event.action == KeyEvent.ACTION_DOWN
        if (app.screen == Screen.SESSION) {
            if (!down) return if (event.keyCode in SESSION_KEYS) true else super.dispatchKeyEvent(event)
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
        if (down && event.keyCode == KeyEvent.KEYCODE_BACK) {
            when {
                app.screen == Screen.PAIR || app.screen == Screen.SUMMARY || app.screen == Screen.SETUP -> {
                    app.backToHome()
                    return true
                }
                app.screen == Screen.MAIN && app.tab != Tab.HOME -> {
                    app.tab = Tab.HOME
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
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
