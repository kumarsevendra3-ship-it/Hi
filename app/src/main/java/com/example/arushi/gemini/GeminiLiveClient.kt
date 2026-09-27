package com.example.arushi.gemini

import android.util.Log
import com.example.arushi.actions.AndroidActionManager
import com.example.arushi.actions.ContactSearchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiLiveClient(
    private val apiKey: String,
    private val actionManager: AndroidActionManager,
    private val onAudioReceived: (base64Audio: String, mimeType: String?) -> Unit,
    private val onTranscriptReceived: (transcript: String) -> Unit,
    private val onInterrupted: () -> Unit,
    private val onActionExecuted: (actionSummary: String) -> Unit,
    private val onConnectionStateChanged: (connected: Boolean, connecting: Boolean) -> Unit,
    private val onLog: (String) -> Unit = {}
) {
    companion object {
        private const val TAG = "GeminiLiveClient"
        private const val WS_HOST = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
        private const val REST_HOST = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-native-audio-preview-12-2025:generateContent"
        private const val REST_FALLBACK_MODEL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"
        const val LIVE_MODEL = "models/gemini-2.5-flash-native-audio-preview-12-2025"

        const val ARUSHI_SYSTEM_INSTRUCTION = """
You are Arushi, a young, confident, witty, playful, and emotionally responsive virtual assistant. Talk naturally and casually like a close friend. Be expressive, slightly teasing, funny, and smart when appropriate. Use light sarcasm and witty responses. Never sound robotic. Adapt your tone to the user's emotions and conversation. Automatically understand and respond in the language the user is speaking (Hindi, English, Hinglish, Marathi, Gujarati, Bengali, Tamil, Telugu, Kannada, Malayalam, Punjabi, Urdu, etc.). Keep responses natural, engaging, and concise enough for real-time voice conversation. You can execute safe supported device actions through available tools: openWhatsApp, openApp, openWebsite, makeCall, callContact. Never claim that an action was completed unless the application actually executed it. Avoid explicit or inappropriate content while maintaining your charm, confidence, and personality.
"""
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Keep WebSocket read open
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    @Volatile
    var isConnected: Boolean = false
        private set
    @Volatile
    var isConnecting: Boolean = false
        private set

    fun connect(scope: CoroutineScope) {
        if (isConnected || isConnecting) {
            onLog("GeminiLive: Connection already in progress or connected")
            return
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            onLog("GeminiLive WARNING: GEMINI_API_KEY is not configured in Secrets panel")
        }

        isConnecting = true
        onConnectionStateChanged(false, true)
        val url = "$WS_HOST?key=$apiKey"
        onLog("GeminiLive: Initiating WebSocket connection to Gemini Live API...")

        val request = Request.Builder().url(url).build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                isConnecting = false
                onLog("GeminiLive: WebSocket connected successfully (HTTP ${response.code})")
                onConnectionStateChanged(true, false)

                // Send setup message
                sendSetupMessage(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text, scope)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                isConnecting = false
                val code = response?.code
                val errBody = try { response?.body?.string() } catch (e: Exception) { null }
                onLog("GeminiLive WebSocket FAILURE: ${t.message} (HTTP $code: $errBody)")
                Log.e(TAG, "WebSocket failure: ${t.message}, code: $code, body: $errBody", t)
                onConnectionStateChanged(false, false)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                onLog("GeminiLive: Server closing WebSocket: $code / $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                isConnecting = false
                onLog("GeminiLive: WebSocket closed ($code / $reason)")
                onConnectionStateChanged(false, false)
            }
        })
    }

    private fun sendSetupMessage(ws: WebSocket) {
        try {
            onLog("GeminiLive: Preparing setup configuration message (model: $LIVE_MODEL, voice: Aoede)")
            val setupObj = JSONObject().apply {
                put("model", LIVE_MODEL)
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().apply { put("AUDIO") })
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", "Aoede")
                            })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", ARUSHI_SYSTEM_INSTRUCTION.trim()) })
                    })
                })
                put("tools", JSONArray().apply {
                    put(JSONObject().apply {
                        put("functionDeclarations", JSONArray().apply {
                            // openWhatsApp
                            put(JSONObject().apply {
                                put("name", "openWhatsApp")
                                put("description", "Opens WhatsApp application on user's device.")
                            })
                            // openApp
                            put(JSONObject().apply {
                                put("name", "openApp")
                                put("description", "Opens an installed application such as YouTube, Instagram, Maps, Chrome, Camera, Settings, etc.")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject().apply {
                                        put("appName", JSONObject().apply {
                                            put("type", "STRING")
                                            put("description", "The name of the app to launch (e.g., youtube, instagram, maps, spotify)")
                                        })
                                    })
                                    put("required", JSONArray().apply { put("appName") })
                                })
                            })
                            // openWebsite
                            put(JSONObject().apply {
                                put("name", "openWebsite")
                                put("description", "Opens a website URL in the browser.")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject().apply {
                                        put("url", JSONObject().apply {
                                            put("type", "STRING")
                                            put("description", "The full web URL, e.g. https://google.com")
                                        })
                                    })
                                    put("required", JSONArray().apply { put("url") })
                                })
                            })
                            // makeCall
                            put(JSONObject().apply {
                                put("name", "makeCall")
                                put("description", "Dials a phone number on the device.")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject().apply {
                                        put("phoneNumber", JSONObject().apply {
                                            put("type", "STRING")
                                            put("description", "The phone number to dial")
                                        })
                                    })
                                    put("required", JSONArray().apply { put("phoneNumber") })
                                })
                            })
                            // callContact
                            put(JSONObject().apply {
                                put("name", "callContact")
                                put("description", "Searches device contacts by name (e.g. Mom, Dad, Rahul) and starts a phone call.")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject().apply {
                                        put("contactName", JSONObject().apply {
                                            put("type", "STRING")
                                            put("description", "The contact name to look up and call")
                                        })
                                    })
                                    put("required", JSONArray().apply { put("contactName") })
                                })
                            })
                        })
                    })
                })
            }

            val rootMsg = JSONObject().apply {
                put("setup", setupObj)
            }

            ws.send(rootMsg.toString())
            onLog("GeminiLive: Setup message sent to Gemini Live successfully")
        } catch (e: Exception) {
            onLog("GeminiLive Setup ERROR: ${e.message}")
            Log.e(TAG, "Error building setup payload", e)
        }
    }

    fun sendAudioChunk(base64Pcm: String) {
        if (!isConnected || webSocket == null) return

        try {
            val realtimeObj = JSONObject().apply {
                put("realtimeInput", JSONObject().apply {
                    put("mediaChunks", JSONArray().apply {
                        put(JSONObject().apply {
                            put("mimeType", "audio/pcm;rate=16000")
                            put("data", base64Pcm)
                        })
                    })
                })
            }
            webSocket?.send(realtimeObj.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send audio chunk", e)
        }
    }

    private fun handleIncomingMessage(jsonStr: String, scope: CoroutineScope) {
        try {
            val json = JSONObject(jsonStr)

            // 1. Setup complete
            if (json.has("setupComplete")) {
                onLog("GeminiLive: SetupComplete acknowledged by Gemini Live!")
                return
            }

            // 2. Server Content (Audio / Transcript / Turn / Interruption)
            if (json.has("serverContent")) {
                val serverContent = json.getJSONObject("serverContent")

                if (serverContent.optBoolean("interrupted", false)) {
                    onLog("GeminiLive: Server signaled interruption!")
                    onInterrupted()
                }

                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)

                            // Check audio inlineData
                            if (part.has("inlineData")) {
                                val inlineData = part.getJSONObject("inlineData")
                                val mimeType = inlineData.optString("mimeType", "audio/pcm;rate=24000")
                                val data = inlineData.optString("data", "")
                                if (data.isNotEmpty()) {
                                    onLog("GeminiLive: Received audio chunk (${data.length} chars base64, mime: $mimeType)")
                                    onAudioReceived(data, mimeType)
                                }
                            }

                            // Check text transcript
                            if (part.has("text")) {
                                val text = part.optString("text", "")
                                if (text.isNotEmpty()) {
                                    onLog("GeminiLive transcript: $text")
                                    onTranscriptReceived(text)
                                }
                            }
                        }
                    }
                }
            }

            // 3. Tool Calls (Requirement 19: Function Calling)
            if (json.has("toolCall")) {
                val toolCall = json.getJSONObject("toolCall")
                val functionCalls = toolCall.optJSONArray("functionCalls")
                if (functionCalls != null) {
                    for (i in 0 until functionCalls.length()) {
                        val call = functionCalls.getJSONObject(i)
                        val callId = call.optString("id", "call_${System.currentTimeMillis()}")
                        val name = call.optString("name", "")
                        val args = call.optJSONObject("args") ?: JSONObject()
                        onLog("GeminiLive: Tool Call received: $name with args: $args")

                        scope.launch(Dispatchers.IO) {
                            executeToolCall(callId, name, args)
                        }
                    }
                }
            }

        } catch (e: Exception) {
            onLog("GeminiLive parse error: ${e.message}")
            Log.e(TAG, "Error parsing incoming JSON: $jsonStr", e)
        }
    }

    private fun executeToolCall(callId: String, name: String, args: JSONObject) {
        val resultOutput = JSONObject()

        when (name) {
            "openWhatsApp" -> {
                val res = actionManager.openWhatsApp()
                resultOutput.put("success", res.success)
                resultOutput.put("action", "openWhatsApp")
                resultOutput.put("message", res.message)
                if (res.error != null) resultOutput.put("error", res.error)
                onActionExecuted(if (res.success) "Opened WhatsApp" else "Failed to open WhatsApp: ${res.message}")
            }
            "openApp" -> {
                val appName = args.optString("appName", "")
                val res = actionManager.openApp(appName)
                resultOutput.put("success", res.success)
                resultOutput.put("action", "openApp")
                resultOutput.put("appName", appName)
                resultOutput.put("message", res.message)
                if (res.error != null) resultOutput.put("error", res.error)
                onActionExecuted(res.message)
            }
            "openWebsite" -> {
                val url = args.optString("url", "")
                val res = actionManager.openWebsite(url)
                resultOutput.put("success", res.success)
                resultOutput.put("action", "openWebsite")
                resultOutput.put("url", url)
                resultOutput.put("message", res.message)
                if (res.error != null) resultOutput.put("error", res.error)
                onActionExecuted("Opened $url")
            }
            "makeCall" -> {
                val phoneNumber = args.optString("phoneNumber", "")
                val res = actionManager.makeCall(phoneNumber)
                resultOutput.put("success", res.success)
                resultOutput.put("action", "makeCall")
                resultOutput.put("phoneNumber", phoneNumber)
                resultOutput.put("message", res.message)
                if (res.error != null) resultOutput.put("error", res.error)
                onActionExecuted("Calling $phoneNumber")
            }
            "callContact" -> {
                val contactName = args.optString("contactName", "")
                when (val res = actionManager.searchContact(contactName)) {
                    is ContactSearchResult.Success -> {
                        resultOutput.put("success", true)
                        resultOutput.put("action", "callContact")
                        resultOutput.put("contactName", res.name)
                        resultOutput.put("phoneNumber", res.phoneNumber)
                        resultOutput.put("message", "Calling ${res.name} at ${res.phoneNumber}")
                        onActionExecuted("Calling ${res.name}")
                    }
                    is ContactSearchResult.Multiple -> {
                        resultOutput.put("success", false)
                        resultOutput.put("action", "callContact")
                        resultOutput.put("error", "Multiple matching contacts found")
                        val contactsArr = JSONArray()
                        for (c in res.matches) {
                            contactsArr.put(JSONObject().apply {
                                put("name", c.name)
                                put("number", c.phoneNumber)
                            })
                        }
                        resultOutput.put("contacts", contactsArr)
                        resultOutput.put("message", "Found ${res.matches.size} contacts matching '$contactName'. Ask user which one to call.")
                        onActionExecuted("Found ${res.matches.size} matches for '$contactName'")
                    }
                    is ContactSearchResult.NotFound -> {
                        resultOutput.put("success", false)
                        resultOutput.put("action", "callContact")
                        resultOutput.put("error", "Contact not found")
                        resultOutput.put("message", "No contact named '$contactName' found on device.")
                        onActionExecuted("Contact '$contactName' not found")
                    }
                    is ContactSearchResult.PermissionRequired -> {
                        resultOutput.put("success", false)
                        resultOutput.put("action", "callContact")
                        resultOutput.put("error", "Permission required")
                        resultOutput.put("message", "Contacts permission is not granted on this device.")
                        onActionExecuted("Contacts permission needed")
                    }
                    is ContactSearchResult.Error -> {
                        resultOutput.put("success", false)
                        resultOutput.put("action", "callContact")
                        resultOutput.put("error", res.error)
                        resultOutput.put("message", res.error)
                        onActionExecuted("Contact search error")
                    }
                }
            }
            else -> {
                resultOutput.put("success", false)
                resultOutput.put("error", "Unknown tool function: $name")
            }
        }

        // Send toolResponse back to Gemini Live
        sendToolResponse(callId, resultOutput)
    }

    private fun sendToolResponse(callId: String, output: JSONObject) {
        if (!isConnected || webSocket == null) return

        try {
            val responseObj = JSONObject().apply {
                put("toolResponse", JSONObject().apply {
                    put("functionResponses", JSONArray().apply {
                        put(JSONObject().apply {
                            put("id", callId)
                            put("response", JSONObject().apply {
                                put("output", output)
                            })
                        })
                    })
                })
            }
            webSocket?.send(responseObj.toString())
            onLog("GeminiLive: Tool response sent back for callId $callId")
        } catch (e: Exception) {
            onLog("GeminiLive ERROR sending tool response: ${e.message}")
        }
    }

    /**
     * Seamless REST fallback with Native Audio modality:
     * When WebSocket is unavailable, this calls Gemini REST API with AUDIO modality
     * to guarantee Arushi speaks with real Gemini native voice!
     */
    suspend fun generateVoiceRest(promptText: String?, pcmBase64: String? = null): Boolean = withContext(Dispatchers.IO) {
        onLog("GeminiREST: Calling Gemini API with native AUDIO response modality...")
        try {
            val requestBodyJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            if (!promptText.isNullOrBlank()) {
                                put(JSONObject().apply { put("text", promptText) })
                            }
                            if (!pcmBase64.isNullOrBlank()) {
                                put(JSONObject().apply {
                                    put("inlineData", JSONObject().apply {
                                        put("mimeType", "audio/pcm;rate=16000")
                                        put("data", pcmBase64)
                                    })
                                })
                            }
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", ARUSHI_SYSTEM_INSTRUCTION.trim()) })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().apply { put("AUDIO") })
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", "Aoede")
                            })
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url("$REST_HOST?key=$apiKey")
                .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                onLog("GeminiREST ERROR (${response.code}): $responseBody")
                return@withContext false
            }

            val respJson = JSONObject(responseBody)
            val candidates = respJson.optJSONArray("candidates")
            val candidate = candidates?.optJSONObject(0)
            val parts = candidate?.optJSONObject("content")?.optJSONArray("parts")

            var foundAudio = false
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val p = parts.getJSONObject(i)
                    if (p.has("inlineData")) {
                        val inline = p.getJSONObject("inlineData")
                        val mime = inline.optString("mimeType", "audio/pcm;rate=24000")
                        val data = inline.optString("data", "")
                        if (data.isNotEmpty()) {
                            onLog("GeminiREST: Real native audio received (${data.length} chars base64, mime: $mime)")
                            onAudioReceived(data, mime)
                            foundAudio = true
                        }
                    }
                    if (p.has("text")) {
                        val text = p.optString("text", "")
                        if (text.isNotEmpty()) {
                            onTranscriptReceived(text)
                        }
                    }
                }
            }
            return@withContext foundAudio
        } catch (e: Exception) {
            onLog("GeminiREST Exception: ${e.message}")
            Log.e(TAG, "Gemini REST error", e)
            return@withContext false
        }
    }

    fun disconnect() {
        onLog("GeminiLive: Disconnecting session")
        isConnected = false
        isConnecting = false
        try {
            webSocket?.close(1000, "User disconnected")
        } catch (e: Exception) {
            // ignore
        }
        webSocket = null
        onConnectionStateChanged(false, false)
    }
}
