package com.steady.tv

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** The coach's voice. Counts interrupt each other; sentences queue politely. */
class Speech(context: Context) {
    private var ready = false
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts.language = Locale.US
            tts.setSpeechRate(0.92f)
        }
    }
    var muted = false

    fun say(text: String, interrupt: Boolean = true) {
        if (!ready || muted || text.isBlank()) return
        tts.speak(text, if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, text.hashCode().toString())
    }

    fun stop() {
        if (ready) tts.stop()
    }

    fun shutdown() = tts.shutdown()
}

private val NUMBER_WORDS = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty")

fun spokenCount(n: Int): String = NUMBER_WORDS.getOrNull(n) ?: n.toString()
