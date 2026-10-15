package com.siya.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Bolna (TextToSpeech) aur sunna (SpeechRecognizer). Sab main thread par chalao. */
class Voice(private val ctx: Context, private val store: Store) {
    private var ready = false
    private val tts: TextToSpeech = TextToSpeech(ctx) { ready = (it == TextToSpeech.SUCCESS) }
    private var recognizer: SpeechRecognizer? = null
    private val main = Handler(Looper.getMainLooper())

    fun speak(text: String, finished: () -> Unit = {}) {
        if (!store.voiceReplies || !ready || text.isBlank()) {
            finished()
            return
        }
        tts.language = Locale.forLanguageTag(store.language)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { main.post { finished() } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { main.post { finished() } }
        })
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "siya")
    }

    fun stopSpeaking() {
        tts.stop()
    }

    fun listen(partialCb: (String) -> Unit, resultCb: (String) -> Unit, errorCb: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            errorCb("Is phone me speech recognition available nahi hai.")
            return
        }
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onError(error: Int) {
                errorCb(
                    when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic ki permission do."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Internet check karo."
                        else -> "" // kuch suna nahi, chup-chaap wapas taiyaar
                    }
                )
            }
            override fun onResults(results: Bundle?) {
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                resultCb(t)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                if (t.isNotBlank()) partialCb(t)
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, store.language)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        r.startListening(i)
    }

    fun stopListening() {
        recognizer?.stopListening()
    }

    fun shutdown() {
        recognizer?.destroy()
        tts.shutdown()
    }
}
