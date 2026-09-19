package com.example.function

/**
 * Categories of dynamic functions executable by MAX Assistant.
 */
enum class FunctionCategory {
    HARDWARE_CONTROL,
    APPLICATION_LAUNCH,
    TELEPHONY_CALL,
    MESSAGING,
    CAMERA_CAPTURE,
    ACCESSIBILITY_NAV,
    DEVICE_UTILITY,
    COMPOSITE_MACRO
}

/**
 * Parameter definition for a dynamic function.
 */
data class FunctionParameter(
    val name: String,
    val type: String, // "string", "number", "integer", "boolean"
    val description: String,
    val isRequired: Boolean = true,
    val allowedValues: List<String>? = null
)

/**
 * Execution result returned by a dynamic function.
 */
data class FunctionExecutionResult(
    val isSuccess: Boolean,
    val resultSummary: String,
    val feedbackSpeech: String = resultSummary,
    val data: Map<String, Any?> = emptyMap(),
    val error: String? = null
)

/**
 * Definition of a callable dynamic function in MAX Assistant.
 */
data class DynamicFunction(
    val name: String,
    val description: String,
    val category: FunctionCategory,
    val parameters: List<FunctionParameter> = emptyList(),
    val isProtected: Boolean = false,
    val execute: suspend (args: Map<String, Any?>) -> FunctionExecutionResult
)

/**
 * Individual planned step in a multi-step dynamic action sequence.
 */
data class PlannedActionStep(
    val stepIndex: Int,
    val functionName: String,
    val arguments: Map<String, Any?>,
    val description: String,
    var isExecuted: Boolean = false,
    var result: FunctionExecutionResult? = null
)

/**
 * Full action sequence formulated dynamically by the assistant.
 */
data class DynamicActionPlan(
    val planId: String,
    val originalQuery: String,
    val steps: List<PlannedActionStep>,
    val formulatedVia: String, // "LOCAL_EDGE_DECOMPOSER" or "GEMINI_FUNCTION_CALLING"
    var isCompleted: Boolean = false,
    var finalFeedbackMessage: String = ""
)
