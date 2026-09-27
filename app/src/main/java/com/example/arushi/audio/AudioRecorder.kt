package com.example.arushi.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class AudioRecorder(
    private val onAudioChunk: (pcmBytes: ByteArray, base64: String) -> Unit,
    private val onAmplitude: (amplitude: Float) -> Unit,
    private val onLog: (String) -> Unit = {}
) {
    companion object {
        private const val TAG = "ArushiAudioRecorder"
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    @Volatile
    var isRecording: Boolean = false
        private set

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope) {
        if (isRecording) {
            onLog("AudioRecorder: Already recording")
            return
        }

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            val bufferSize = maxOf(minBufferSize, 4096)

            onLog("AudioRecorder: Initializing AudioRecord (rate=$SAMPLE_RATE, minBuffer=$minBufferSize, buffer=$bufferSize)")
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                // Fallback to MIC if VOICE_RECOGNITION fails
                audioRecord?.release()
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
            }

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                val errorMsg = "AudioRecord failed to initialize. State: ${audioRecord?.state}"
                Log.e(TAG, errorMsg)
                onLog("AudioRecorder ERROR: $errorMsg")
                audioRecord?.release()
                audioRecord = null
                return
            }

            audioRecord?.startRecording()
            isRecording = true
            onLog("AudioRecorder: Microphone started successfully")

            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ShortArray(1024)
                val byteBuffer = ByteArray(buffer.size * 2)

                while (isActive && isRecording) {
                    val readSamples = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readSamples > 0) {
                        // Calculate RMS amplitude for visualizer
                        var sumSquares = 0.0
                        for (i in 0 until readSamples) {
                            val sample = buffer[i]
                            sumSquares += sample * sample
                            // Convert short to little-endian bytes
                            byteBuffer[i * 2] = (sample.toInt() and 0xFF).toByte()
                            byteBuffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                        }
                        val rms = sqrt(sumSquares / readSamples).toFloat()
                        val normalizedAmplitude = (rms / 32768f).coerceIn(0f, 1f)
                        onAmplitude(normalizedAmplitude)

                        val actualBytes = if (readSamples == buffer.size) {
                            byteBuffer
                        } else {
                            byteBuffer.copyOf(readSamples * 2)
                        }

                        val base64 = Base64.encodeToString(actualBytes, Base64.NO_WRAP)
                        onAudioChunk(actualBytes, base64)
                    }
                }
            }
        } catch (e: Exception) {
            val err = "AudioRecorder start failed: ${e.message}"
            Log.e(TAG, err, e)
            onLog("AudioRecorder ERROR: $err")
            stop()
        }
    }

    fun stop() {
        if (!isRecording && audioRecord == null) return
        onLog("AudioRecorder: Stopping microphone")
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioRecord", e)
        }
        audioRecord = null
        onAmplitude(0f)
        onLog("AudioRecorder: Microphone stopped")
    }
}
