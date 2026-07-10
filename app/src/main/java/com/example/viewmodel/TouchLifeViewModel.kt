package com.example.viewmodel

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.CoachResponse
import com.example.api.GeminiManager
import com.example.speech.SpeechManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for the TouchLife vocal first-aid coach.
 */
sealed interface CoachUiState {
    object Idle : CoachUiState
    object Loading : CoachUiState
    data class Success(val response: CoachResponse) : CoachUiState
    data class Error(val message: String) : CoachUiState
}

/**
 * Represents a log entry in the active rescue session timeline.
 */
data class RescueLog(
    val id: String,
    val timestamp: String,
    val sender: String, // "Bystander" or "Vocal Coach"
    val message: String,
    val actionCode: String? = null,
    val isWarning: Boolean = false
)

class TouchLifeViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "TouchLifeViewModel"

    // Speech synthesis manager
    private var speechManager: SpeechManager? = null

    // UI state flows
    private val _uiState = MutableStateFlow<CoachUiState>(CoachUiState.Idle)
    val uiState: StateFlow<CoachUiState> = _uiState.asStateFlow()

    private val _scenario = MutableStateFlow("Road Traffic Crash")
    val scenario: StateFlow<String> = _scenario.asStateFlow()

    private val _victimStatus = MutableStateFlow("Unconscious on ground")
    val victimStatus: StateFlow<String> = _victimStatus.asStateFlow()

    private val _bystanderInput = MutableStateFlow("")
    val bystanderInput: StateFlow<String> = _bystanderInput.asStateFlow()

    // Logs history
    private val _sessionLogs = MutableStateFlow<List<RescueLog>>(emptyList())
    val sessionLogs: StateFlow<List<RescueLog>> = _sessionLogs.asStateFlow()

    // Current guidance active
    private val _currentInstruction = MutableStateFlow(
        "Ensure you are safe from oncoming traffic before approaching the victim. Place warning symbols or triangles if available."
    )
    val currentInstruction: StateFlow<String> = _currentInstruction.asStateFlow()

    private val _nextRecommendedAction = MutableStateFlow("SCENE_SAFETY")
    val nextRecommendedAction: StateFlow<String> = _nextRecommendedAction.asStateFlow()

    private val _isWarningTriggered = MutableStateFlow(true)
    val isWarningTriggered: StateFlow<Boolean> = _isWarningTriggered.asStateFlow()

    // Decoupled speech states exposed from SpeechManager
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    // Live Booking Simulated Status
    private val _responderDistance = MutableStateFlow(1.8f) // in km
    val responderDistance: StateFlow<Float> = _responderDistance.asStateFlow()

    private val _responderMinutes = MutableStateFlow(6) // in minutes
    val responderMinutes: StateFlow<Int> = _responderMinutes.asStateFlow()

    private val _responderStatus = MutableStateFlow("First-aider booked & navigating")
    val responderStatus: StateFlow<String> = _responderStatus.asStateFlow()

    private val _responderName = MutableStateFlow("Emeka Okafor (TouchLife Responder ID: 2901)")
    val responderName: StateFlow<String> = _responderName.asStateFlow()

    init {
        try {
            // Initialize speech synthesis
            speechManager = SpeechManager(application)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SpeechManager: ${e.message}", e)
            speechManager = null
        }

        // Observe speech manager state to update UI flows
        viewModelScope.launch {
            speechManager?.let { sm ->
                launch {
                    sm.isSpeaking.collect { _isSpeaking.value = it }
                }
                launch {
                    sm.isMuted.collect { _isMuted.value = it }
                }
            }
        }

        // Add initial system warning log to log list
        addLog(
            sender = "Vocal Coach",
            message = _currentInstruction.value,
            actionCode = _nextRecommendedAction.value,
            isWarning = true
        )

        // Automatically vocalize the initial safety instruction after brief delay for initialization
        Handler(Looper.getMainLooper()).postDelayed({
            speakInstruction(_currentInstruction.value)
        }, 1500)

        // Simulate active booking ETA closing in over time (decentralized rescue experience)
        startResponderSimulation()
    }

    /**
     * Updates fields
     */
    fun setScenario(newScenario: String) {
        _scenario.value = newScenario
    }

    fun setVictimStatus(newStatus: String) {
        _victimStatus.value = newStatus
    }

    fun setBystanderInput(newInput: String) {
        _bystanderInput.value = newInput
    }

    /**
     * Executes the main emergency coach reasoning loop.
     * Posts log of bystander input, queries Gemini API (or triggers local offline fallback),
     * and speaks/updates guidance results.
     */
    fun submitBystanderUpdate(customInput: String? = null) {
        val inputToUse = customInput ?: _bystanderInput.value
        if (inputToUse.trim().isEmpty()) return

        // 1. Log the bystander input
        addLog(sender = "Bystander", message = inputToUse)
        _bystanderInput.value = "" // clear input field

        _uiState.value = CoachUiState.Loading

        viewModelScope.launch {
            try {
                val currentScenarioVal = _scenario.value
                val victimStatusVal = _victimStatus.value

                Log.d(TAG, "Querying coach: Scenario=$currentScenarioVal, Status=$victimStatusVal, Input=$inputToUse")

                val result = GeminiManager.getFirstAidInstruction(
                    currentScenario = currentScenarioVal,
                    victimStatus = victimStatusVal,
                    bystanderInput = inputToUse
                )

                // Update flows
                _currentInstruction.value = result.voiceResponse
                _nextRecommendedAction.value = result.nextRecommendedAction
                _isWarningTriggered.value = result.safetyWarningTriggered
                _uiState.value = CoachUiState.Success(result)

                // Log the AI instruction
                addLog(
                    sender = if (result.isOfflineFallback) "Vocal Coach (Offline Core)" else "Vocal Coach",
                    message = result.voiceResponse,
                    actionCode = result.nextRecommendedAction,
                    isWarning = result.safetyWarningTriggered
                )

                // Speak instruction
                speakInstruction(result.voiceResponse)

                // If next action is specified, auto-update status to align with the recommendation
                when (result.nextRecommendedAction) {
                    "BLEEDING_CONTROL" -> _victimStatus.value = "Bleeding heavily, pressure being applied"
                    "CPR_CHECK" -> _victimStatus.value = "Unconscious, CPR in progress"
                    "SPINAL_ALERT" -> _victimStatus.value = "High-impact trauma, head stabilized"
                    "CHOKING_RESCUE" -> _victimStatus.value = "Choking, abdominal thrusts active"
                    "COMPLETE" -> _victimStatus.value = "Stable, waiting for responder arrival"
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed in coach loop: ${e.message}", e)
                _uiState.value = CoachUiState.Error(e.message ?: "Unknown error")
                
                // Offline fallback trigger directly on exception
                val fallback = GeminiManager.getOfflineInstruction(
                    scenario = _scenario.value,
                    status = _victimStatus.value,
                    input = inputToUse
                )
                _currentInstruction.value = fallback.voiceResponse
                _nextRecommendedAction.value = fallback.nextRecommendedAction
                _isWarningTriggered.value = fallback.safetyWarningTriggered
                
                addLog(
                    sender = "Vocal Coach (Local Safety core)",
                    message = fallback.voiceResponse,
                    actionCode = fallback.nextRecommendedAction,
                    isWarning = fallback.safetyWarningTriggered
                )
                speakInstruction(fallback.voiceResponse)
            }
        }
    }

    /**
     * Speaks the instruction out load using Android TTS.
     */
    fun speakInstruction(text: String) {
        speechManager?.speak(text)
    }

    /**
     * Retries or replays the current active instruction.
     */
    fun replayInstruction() {
        speakInstruction(_currentInstruction.value)
    }

    /**
     * Toggles speech mute.
     */
    fun toggleMute() {
        speechManager?.toggleMute()
    }

    /**
     * Clears history logs and resets session.
     */
    fun resetSession() {
        _scenario.value = "Road Traffic Crash"
        _victimStatus.value = "Unconscious on ground"
        _bystanderInput.value = ""
        _currentInstruction.value = "Ensure you are safe from oncoming traffic before approaching the victim. Place warning symbols or triangles if available."
        _nextRecommendedAction.value = "SCENE_SAFETY"
        _isWarningTriggered.value = true
        _uiState.value = CoachUiState.Idle
        _sessionLogs.value = emptyList()

        addLog(
            sender = "Vocal Coach",
            message = _currentInstruction.value,
            actionCode = _nextRecommendedAction.value,
            isWarning = true
        )

        // Reset responder booking stats
        _responderDistance.value = 1.8f
        _responderMinutes.value = 6
        _responderStatus.value = "First-aider booked & navigating"
        _responderName.value = "Emeka Okafor (TouchLife Responder ID: 2901)"

        speakInstruction(_currentInstruction.value)
    }

    private fun addLog(sender: String, message: String, actionCode: String? = null, isWarning: Boolean = false) {
        val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val newLog = RescueLog(
            id = java.util.UUID.randomUUID().toString(),
            timestamp = timeStr,
            sender = sender,
            message = message,
            actionCode = actionCode,
            isWarning = isWarning
        )
        _sessionLogs.value = _sessionLogs.value + newLog
    }

    /**
     * Simulates the decentralized responder closing the distance dynamically to enhance presentation realism.
     */
    private fun startResponderSimulation() {
        viewModelScope.launch {
            while (true) {
                delay(12000) // update every 12 seconds
                val currentDist = _responderDistance.value
                if (currentDist > 0.1f) {
                    val nextDist = String.format("%.2f", currentDist - 0.15f).toFloat()
                    _responderDistance.value = if (nextDist < 0.1f) 0.1f else nextDist
                    
                    val currentMins = _responderMinutes.value
                    if (currentMins > 1 && nextDist < currentMins * 0.3f) {
                        _responderMinutes.value = currentMins - 1
                    }
                } else {
                    _responderDistance.value = 0.0f
                    _responderMinutes.value = 0
                    _responderStatus.value = "First-aider arrived at scene!"
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechManager?.shutdown()
        speechManager = null
    }
}
