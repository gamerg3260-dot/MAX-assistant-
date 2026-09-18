package com.example.ai

import java.util.Locale

/**
 * Fast Local Match Rule Engine for MAX Assistant.
 * Evaluates incoming queries and messages instantly (0ms latency) for common greetings,
 * acknowledgments, and basic queries without triggering external Gemini API network requests.
 */
object FastLocalRuleEngine {

    private val greetingRegex = Regex("^(hi+|hello+|hey+|namaste+|pranam+|good\\s*(morning|afternoon|evening))$", RegexOption.IGNORE_CASE)
    private val howAreYouRegex = Regex("^(how\\s*are\\s*you|how\\s*r\\s*u|kaise\\s*ho|kaise\\s*hain|kya\\s*haal\\s*hai)$", RegexOption.IGNORE_CASE)
    private val thanksRegex = Regex("^(thanks|thank\\s*you|dhanyawad|shukriya|great|awesome|thanku)$", RegexOption.IGNORE_CASE)
    private val identityRegex = Regex("^(who\\s*are\\s*you|who\\s*is\\s*max|tum\\s*kaun\\s*ho|aap\\s*kaun\\s*hain|what\\s*is\\s*your\\s*name)$", RegexOption.IGNORE_CASE)
    private val affirmationRegex = Regex("^(ok|okay|alright|fine|theek\\s*hai|haan)$", RegexOption.IGNORE_CASE)
    private val byeRegex = Regex("^(bye|goodbye|alvida|see\\s*you)$", RegexOption.IGNORE_CASE)

    /**
     * Evaluates input string and returns an instant response if matched, or null if complex AI processing is required.
     */
    fun evaluate(input: String): String? {
        val clean = input.trim().lowercase(Locale.ROOT)
            .replace(Regex("[^a-zA-Z0-9\\s]"), "") // Remove punctuation
            .trim()

        if (clean.isEmpty()) return null

        return when {
            greetingRegex.matches(clean) -> {
                if (clean.contains("namaste") || clean.contains("pranam")) {
                    "Namaste! Mai MAX hu. Bataye, mai aapki kya sahayata kar sakta hu?"
                } else {
                    "Hello! How can MAX assist you today?"
                }
            }
            howAreYouRegex.matches(clean) -> {
                "I'm MAX, doing great and ready to assist you!"
            }
            thanksRegex.matches(clean) -> {
                "You're welcome! Let me know if you need anything else."
            }
            identityRegex.matches(clean) -> {
                "I am MAX, your personal AI assistant."
            }
            affirmationRegex.matches(clean) -> {
                "Got it! Let me know if you need any further help."
            }
            byeRegex.matches(clean) -> {
                "Goodbye! Have a wonderful day."
            }
            else -> null
        }
    }
}
