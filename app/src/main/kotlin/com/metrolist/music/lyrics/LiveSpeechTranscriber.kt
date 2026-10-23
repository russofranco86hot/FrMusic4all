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
import java.lang.ref.WeakReference
import java.util.Locale

enum class TranscriberLanguage(val code: String, val displayName: String, val flag: String) {
    SPANISH("es-419", "Español", "🇪🇸"),
    ENGLISH("en-US", "English", "🇺🇸"),
    PORTUGUESE("pt-BR", "Português", "🇧🇷"),
    ITALIAN("it-IT", "Italiano", "🇮🇹"),
    AUTO("", "Auto", "🌐"),
}

object LiveSpeechTranscriber {
    private var recognizer: SpeechRecognizer? = null
    private var appContext: Context? = null
    private var activeContextRef: WeakReference<Context>? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _selectedLanguage = MutableStateFlow(TranscriberLanguage.SPANISH)
    val selectedLanguage: StateFlow<TranscriberLanguage> = _selectedLanguage.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _transcribedLines = MutableStateFlow<List<String>>(emptyList())
    val transcribedLines: StateFlow<List<String>> = _transcribedLines.asStateFlow()

    private val _currentPartialText = MutableStateFlow("")
    val currentPartialText: StateFlow<String> = _currentPartialText.asStateFlow()

    private val _soundLevel = MutableStateFlow(0f)
    val soundLevel: StateFlow<Float> = _soundLevel.asStateFlow()

    private var shouldKeepListening = false
    private var currentLanguageTag: String? = null
    private var consecutiveErrors = 0

    fun setLanguage(lang: TranscriberLanguage) {
        _selectedLanguage.value = lang
        currentLanguageTag = lang.code.ifBlank { null }
        if (shouldKeepListening) {
            mainHandler.post {
                try {
                    recognizer?.cancel()
                    startListeningInternal(currentLanguageTag)
                } catch (e: Exception) {
                    recreateRecognizer()
                }
            }
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Timber.d("LiveSpeech: ready for speech")
            _isListening.value = true
            consecutiveErrors = 0
        }

        override fun onBeginningOfSpeech() {
            Timber.d("LiveSpeech: beginning of speech")
        }

        override fun onRmsChanged(rmsdB: Float) {
            val level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            _soundLevel.value = level
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Timber.d("LiveSpeech: end of speech segment")
        }

        override fun onError(error: Int) {
            Timber.w("LiveSpeech: recognition error: $error")
            val isPermissionError = error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
            if (isPermissionError) {
                _isListening.value = false
                shouldKeepListening = false
                return
            }

            if (!shouldKeepListening) {
                _isListening.value = false
                return
            }

            consecutiveErrors++
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    // Normal pauses between sentences; restart immediately without destroying recognizer
                    mainHandler.postDelayed({
                        if (shouldKeepListening) {
                            startListeningInternal(currentLanguageTag)
                        }
                    }, 150)
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    mainHandler.postDelayed({
                        if (shouldKeepListening) {
                            try { recognizer?.cancel() } catch (_: Exception) {}
                            startListeningInternal(currentLanguageTag)
                        }
                    }, 400)
                }
                else -> {
                    // If repeated client errors, recreate the instance after delay
                    val delayMs = if (consecutiveErrors > 3) 800L else 300L
                    mainHandler.postDelayed({
                        if (shouldKeepListening) {
                            if (consecutiveErrors > 3) {
                                recreateRecognizer()
                            } else {
                                startListeningInternal(currentLanguageTag)
                            }
                        }
                    }, delayMs)
                }
            }
        }

        override fun onResults(results: Bundle?) {
            consecutiveErrors = 0
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
                        startListeningInternal(currentLanguageTag)
                    }
                }, 100)
            } else {
                _isListening.value = false
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?: partialResults?.getStringArrayList("android.speech.extra.UNSTABLE_TEXT")
            val partial = matches?.firstOrNull()?.trim()
                ?: partialResults?.getString("android.speech.extra.UNSTABLE_TEXT")?.trim()
                ?: ""
            if (partial.isNotBlank()) {
                _currentPartialText.value = partial
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun start(context: Context, languageTag: String? = null) {
        val app = context.applicationContext
        appContext = app
        activeContextRef = WeakReference(context)
        currentLanguageTag = languageTag ?: _selectedLanguage.value.code.ifBlank { null }

        shouldKeepListening = true
        _isListening.value = true
        _transcribedLines.value = emptyList()
        _currentPartialText.value = ""
        _soundLevel.value = 0f
        consecutiveErrors = 0

        mainHandler.post {
            try {
                recognizer?.destroy()
                val newRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(listener)
                }
                recognizer = newRecognizer
                startListeningInternal(currentLanguageTag)
            } catch (e: Exception) {
                Timber.e(e, "LiveSpeech: failed to create speech recognizer with context, trying app context")
                try {
                    val fallbackRecognizer = SpeechRecognizer.createSpeechRecognizer(app).apply {
                        setRecognitionListener(listener)
                    }
                    recognizer = fallbackRecognizer
                    startListeningInternal(currentLanguageTag)
                } catch (e2: Exception) {
                    Timber.e(e2, "LiveSpeech: fallback recognizer creation also failed")
                    _isListening.value = false
                    Toast.makeText(context, "El servicio de voz no pudo iniciarse en este dispositivo.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startListeningInternal(languageTag: String? = null) {
        val rec = recognizer ?: run {
            recreateRecognizer()
            return
        }
        val lang = languageTag ?: _selectedLanguage.value.code.ifBlank { null } ?: "es-419"
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf(lang, "es-419", "es-ES", "es-AR", "en-US", "pt-BR", "it-IT"))
            putExtra("android.speech.extra.DICTATION_MODE", true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 10000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            appContext?.packageName?.let { pkg ->
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, pkg)
            }
        }
        try {
            rec.startListening(intent)
            _isListening.value = true
        } catch (e: Exception) {
            Timber.e(e, "LiveSpeech: startListening failed")
            recreateRecognizer()
        }
    }

    private fun recreateRecognizer() {
        if (!shouldKeepListening) return
        mainHandler.post {
            try {
                recognizer?.destroy()
                recognizer = null
                val ctx = activeContextRef?.get() ?: appContext ?: return@post
                recognizer = SpeechRecognizer.createSpeechRecognizer(ctx).apply {
                    setRecognitionListener(listener)
                }
                startListeningInternal(currentLanguageTag)
            } catch (e: Exception) {
                Timber.w(e, "LiveSpeech: error recreating recognizer")
            }
        }
    }

    fun stop() {
        shouldKeepListening = false
        _isListening.value = false
        _currentPartialText.value = ""
        _soundLevel.value = 0f
        consecutiveErrors = 0
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
