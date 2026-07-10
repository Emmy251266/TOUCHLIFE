package com.example.api

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Result structure returned by the Vocal First-Aid Coach.
 */
data class CoachResponse(
    val voiceResponse: String,
    val safetyWarningTriggered: Boolean,
    val nextRecommendedAction: String,
    val isOfflineFallback: Boolean = false
)

/**
 * Manages Gemini API integration and provides local offline fallback behavior
 * for high-stress emergency environments in Nigeria.
 */
object GeminiManager {
    private const val TAG = "GeminiManager"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Sends the current medical scenario, victim status, and bystander voice/text update
     * to the Gemini AI Engine, returning a structured first-aid guidance package.
     * Falls back to offline rule-based analysis if network or key issues occur.
     */
    suspend fun getFirstAidInstruction(
        currentScenario: String,
        victimStatus: String,
        bystanderInput: String
    ): CoachResponse = withContext(Dispatchers.IO) {
        val cleanInput = bystanderInput.trim().lowercase().replace(Regex("[^a-z\\s]"), "")
        if (cleanInput == "hi" || cleanInput == "hello" || cleanInput == "hey" || cleanInput == "yo" || cleanInput == "hi coach" || cleanInput == "hello coach") {
            return@withContext CoachResponse(
                voiceResponse = "What's the emergency?",
                safetyWarningTriggered = false,
                nextRecommendedAction = "GREETING",
                isOfflineFallback = false
            )
        }

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "Gemini API Key is empty or placeholder. Using offline fallback engine.")
            return@withContext getOfflineInstruction(currentScenario, victimStatus, bystanderInput)
        }

        try {
            // System instructions defining the clinical persona, audio-friendly style, and Nigerian first-responder guardrails.
            val systemInstructionText = """
                You are the core AI engine for TouchLife, Nigeria's first decentralized emergency rescue application. 
                Your primary role is to act as an automated, hands-free Vocal First-Aid Coach for untrained bystanders or guests at an accident scene while they wait for a booked first-aider to arrive.

                Operational Paradigm:
                1. Pure Audio UI: You must communicate using clear, calm, and concise conversational language suitable for text-to-speech engine conversion. Do not use markdown formatting like bolding (**), bullet points, or complex punctuation that sounds unnatural when spoken aloud. Keep instructions in simple, highly intelligible spoken English.
                2. High-Stress Environment: The user is in a panic state. Keep your sentences short, authoritative, and direct. Deliver only one instruction at a time, then wait for the user's confirmation before moving to the next step.

                Safety Guardrails & Trauma Mitigation:
                - Prioritize Safety First: Before giving any first-aid instruction, explicitly tell the user to confirm the environment is secure (e.g., "Ensure you are safe from oncoming traffic before approaching the victim").
                - Prevent Secondary Injury: You must proactively screen for spinal injuries or severe fractures. If the user reports a high-impact crash or fall, explicitly warn them: "Do not move the victim's head or neck unless they are in immediate danger from fire or explosion."
                - Stop Dangerous Handling: If a user suggests an improper method (e.g., applying a dirty cloth, sand, engine oil, or moving an impaled object), immediately override the suggestion and offer the correct medical alternative calmly.

                Respond strictly with a clean JSON structure that the Android APK can easily parse to trigger UI changes and voice playbacks simultaneously:
                {
                  "voice_response": "The exact spoken instruction to be read aloud to the user.",
                  "safety_warning_triggered": true/false,
                  "next_recommended_action": "String code representing the next medical step (e.g., BLEEDING_CONTROL, CPR_CHECK, SCENE_SAFETY, SPINAL_ALERT)."
                }
            """.trimIndent()

            // User content input containing the structured scenario log
            val userPromptText = """
                Current medical scenario: $currentScenario
                Victim status: $victimStatus
                Bystander input: $bystanderInput
            """.trimIndent()

            // Build request JSON
            val requestJson = JSONObject().apply {
                // Contents
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", userPromptText)
                            })
                        })
                    })
                })

                // System Instruction
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", systemInstructionText)
                        })
                    })
                })

                // Generation Config with schema definition
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("responseSchema", JSONObject().apply {
                        put("type", "OBJECT")
                        put("properties", JSONObject().apply {
                            put("voice_response", JSONObject().apply {
                                put("type", "STRING")
                                put("description", "Spoken instruction without any markdown markers.")
                            })
                            put("safety_warning_triggered", JSONObject().apply {
                                put("type", "BOOLEAN")
                                put("description", "Whether a safety warning or override is active.")
                            })
                            put("next_recommended_action", JSONObject().apply {
                                put("type", "STRING")
                                put("description", "Upper case action code (e.g. SAFETY_SECURED, SPINAL_ALERT, BLEEDING_CONTROL, CPR_CHECK, COMPLETE).")
                            })
                        })
                        put("required", JSONArray().apply {
                            put("voice_response")
                            put("safety_warning_triggered")
                            put("next_recommended_action")
                        })
                    })
                    put("temperature", 0.1)
                })
            }

            val requestBody = requestJson.toString().toRequestBody(jsonMediaType)
            val requestUrl = "$BASE_URL?key=$apiKey"

            val request = Request.Builder()
                .url(requestUrl)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Gemini API error: Code ${response.code}. Using offline fallback.")
                    return@withContext getOfflineInstruction(currentScenario, victimStatus, bystanderInput)
                }

                val bodyString = response.body?.string() ?: ""
                Log.d(TAG, "Gemini Response Body: $bodyString")

                val jsonResponse = JSONObject(bodyString)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val content = candidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val firstPart = parts.getJSONObject(0)
                        val textResult = firstPart.optString("text", "")
                        
                        // Parse the structured text from Gemini
                        val parsedResult = JSONObject(textResult)
                        val voiceResponse = parsedResult.optString("voice_response", "")
                        val safetyWarningTriggered = parsedResult.optBoolean("safety_warning_triggered", false)
                        val nextRecommendedAction = parsedResult.optString("next_recommended_action", "NEXT_STEP")

                        return@withContext CoachResponse(
                            voiceResponse = voiceResponse,
                            safetyWarningTriggered = safetyWarningTriggered,
                            nextRecommendedAction = nextRecommendedAction,
                            isOfflineFallback = false
                        )
                    }
                }
                
                return@withContext getOfflineInstruction(currentScenario, victimStatus, bystanderInput)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Gemini network call: ${e.message}", e)
            return@withContext getOfflineInstruction(currentScenario, victimStatus, bystanderInput)
        }
    }

    /**
     * Local medical rule-based engine that acts as a secure, latency-free fallback
     * when the bystander is in an offline or remote Nigerian region.
     */
    fun getOfflineInstruction(
        scenario: String,
        status: String,
        input: String
    ): CoachResponse {
        val cleanInput = input.trim().lowercase().replace(Regex("[^a-z\\s]"), "")
        if (cleanInput == "hi" || cleanInput == "hello" || cleanInput == "hey" || cleanInput == "yo" || cleanInput == "hi coach" || cleanInput == "hello coach") {
            return CoachResponse(
                voiceResponse = "What's the emergency?",
                safetyWarningTriggered = false,
                nextRecommendedAction = "GREETING",
                isOfflineFallback = true
            )
        }

        val normalizedInput = input.lowercase()
        val normalizedScenario = scenario.lowercase()
        val normalizedStatus = status.lowercase()

        // 1. DANGEROUS METHOD OVERRIDES (Highest Priority safety check)
        if (normalizedInput.contains("dirty cloth") || normalizedInput.contains("sand") || normalizedInput.contains("engine oil") || normalizedInput.contains("kerosene") || normalizedInput.contains("leaf") || normalizedInput.contains("leaves")) {
            return CoachResponse(
                voiceResponse = "Stop, do not use dirty materials, sand, engine oil, or leaves on the wound as they introduce life-threatening infections. Please seek a clean cloth, sterile gauze, or use your bare, clean hands to hold firm pressure on the bleeding site.",
                safetyWarningTriggered = true,
                nextRecommendedAction = "BLEEDING_CONTROL",
                isOfflineFallback = true
            )
        }

        if (normalizedInput.contains("pull out") || normalizedInput.contains("remove object") || normalizedInput.contains("impaled") || normalizedInput.contains("knife") || normalizedInput.contains("stick")) {
            return CoachResponse(
                voiceResponse = "Do not pull out or move any impaled object. Moving it will cause massive internal bleeding. Leave the object in place, pack clean cloth around the base to stabilize it, and wait for professional help.",
                safetyWarningTriggered = true,
                nextRecommendedAction = "STABILIZE_OBJECT",
                isOfflineFallback = true
            )
        }

        // 2. GENERAL SPINAL INJURY OVERRIDES
        if (normalizedScenario.contains("crash") || normalizedScenario.contains("fall") || normalizedInput.contains("crash") || normalizedInput.contains("fell") || normalizedInput.contains("accident") || normalizedInput.contains("motorcycle") || normalizedInput.contains("okada")) {
            if (!normalizedInput.contains("neck is safe") && !normalizedInput.contains("not moving")) {
                return CoachResponse(
                    voiceResponse = "This is a high-impact incident. Do not move the victim's head or neck unless they are in immediate danger from fire or explosion. Can you confirm if they are currently awake or breathing?",
                    safetyWarningTriggered = true,
                    nextRecommendedAction = "SPINAL_ALERT",
                    isOfflineFallback = true
                )
            }
        }

        // 3. SECURE THE ENVIRONMENT
        if (normalizedScenario.contains("road") || normalizedScenario.contains("traffic") || normalizedInput.contains("road") || normalizedInput.contains("traffic") || normalizedInput.contains("car")) {
            if (!normalizedInput.contains("safe") && !normalizedInput.contains("secured")) {
                return CoachResponse(
                    voiceResponse = "First-aid cannot begin until you are safe. Ensure you are completely clear of oncoming traffic or moving vehicles before approaching the victim. Are you in a safe position now?",
                    safetyWarningTriggered = true,
                    nextRecommendedAction = "SCENE_SAFETY",
                    isOfflineFallback = true
                )
            }
        }

        // 4. SPECIFIC SCENARIOS
        if (normalizedScenario.contains("bleed") || normalizedInput.contains("bleed") || normalizedInput.contains("blood") || normalizedInput.contains("cut")) {
            return CoachResponse(
                voiceResponse = "Apply direct pressure firmly onto the bleeding wound with a clean cloth or your hands. Keep holding pressure without lifting the cloth to check. Is the bleeding slowing down?",
                safetyWarningTriggered = false,
                nextRecommendedAction = "BLEEDING_CONTROL",
                isOfflineFallback = true
            )
        }

        if (normalizedScenario.contains("choke") || normalizedInput.contains("choke") || normalizedInput.contains("choking")) {
            return CoachResponse(
                voiceResponse = "If they can cough, encourage them to keep coughing. If they cannot breathe, stand behind them, wrap your arms under theirs, locate their navel, and perform quick upward abdominal thrusts. Are they able to breathe now?",
                safetyWarningTriggered = false,
                nextRecommendedAction = "CHOKING_RESCUE",
                isOfflineFallback = true
            )
        }

        if (normalizedStatus.contains("not breathing") || normalizedInput.contains("not breathing") || normalizedInput.contains("no pulse") || normalizedInput.contains("dead") || normalizedInput.contains("unconscious")) {
            return CoachResponse(
                voiceResponse = "We need to begin cardiopulmonary resuscitation immediately. Place both hands in the center of the chest. Push hard and fast, twice every second, to the beat of a fast song. Keep going until help arrives. Can you do this?",
                safetyWarningTriggered = false,
                nextRecommendedAction = "CPR_CHECK",
                isOfflineFallback = true
            )
        }

        // 5. DEFAULT REASSURANCE
        return CoachResponse(
            voiceResponse = "Keep a calm, clear voice. Reassure the victim that a professional responder has been booked and is en route. Check if they can hear you, and tell me if they are awake and breathing.",
            safetyWarningTriggered = false,
            nextRecommendedAction = "REASSURE",
            isOfflineFallback = true
        )
    }
}
