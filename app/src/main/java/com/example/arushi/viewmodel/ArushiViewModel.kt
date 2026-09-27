package com.example.arushi.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.arushi.actions.AndroidActionManager
import com.example.arushi.audio.AudioRecorder
import com.example.arushi.audio.AudioTrackPlayer
import com.example.arushi.gemini.GeminiLiveClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AssistantState {
    IDLE,
    CONNECTING,
    LISTENING,
    SPEAKING,
    ERROR
}

data class ArushiUiState(
    val state: AssistantState = AssistantState.IDLE,
    val userTranscript: String = "",
    val arushiTranscript: String = "Hi! I'm Arushi. Tap the mic to talk with me in any language!",
    val executedAction: String? = null,
    val amplitude: Float = 0f,
    val isLiveConnected: Boolean = false,
    val isMicActive: Boolean = false,
    val isSpeaking: Boolean = false,
    val logs: List<String> = emptyList(),
    val errorMessage: String? = null
)

class ArushiViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ArushiViewModel"
        private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    }

    private val _uiState = MutableStateFlow(ArushiUiState())
    val uiState: StateFlow<ArushiUiState> = _uiState.asStateFlow()

    private val actionManager = AndroidActionManager(application.applicationContext)

    private val audioPlayer = AudioTrackPlayer(
        onPlaybackStarted = {
            _uiState.update { it.copy(state = AssistantState.SPEAKING, isSpeaking = true) }
        },
        onPlaybackEnded = {
            _uiState.update {
                it.copy(
                    isSpeaking = false,
                    state = if (it.isMicActive) AssistantState.LISTENING else AssistantState.IDLE
                )
            }
        },
        onPlaybackAmplitude = { amp ->
            if (_uiState.value.isSpeaking) {
                _uiState.update { it.copy(amplitude = amp) }
            }
        },
        onLog = { addLog(it) }
    )

    private val liveClient = GeminiLiveClient(
        apiKey = BuildConfig.GEMINI_API_KEY,
        actionManager = actionManager,
        onAudioReceived = { base64Audio, mimeType ->
            audioPlayer.enqueueBase64Pcm(base64Audio, mimeType)
        },
        onTranscriptReceived = { text ->
            _uiState.update {
                val current = if (it.state == AssistantState.SPEAKING) it.arushiTranscript + " " + text else text
                it.copy(arushiTranscript = current.trim())
            }
        },
        onInterrupted = {
            audioPlayer.stopAndClearQueue()
            _uiState.update {
                it.copy(
                    state = if (it.isMicActive) AssistantState.LISTENING else AssistantState.IDLE,
                    isSpeaking = false
                )
            }
        },
        onActionExecuted = { summary ->
            _uiState.update { it.copy(executedAction = summary) }
        },
        onConnectionStateChanged = { connected, connecting ->
            _uiState.update {
                it.copy(
                    isLiveConnected = connected,
                    state = when {
                        connecting -> AssistantState.CONNECTING
                        connected && it.isMicActive -> AssistantState.LISTENING
                        connected -> AssistantState.IDLE
                        else -> it.state
                    }
                )
            }
        },
        onLog = { addLog(it) }
    )

    private val audioRecorder = AudioRecorder(
        onAudioChunk = { pcmBytes, base64 ->
            if (liveClient.isConnected) {
                liveClient.sendAudioChunk(base64)
            }
        },
        onAmplitude = { amp ->
            if (!_uiState.value.isSpeaking) {
                _uiState.update { it.copy(amplitude = amp) }
            }
        },
        onLog = { addLog(it) }
    )

    init {
        audioPlayer.startPlaybackWorker(viewModelScope)
        addLog("Arushi AI Assistant initialized")
        val key = BuildConfig.GEMINI_API_KEY
        if (key.isBlank() || key == "MY_GEMINI_API_KEY") {
            addLog("Notice: Add your Gemini API key in Secrets panel for live cloud connectivity")
        } else {
            addLog("Gemini API key loaded from BuildConfig")
        }
    }

    private fun addLog(message: String) {
        val timestamp = timeFormat.format(Date())
        val entry = "[$timestamp] $message"
        Log.d(TAG, entry)
        _uiState.update {
            val updated = (listOf(entry) + it.logs).take(150)
            it.copy(logs = updated)
        }
    }

    fun toggleSession() {
        if (_uiState.value.isMicActive || _uiState.value.isLiveConnected) {
            stopSession()
        } else {
            startSession()
        }
    }

    fun startSession() {
        addLog("User started Arushi session")
        _uiState.update {
            it.copy(
                state = AssistantState.CONNECTING,
                errorMessage = null,
                executedAction = null
            )
        }

        // Connect WebSocket if not connected
        if (!liveClient.isConnected) {
            liveClient.connect(viewModelScope)
        }

        // Start mic recording
        audioRecorder.start(viewModelScope)
        _uiState.update {
            it.copy(
                isMicActive = true,
                state = if (liveClient.isConnected) AssistantState.LISTENING else AssistantState.CONNECTING
            )
        }
    }

    fun stopSession() {
        addLog("User stopped Arushi session")
        audioRecorder.stop()
        audioPlayer.stopAndClearQueue()
        liveClient.disconnect()
        _uiState.update {
            it.copy(
                state = AssistantState.IDLE,
                isMicActive = false,
                isSpeaking = false,
                isLiveConnected = false,
                amplitude = 0f
            )
        }
    }

    /**
     * Requirement 15: Speaker Diagnostic Test
     * Plays 440Hz tone through the exact same AudioTrack pipeline.
     */
    fun testSpeaker() {
        addLog("Diagnostics: Triggered Speaker Test (440Hz)")
        _uiState.update {
            it.copy(
                executedAction = "🔊 Testing Speaker (440Hz tone)",
                arushiTranscript = "Playing 440Hz diagnostic tone through speaker..."
            )
        }
        audioPlayer.playTestTone(440, 1000)
    }

    /**
     * Test natural command or text prompt via REST Native Audio modality
     */
    fun sendPrompt(prompt: String) {
        addLog("User prompt: '$prompt'")
        _uiState.update {
            it.copy(
                userTranscript = prompt,
                state = AssistantState.CONNECTING,
                errorMessage = null
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            // First check if this is an explicit direct action command to execute instantly
            val lower = prompt.lowercase()
            if (lower.contains("whatsapp")) {
                val res = actionManager.openWhatsApp()
                _uiState.update { it.copy(executedAction = res.message) }
            } else if (lower.contains("call mom") || lower.contains("mom ko call")) {
                actionManager.searchContact("Mom")
                _uiState.update { it.copy(executedAction = "Calling Mom") }
            }

            val success = liveClient.generateVoiceRest(promptText = prompt)
            if (!success && !liveClient.isConnected) {
                // If cloud is unavailable, provide friendly local voice explanation
                _uiState.update {
                    it.copy(
                        state = AssistantState.IDLE,
                        arushiTranscript = "Hey! I heard '$prompt'. Make sure your GEMINI_API_KEY is configured in Secrets to chat live with my full voice model!"
                    )
                }
            }
        }
    }

    fun clearLogs() {
        _uiState.update { it.copy(logs = emptyList()) }
    }

    fun dismissAction() {
        _uiState.update { it.copy(executedAction = null) }
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.stop()
        audioPlayer.release()
        liveClient.disconnect()
    }
}
