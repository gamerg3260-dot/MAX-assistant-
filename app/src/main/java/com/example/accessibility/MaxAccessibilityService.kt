package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Bundle
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

data class AccessibilityVoiceResult(
    val isHandled: Boolean,
    val feedbackMessage: String,
    val actionTaken: String? = null
)

/**
 * Android Accessibility Service for MAX Assistant.
 * Provides hands-free screen auto-scrolling, swipe gestures, and automated
 * text typing in social media feeds and form input fields.
 */
class MaxAccessibilityService : AccessibilityService() {
    private val tag = "MaxAccessibility"
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var autoScrollJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInstance = this
        _isServiceConnected.value = true
        _lastActionStatus.value = "MAX Accessibility Service Connected & Active."
        Log.i(tag, "MaxAccessibilityService connected successfully.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Receives window and view events if needed
    }

    override fun onInterrupt() {
        Log.w(tag, "MaxAccessibilityService interrupted.")
        _lastActionStatus.value = "Accessibility Service Interrupted."
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoScroll()
        _isServiceConnected.value = false
        if (serviceInstance == this) {
            serviceInstance = null
        }
        Log.i(tag, "MaxAccessibilityService destroyed.")
    }

    /**
     * Executes a vertical swipe gesture or node scroll action to scroll down the screen feed.
     */
    fun performScrollDown(): Boolean {
        val displayMetrics = getDisplayMetrics()
        val width = displayMetrics.widthPixels.toFloat()
        val height = displayMetrics.heightPixels.toFloat()

        if (width <= 0 || height <= 0) {
            val fallbackSuccess = performScrollNodeFallback(forward = true)
            _lastActionStatus.value = if (fallbackSuccess) "Scrolled Down (Node)" else "Scroll Down Failed"
            return fallbackSuccess
        }

        val startX = width * 0.5f
        val startY = height * 0.75f
        val endX = width * 0.5f
        val endY = height * 0.25f

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 350))
            .build()

        var isDispatched = false
        try {
            isDispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    _lastActionStatus.value = "Scrolled Down successfully."
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    performScrollNodeFallback(forward = true)
                    _lastActionStatus.value = "Scroll gesture cancelled, used node scroll fallback."
                }
            }, null)
        } catch (e: Exception) {
            Log.e(tag, "Error dispatching scroll down gesture: ${e.message}")
            isDispatched = performScrollNodeFallback(forward = true)
        }

        return isDispatched
    }

    /**
     * Executes a vertical swipe gesture or node scroll action to scroll up the screen feed.
     */
    fun performScrollUp(): Boolean {
        val displayMetrics = getDisplayMetrics()
        val width = displayMetrics.widthPixels.toFloat()
        val height = displayMetrics.heightPixels.toFloat()

        if (width <= 0 || height <= 0) {
            val fallbackSuccess = performScrollNodeFallback(forward = false)
            _lastActionStatus.value = if (fallbackSuccess) "Scrolled Up (Node)" else "Scroll Up Failed"
            return fallbackSuccess
        }

        val startX = width * 0.5f
        val startY = height * 0.25f
        val endX = width * 0.5f
        val endY = height * 0.75f

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 350))
            .build()

        var isDispatched = false
        try {
            isDispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    _lastActionStatus.value = "Scrolled Up successfully."
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    performScrollNodeFallback(forward = false)
                    _lastActionStatus.value = "Scroll up gesture cancelled, used node scroll fallback."
                }
            }, null)
        } catch (e: Exception) {
            Log.e(tag, "Error dispatching scroll up gesture: ${e.message}")
            isDispatched = performScrollNodeFallback(forward = false)
        }

        return isDispatched
    }

    private fun performScrollNodeFallback(forward: Boolean): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val scrollableNode = findScrollableNode(rootNode) ?: return false
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        return scrollableNode.performAction(action)
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val scrollableChild = findScrollableNode(child)
            if (scrollableChild != null) return scrollableChild
        }
        return null
    }

    /**
     * Starts continuous auto-scrolling at the configured interval (e.g., 2000ms).
     */
    fun startAutoScroll(intervalMs: Long = _autoScrollSpeedMs.value) {
        stopAutoScroll()
        _autoScrollSpeedMs.value = intervalMs
        _isAutoScrolling.value = true
        _lastActionStatus.value = "Continuous Auto-Scroll Started (${intervalMs / 1000f}s interval)."

        autoScrollJob = serviceScope.launch {
            while (isActive && _isAutoScrolling.value) {
                performScrollDown()
                delay(_autoScrollSpeedMs.value)
            }
        }
    }

    /**
     * Stops active continuous auto-scrolling.
     */
    fun stopAutoScroll() {
        autoScrollJob?.cancel()
        autoScrollJob = null
        _isAutoScrolling.value = false
        _lastActionStatus.value = "Continuous Auto-Scroll Stopped."
    }

    /**
     * Finds the currently focused input node or editable text field on screen
     * and dispatches auto-type text injection.
     */
    fun autoTypeInFocusedField(textToType: String): Boolean {
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            _lastActionStatus.value = "Cannot auto-type: Active window content unavailable."
            return false
        }

        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: findFirstEditableNode(rootNode)

        if (focusedNode == null) {
            _lastActionStatus.value = "No focused text input or form field found on screen."
            return false
        }

        return try {
            focusedNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
            }
            val isSuccess = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            if (isSuccess) {
                _lastActionStatus.value = "Auto-typed successfully into active text field."
                Log.i(tag, "Auto-typed text: '$textToType'")
            } else {
                _lastActionStatus.value = "Failed to set text in target input node."
            }
            isSuccess
        } catch (e: Exception) {
            Log.e(tag, "Error auto-typing text: ${e.message}", e)
            _lastActionStatus.value = "Auto-type failed: ${e.localizedMessage}"
            false
        }
    }

    private fun findFirstEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable || node.className?.toString()?.contains("EditText", ignoreCase = true) == true) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val editableChild = findFirstEditableNode(child)
            if (editableChild != null) return editableChild
        }
        return null
    }

    private fun getDisplayMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        wm?.defaultDisplay?.getRealMetrics(metrics)
        return metrics
    }

    companion object {
        var serviceInstance: MaxAccessibilityService? = null
            private set

        val instance: MaxAccessibilityService? get() = serviceInstance

        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

        private val _isAutoScrolling = MutableStateFlow(false)
        val isAutoScrolling: StateFlow<Boolean> = _isAutoScrolling.asStateFlow()

        private val _autoScrollSpeedMs = MutableStateFlow(2000L)
        val autoScrollSpeedMs: StateFlow<Long> = _autoScrollSpeedMs.asStateFlow()

        private val _lastActionStatus = MutableStateFlow<String?>("Accessibility Service Ready.")
        val lastActionStatus: StateFlow<String?> = _lastActionStatus.asStateFlow()

        fun isAccessibilityEnabled(context: Context): Boolean {
            val expectedService = "${context.packageName}/${MaxAccessibilityService::class.java.canonicalName}"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            return enabledServices.contains(expectedService) || serviceInstance != null
        }

        /**
         * Parses voice triggers for Screen Auto Scroll and Auto-Type Dispatcher.
         */
        fun processVoiceAccessibilityCommand(query: String): AccessibilityVoiceResult {
            val q = query.lowercase(Locale.ROOT).trim()
            val service = instance

            if (service == null) {
                if (q.contains("scroll") || q.contains("type") || q.contains("स्क्रॉल") || q.contains("टाइप")) {
                    return AccessibilityVoiceResult(
                        isHandled = true,
                        feedbackMessage = "Accessibility service is not enabled. Please grant Accessibility access in settings.",
                        actionTaken = "ACCESSIBILITY_PERMISSION_NEEDED"
                    )
                }
                return AccessibilityVoiceResult(false, "Accessibility service disconnected.")
            }

            // 1. Auto-Type Dispatcher: "type [text]", "write [text]", "auto type [text]"
            if (q.startsWith("type ") || q.startsWith("write ") || q.startsWith("auto type ") ||
                q.startsWith("टाइप करो ") || q.startsWith("लिखो ")) {

                val textToType = when {
                    q.startsWith("auto type ") -> query.substring(10).trim()
                    q.startsWith("type ") -> query.substring(5).trim()
                    q.startsWith("write ") -> query.substring(6).trim()
                    q.startsWith("टाइप करो ") -> query.substring(10).trim()
                    q.startsWith("लिखो ") -> query.substring(5).trim()
                    else -> query.trim()
                }

                return if (textToType.isNotBlank()) {
                    val typed = service.autoTypeInFocusedField(textToType)
                    val feedback = if (typed)
                        "Auto-typed '$textToType' into active field."
                    else
                        "Could not find an active text field to type."
                    AccessibilityVoiceResult(true, feedback, "AUTO_TYPE")
                } else {
                    AccessibilityVoiceResult(true, "Please specify what text you would like me to type.", "AUTO_TYPE_EMPTY")
                }
            }

            // 2. Start Continuous Auto Scroll
            if (q.contains("auto scroll") || q.contains("start scrolling") || q.contains("keep scrolling") ||
                q.contains("continuous scroll") || q.contains("ऑटो स्क्रॉल") || q.contains("स्क्रॉल चालू")) {
                service.startAutoScroll()
                return AccessibilityVoiceResult(true, "Continuous auto-scroll started.", "AUTO_SCROLL_START")
            }

            // 3. Stop Continuous Auto Scroll
            if (q.contains("stop scroll") || q.contains("stop scrolling") || q.contains("pause scroll") ||
                q.contains("halt scroll") || q.contains("स्क्रॉल रोको") || q.contains("स्क्रॉल बंद")) {
                service.stopAutoScroll()
                return AccessibilityVoiceResult(true, "Auto-scroll stopped.", "AUTO_SCROLL_STOP")
            }

            // 4. Single Scroll Down
            if (q.contains("scroll down") || q.contains("page down") || q.contains("next page") ||
                q.contains("swipe down") || q.contains("स्क्रॉल डाउन") || q.contains("नीचे करो")) {
                service.performScrollDown()
                return AccessibilityVoiceResult(true, "Scrolled down.", "SCROLL_DOWN")
            }

            // 5. Single Scroll Up
            if (q.contains("scroll up") || q.contains("page up") || q.contains("previous page") ||
                q.contains("swipe up") || q.contains("स्क्रॉल अप") || q.contains("ऊपर करो")) {
                service.performScrollUp()
                return AccessibilityVoiceResult(true, "Scrolled up.", "SCROLL_UP")
            }

            return AccessibilityVoiceResult(false, "Command not recognized as accessibility gesture.")
        }
    }
}
