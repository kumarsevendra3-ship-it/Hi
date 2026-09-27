package com.example.arushi.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class AudioTrackPlayer(
    private val onPlaybackStarted: () -> Unit,
    private val onPlaybackEnded: () -> Unit,
    private val onPlaybackAmplitude: (Float) -> Unit,
    private val onLog: (String) -> Unit = {}
) {
    companion object {
        private const val TAG = "ArushiAudioPlayer"
        const val DEFAULT_SAMPLE_RATE = 24000
    }

    private var audioTrack: AudioTrack? = null
    private var currentSampleRate: Int = DEFAULT_SAMPLE_RATE
    private val audioQueue = LinkedBlockingQueue<ByteArray>()
    private var playbackJob: Job? = null

    @Volatile
    var isPlaying: Boolean = false
        private set

    @Volatile
    private var isMuted: Boolean = false

    fun initialize(sampleRate: Int = DEFAULT_SAMPLE_RATE) {
        if (audioTrack != null && currentSampleRate == sampleRate && audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
            return
        }

        releaseTrack()
        currentSampleRate = sampleRate
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufferSize * 2, 8192)

        onLog("AudioPlayer: Initializing AudioTrack at $sampleRate Hz, bufferSize: $bufferSize")
        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                onLog("AudioPlayer ERROR: AudioTrack failed to initialize, state: ${audioTrack?.state}")
                releaseTrack()
            } else {
                audioTrack?.play()
                onLog("AudioPlayer: AudioTrack initialized and ready in PLAY state")
            }
        } catch (e: Exception) {
            onLog("AudioPlayer ERROR: ${e.message}")
            Log.e(TAG, "AudioTrack init error", e)
        }
    }

    fun startPlaybackWorker(scope: CoroutineScope) {
        if (playbackJob?.isActive == true) return

        playbackJob = scope.launch(Dispatchers.IO) {
            initialize(currentSampleRate)

            while (isActive) {
                val chunk = audioQueue.poll(100, TimeUnit.MILLISECONDS)
                if (chunk != null) {
                    if (!isPlaying) {
                        isPlaying = true
                        onLog("AudioPlayer: Sequential playback started")
                        onPlaybackStarted()
                    }

                    // Compute RMS amplitude for speaking visualizer
                    calculateAndPostAmplitude(chunk)

                    // Write to AudioTrack
                    try {
                        if (audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                            audioTrack?.play()
                        }
                        audioTrack?.write(chunk, 0, chunk.size)
                    } catch (e: Exception) {
                        onLog("AudioPlayer write ERROR: ${e.message}")
                    }
                } else {
                    if (isPlaying && audioQueue.isEmpty()) {
                        // Short wait before declaring playback ended to prevent stutter between chunks
                        kotlinx.coroutines.delay(120)
                        if (audioQueue.isEmpty()) {
                            isPlaying = false
                            onPlaybackAmplitude(0f)
                            onLog("AudioPlayer: Playback ended (queue drained)")
                            onPlaybackEnded()
                        }
                    }
                }
            }
        }
    }

    private fun calculateAndPostAmplitude(pcmBytes: ByteArray) {
        val sampleCount = pcmBytes.size / 2
        if (sampleCount == 0) return
        var sumSquares = 0.0
        for (i in 0 until sampleCount) {
            val low = pcmBytes[i * 2].toInt() and 0xFF
            val high = pcmBytes[i * 2 + 1].toInt()
            val sample = (high shl 8) or low
            sumSquares += sample * sample
        }
        val rms = sqrt(sumSquares / sampleCount).toFloat()
        val normalized = (rms / 32768f).coerceIn(0f, 1f)
        onPlaybackAmplitude(normalized)
    }

    fun enqueueAudioChunk(pcmBytes: ByteArray) {
        if (pcmBytes.isEmpty()) return
        onLog("AudioPlayer: Received audio chunk (${pcmBytes.size} bytes). Queue size: ${audioQueue.size + 1}")
        audioQueue.offer(pcmBytes)
    }

    fun enqueueBase64Pcm(base64Audio: String, mimeType: String? = null) {
        try {
            val pcmBytes = Base64.decode(base64Audio, Base64.DEFAULT)
            onLog("AudioPlayer: Base64 decoded successfully (${pcmBytes.size} bytes, mime: $mimeType)")

            // Check if mime specifies sample rate
            if (mimeType != null && mimeType.contains("rate=")) {
                val rateStr = mimeType.substringAfter("rate=").substringBefore(";").trim()
                rateStr.toIntOrNull()?.let { rate ->
                    if (rate != currentSampleRate && rate in 8000..48000) {
                        onLog("AudioPlayer: Adapting sample rate from $currentSampleRate to $rate")
                        initialize(rate)
                    }
                }
            }
            enqueueAudioChunk(pcmBytes)
        } catch (e: Exception) {
            onLog("AudioPlayer Base64 Decode ERROR: ${e.message}")
            Log.e(TAG, "Base64 decode failed", e)
        }
    }

    fun stopAndClearQueue() {
        val queuedItems = audioQueue.size
        audioQueue.clear()
        try {
            if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
                audioTrack?.pause()
                audioTrack?.flush()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error flushing AudioTrack", e)
        }
        if (isPlaying) {
            isPlaying = false
            onPlaybackAmplitude(0f)
            onLog("AudioPlayer: Interrupted! Cleared $queuedItems queued audio chunks.")
            onPlaybackEnded()
        }
    }

    /**
     * Requirement 15: Speaker Diagnostic Test
     * Generates a 440Hz sine wave tone and plays through the exact same output system.
     */
    fun playTestTone(frequencyHz: Int = 440, durationMs: Int = 1000) {
        onLog("SpeakerTest: Initiating 440Hz diagnostic tone test ($durationMs ms)")
        initialize(DEFAULT_SAMPLE_RATE)

        val totalSamples = (DEFAULT_SAMPLE_RATE * (durationMs / 1000.0)).toInt()
        val pcmData = ByteArray(totalSamples * 2)

        for (i in 0 until totalSamples) {
            val angle = 2.0 * PI * i * frequencyHz / DEFAULT_SAMPLE_RATE
            val sample = (sin(angle) * 30000.0).toInt().coerceIn(-32768, 32767).toShort()
            pcmData[i * 2] = (sample.toInt() and 0xFF).toByte()
            pcmData[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        onLog("SpeakerTest: Generated ${pcmData.size} bytes tone buffer. Enqueueing to speaker...")
        enqueueAudioChunk(pcmData)
    }

    private fun releaseTrack() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            // ignore
        }
        audioTrack = null
    }

    fun release() {
        stopAndClearQueue()
        playbackJob?.cancel()
        playbackJob = null
        releaseTrack()
        onLog("AudioPlayer: Released all resources")
    }
}
