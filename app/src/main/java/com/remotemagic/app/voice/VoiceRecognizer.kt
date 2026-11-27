package com.remotemagic.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VoiceState(
    val listening: Boolean = false,
    val level: Float = 0f,
    val heard: String = "",
    val errorCode: Int? = null
)

/** Wrapper around Android SpeechRecognizer. Must be used from the main thread. */
class VoiceRecognizer(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    private val mutable = MutableStateFlow(VoiceState())
    val state = mutable.asStateFlow()

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(languageTag: String, onSentences: (List<String>) -> Unit) {
        stop()
        if (!available) {
            mutable.value = VoiceState(errorCode = SpeechRecognizer.ERROR_CLIENT)
            return
        }
        val speech = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = speech
        speech.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { mutable.value = VoiceState(listening = true) }
            override fun onRmsChanged(rmsdB: Float) {
                mutable.value = mutable.value.copy(level = (rmsdB / 10f).coerceIn(0f, 1f))
            }
            override fun onResults(results: Bundle?) {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                mutable.value = VoiceState(heard = list.firstOrNull().orEmpty())
                onSentences(list)
            }
            override fun onError(error: Int) { mutable.value = VoiceState(errorCode = error) }
            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { mutable.value = mutable.value.copy(listening = false) }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speech.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        )
    }

    fun stop() {
        recognizer?.destroy()
        recognizer = null
        mutable.value = mutable.value.copy(listening = false)
    }
}
