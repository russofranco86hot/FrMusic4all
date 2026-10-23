/**
 * FrMusic4all (C) 2026
 * Licensed under GPL-3.0
 */

package com.metrolist.music.lyrics

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.Locale

object LiveSpeechTranscriber {
    private var recognizer: SpeechRecognizer? = null
    private var appContext: Context? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _transcribedLines = MutableStateFlow<List<String>>(emptyList())
    val transcribedLines: StateFlow<List<String>> = _transcribedLines.asStateFlow()

    private val _currentPartialText = MutableStateFlow("")
    val currentPartialText: StateFlow<String> = _currentPartialText.asStateFlow()

    private var shouldKeepListening = false
    private var currentLanguageTag: String? = null

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Timber.d("LiveSpeech: ready for speech")
            _isListening.value = true
        }

        override fun onBeginningOfSpeech() {
            Timber.d("LiveSpeech: beginning of speech")
        }

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Timber.d("LiveSpeech: end of speech segment")
        }

        override fun onError(error: Int) {
            Timber.w("LiveSpeech: recognition error: $error")
            val isPermissionError = error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
            if (shouldKeepListening && !isPermissionError) {
                // Delay restart to let audio hardware/recognizer recycle cleanly
                mainHandler.postDelayed({
                    if (shouldKeepListening) {
                        restartListening()
                    }
                }, 500)
            } else {
                _isListening.value = false
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val recognizedText = matches?.firstOrNull()?.trim().orEmpty()
            if (recognizedText.isNotBlank()) {
                val current = _transcribedLines.value.toMutableList()
                current.add(recognizedText)
                _transcribedLines.value = current
            }
            _currentPartialText.value = ""

            if (shouldKeepListening) {
                mainHandler.postDelayed({
                    if (shouldKeepListening) {
                        restartListening()
                    }
                }, 200)
            } else {
                _isListening.value = false
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = matches?.firstOrNull()?.trim().orEmpty()
            if (partial.isNotBlank()) {
                _currentPartialText.value = partial
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun start(context: Context, languageTag: String? = null) {
        val app = context.applicationContext
        appContext = app
        currentLanguageTag = languageTag

        shouldKeepListening = true
        _isListening.value = true
        _transcribedLines.value = emptyList()
        _currentPartialText.value = ""

        mainHandler.post {
            try {
                recognizer?.destroy()
                val newRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(listener)
                }
                recognizer = newRecognizer
                startListeningInternal(languageTag)
            } catch (e: Exception) {
                Timber.e(e, "LiveSpeech: failed to create speech recognizer")
                try {
                    val fallbackRecognizer = SpeechRecognizer.createSpeechRecognizer(app).apply {
                        setRecognitionListener(listener)
                    }
                    recognizer = fallbackRecognizer
                    startListeningInternal(languageTag)
                } catch (e2: Exception) {
                    Timber.e(e2, "LiveSpeech: fallback recognizer creation also failed")
                    _isListening.value = false
                    Toast.makeText(context, "El servicio de voz no pudo iniciarse en este dispositivo.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startListeningInternal(languageTag: String? = null) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            val lang = languageTag ?: Locale.getDefault().toLanguageTag()
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
            appContext?.packageName?.let { pkg ->
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, pkg)
            }
        }
        try {
            recognizer?.startListening(intent)
            _isListening.value = true
        } catch (e: Exception) {
            Timber.e(e, "LiveSpeech: startListening failed")
            _isListening.value = false
        }
    }

    private fun restartListening() {
        if (!shouldKeepListening) return
        mainHandler.post {
            try {
                recognizer?.cancel()
                startListeningInternal(currentLanguageTag)
            } catch (e: Exception) {
                Timber.w(e, "LiveSpeech: error restarting listener")
            }
        }
    }

    fun stop() {
        shouldKeepListening = false
        _isListening.value = false
        _currentPartialText.value = ""
        mainHandler.post {
            try {
                recognizer?.stopListening()
                recognizer?.cancel()
                recognizer?.destroy()
                recognizer = null
            } catch (e: Exception) {
                Timber.e(e, "LiveSpeech: error stopping recognizer")
            }
        }
    }

    fun clear() {
        _transcribedLines.value = emptyList()
        _currentPartialText.value = ""
    }
}
