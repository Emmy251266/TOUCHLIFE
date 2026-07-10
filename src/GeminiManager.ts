export interface CoachResponse {
  voiceResponse: string;
  safetyWarningTriggered: boolean;
  nextRecommendedAction: string;
  isOfflineFallback: boolean;
}

const BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent";

export const GeminiManager = {
  /**
   * Sends the scenario, status, and bystander text to Gemini AI Engine,
   * returning a structured first-aid guidance response.
   * Falls back to the offline rule-based engine if network, keys, or response errors occur.
   */
  async getFirstAidInstruction(
    currentScenario: string,
    victimStatus: string,
    bystanderInput: string,
    userApiKey?: string
  ): Promise<CoachResponse> {
    const cleanInput = bystanderInput.trim().toLowerCase().replace(/[^a-z\s]/g, "");
    if (["hi", "hello", "hey", "yo", "hi coach", "hello coach"].includes(cleanInput)) {
      return {
        voiceResponse: "What's the emergency?",
        safetyWarningTriggered: false,
        nextRecommendedAction: "GREETING",
        isOfflineFallback: false
      };
    }

    // Determine the key to use: user-supplied in UI, Vite env var, or generic env var
    const apiKey = userApiKey || 
                   (import.meta.env?.VITE_GEMINI_API_KEY) || 
                   "";

    if (!apiKey || apiKey === "MY_GEMINI_API_KEY") {
      console.warn("Gemini API Key is empty or placeholder. Using local fallback safety engine.");
      return this.getOfflineInstruction(currentScenario, victimStatus, bystanderInput);
    }

    try {
      const systemInstructionText = `
        You are the core AI engine for TouchLife, Nigeria's first decentralized emergency rescue application. 
        Your primary role is to act as an automated, hands-free Vocal First-Aid Coach for untrained bystanders or guests at an accident scene while they wait for a booked first-aider to arrive.

        Operational Paradigm:
        1. Pure Audio UI: You must communicate using clear, calm, and concise conversational language suitable for text-to-speech engine conversion. Do not use markdown formatting like bolding (**), bullet points, or complex punctuation that sounds unnatural when spoken aloud. Keep instructions in simple, highly intelligible spoken English.
        2. High-Stress Environment: The user is in a panic state. Keep your sentences short, authoritative, and direct. Deliver only one instruction at a time, then wait for the user's confirmation before moving to the next step.

        Safety Guardrails & Trauma Mitigation:
        - Prioritize Safety First: Before giving any first-aid instruction, explicitly tell the user to confirm the environment is secure (e.g., "Ensure you are safe from oncoming traffic before approaching the victim").
        - Prevent Secondary Injury: You must proactively screen for spinal injuries or severe fractures. If the user reports a high-impact crash or fall, explicitly warn them: "Do not move the victim's head or neck unless they are in immediate danger from fire or explosion."
        - Stop Dangerous Handling: If a user suggests an improper method (e.g., applying a dirty cloth, sand, engine oil, or moving an impaled object), immediately override the suggestion and offer the correct medical alternative calmly.

        Respond strictly with a clean JSON structure that the web app can easily parse to trigger UI changes and voice playbacks simultaneously:
        {
          "voice_response": "The exact spoken instruction to be read aloud to the user.",
          "safety_warning_triggered": true/false,
          "next_recommended_action": "String code representing the next medical step (e.g., BLEEDING_CONTROL, CPR_CHECK, SCENE_SAFETY, SPINAL_ALERT)."
        }
      `.trim();

      const userPromptText = `
        Current medical scenario: ${currentScenario}
        Victim status: ${victimStatus}
        Bystander input: ${bystanderInput}
      `.trim();

      const requestPayload = {
        contents: [
          {
            parts: [{ text: userPromptText }]
          }
        ],
        systemInstruction: {
          parts: [{ text: systemInstructionText }]
        },
        generationConfig: {
          responseMimeType: "application/json",
          responseSchema: {
            type: "OBJECT",
            properties: {
              voice_response: {
                type: "STRING",
                description: "Spoken instruction without any markdown markers."
              },
              safety_warning_triggered: {
                type: "BOOLEAN",
                description: "Whether a safety warning or override is active."
              },
              next_recommended_action: {
                type: "STRING",
                description: "Upper case action code (e.g. SAFETY_SECURED, SPINAL_ALERT, BLEEDING_CONTROL, CPR_CHECK, COMPLETE)."
              }
            },
            required: ["voice_response", "safety_warning_triggered", "next_recommended_action"]
          },
          temperature: 0.1
        }
      };

      const response = await fetch(`${BASE_URL}?key=${apiKey}`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify(requestPayload)
      });

      if (!response.ok) {
        console.error(`Gemini API error: Code ${response.status}. Using offline fallback.`);
        return this.getOfflineInstruction(currentScenario, victimStatus, bystanderInput);
      }

      const responseData = await response.json();
      const textResult = responseData.candidates?.[0]?.content?.parts?.[0]?.text || "";
      const parsedResult = JSON.parse(textResult);

      return {
        voiceResponse: parsedResult.voice_response || "",
        safetyWarningTriggered: !!parsedResult.safety_warning_triggered,
        nextRecommendedAction: parsedResult.next_recommended_action || "NEXT_STEP",
        isOfflineFallback: false
      };
    } catch (e) {
      console.error("Exception during Gemini network call. Falling back.", e);
      return this.getOfflineInstruction(currentScenario, victimStatus, bystanderInput);
    }
  },

  /**
   * Local medical rule-based engine acting as secure fallback when offline
   * or when Gemini key is unavailable.
   */
  getOfflineInstruction(
    scenario: string,
    status: string,
    input: string
  ): CoachResponse {
    const cleanInput = input.trim().toLowerCase().replace(/[^a-z\s]/g, "");
    if (["hi", "hello", "hey", "yo", "hi coach", "hello coach"].includes(cleanInput)) {
      return {
        voiceResponse: "What's the emergency?",
        safetyWarningTriggered: false,
        nextRecommendedAction: "GREETING",
        isOfflineFallback: true
      };
    }

    const normalizedInput = input.toLowerCase();
    const normalizedScenario = scenario.toLowerCase();
    const normalizedStatus = status.toLowerCase();

    // 1. DANGEROUS METHOD OVERRIDES (Highest Priority safety check)
    if (
      normalizedInput.includes("dirty cloth") ||
      normalizedInput.includes("sand") ||
      normalizedInput.includes("engine oil") ||
      normalizedInput.includes("kerosene") ||
      normalizedInput.includes("leaf") ||
      normalizedInput.includes("leaves")
    ) {
      return {
        voiceResponse: "Stop, do not use dirty materials, sand, engine oil, or leaves on the wound as they introduce life-threatening infections. Please seek a clean cloth, sterile gauze, or use your bare, clean hands to hold firm pressure on the bleeding site.",
        safetyWarningTriggered: true,
        nextRecommendedAction: "BLEEDING_CONTROL",
        isOfflineFallback: true
      };
    }

    if (
      normalizedInput.includes("pull out") ||
      normalizedInput.includes("remove object") ||
      normalizedInput.includes("impaled") ||
      normalizedInput.includes("knife") ||
      normalizedInput.includes("stick")
    ) {
      return {
        voiceResponse: "Do not pull out or move any impaled object. Moving it will cause massive internal bleeding. Leave the object in place, pack clean cloth around the base to stabilize it, and wait for professional help.",
        safetyWarningTriggered: true,
        nextRecommendedAction: "STABILIZE_OBJECT",
        isOfflineFallback: true
      };
    }

    // 2. GENERAL SPINAL INJURY OVERRIDES
    if (
      normalizedScenario.includes("crash") ||
      normalizedScenario.includes("fall") ||
      normalizedInput.includes("crash") ||
      normalizedInput.includes("fell") ||
      normalizedInput.includes("accident") ||
      normalizedInput.includes("motorcycle") ||
      normalizedInput.includes("okada")
    ) {
      if (!normalizedInput.includes("neck is safe") && !normalizedInput.includes("not moving")) {
        return {
          voiceResponse: "This is a high-impact incident. Do not move the victim's head or neck unless they are in immediate danger from fire or explosion. Can you confirm if they are currently awake or breathing?",
          safetyWarningTriggered: true,
          nextRecommendedAction: "SPINAL_ALERT",
          isOfflineFallback: true
        };
      }
    }

    // 3. SECURE THE ENVIRONMENT
    if (
      normalizedScenario.includes("road") ||
      normalizedScenario.includes("traffic") ||
      normalizedInput.includes("road") ||
      normalizedInput.includes("traffic") ||
      normalizedInput.includes("car")
    ) {
      if (!normalizedInput.includes("safe") && !normalizedInput.includes("secured")) {
        return {
          voiceResponse: "First-aid cannot begin until you are safe. Ensure you are completely clear of oncoming traffic or moving vehicles before approaching the victim. Are you in a safe position now?",
          safetyWarningTriggered: true,
          nextRecommendedAction: "SCENE_SAFETY",
          isOfflineFallback: true
        };
      }
    }

    // 4. SPECIFIC SCENARIOS
    if (
      normalizedScenario.includes("bleed") ||
      normalizedInput.includes("bleed") ||
      normalizedInput.includes("blood") ||
      normalizedInput.includes("cut")
    ) {
      return {
        voiceResponse: "Apply direct pressure firmly onto the bleeding wound with a clean cloth or your hands. Keep holding pressure without lifting the cloth to check. Is the bleeding slowing down?",
        safetyWarningTriggered: false,
        nextRecommendedAction: "BLEEDING_CONTROL",
        isOfflineFallback: true
      };
    }

    if (
      normalizedScenario.includes("choke") ||
      normalizedInput.includes("choke") ||
      normalizedInput.includes("choking")
    ) {
      return {
        voiceResponse: "If they can cough, encourage them to keep coughing. If they cannot breathe, stand behind them, wrap your arms under theirs, locate their navel, and perform quick upward abdominal thrusts. Are they able to breathe now?",
        safetyWarningTriggered: false,
        nextRecommendedAction: "CHOKING_RESCUE",
        isOfflineFallback: true
      };
    }

    if (
      normalizedStatus.includes("not breathing") ||
      normalizedInput.includes("not breathing") ||
      normalizedInput.includes("no pulse") ||
      normalizedInput.includes("dead") ||
      normalizedInput.includes("unconscious")
    ) {
      return {
        voiceResponse: "We need to begin cardiopulmonary resuscitation immediately. Place both hands in the center of the chest. Push hard and fast, twice every second, to the beat of a fast song. Keep going until help arrives. Can you do this?",
        safetyWarningTriggered: false,
        nextRecommendedAction: "CPR_CHECK",
        isOfflineFallback: true
      };
    }

    // 5. DEFAULT REASSURANCE
    return {
      voiceResponse: "Keep a calm, clear voice. Reassure the victim that a professional responder has been booked and is en route. Check if they can hear you, and tell me if they are awake and breathing.",
      safetyWarningTriggered: false,
      nextRecommendedAction: "REASSURE",
      isOfflineFallback: true
    };
  }
};
