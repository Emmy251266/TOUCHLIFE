package com.example.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Handles Text-To-Speech voice output in a calm, clear, authoritative tone for emergencies.
 */
class SpeechManager(context: Context) : TextToSpeech.OnInitListener {
    private val TAG = "SpeechManager"
    private var tts: TextToSpeech? = null
    
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private var isInitialized = false

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to instantiate TextToSpeech: ${e.message}", e)
            tts = null
            isInitialized = false
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.ENGLISH)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Language is not supported or missing data")
            } else {
                isInitialized = true
                // Configure calm, clear speaking properties
                tts?.setPitch(0.95f) // Slightly lower pitch is perceived as calmer and more authoritative
                tts?.setSpeechRate(0.85f) // Slower speaking rate to ensure comprehension under panic/noise

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        _isSpeaking.value = false
                        Log.e(TAG, "TTS Error code: $errorCode")
                    }
                })
            }
        } else {
            Log.e(TAG, "Initialization of TTS failed")
        }
    }

    /**
     * Speaks the given instruction. Aborts any ongoing speech first.
     */
    fun speak(text: String) {
        if (_isMuted.value) {
            Log.d(TAG, "Muted. Not speaking: $text")
            return
        }
        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized yet. Skipping speech.")
            return
        }

        try {
            // Clean text of any accidental markdown or symbols to ensure smooth text-to-speech reading
            val cleanText = text
                .replace("*", "")
                .replace("_", "")
                .replace("#", "")
                .replace("-", " ")
                .replace("\n", " ")

            _isSpeaking.value = true
            tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "TouchLifeUtterance")
        } catch (e: Exception) {
            Log.e(TAG, "Error speaking: ${e.message}", e)
            _isSpeaking.value = false
        }
    }

    /**
     * Stops the current speech.
     */
    fun stop() {
        if (isInitialized) {
            tts?.stop()
            _isSpeaking.value = false
        }
    }

    /**
     * Toggles mute state. If muted, stops any ongoing speech.
     */
    fun toggleMute() {
        val newMute = !_isMuted.value
        _isMuted.value = newMute
        if (newMute) {
            stop()
        }
    }

    /**
     * Shuts down and releases the TTS engine.
     */
    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS: ${e.message}", e)
        }
    }
}
