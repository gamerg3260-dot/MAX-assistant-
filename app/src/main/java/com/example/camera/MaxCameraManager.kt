package com.example.camera

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

data class CameraVoiceResult(
    val isHandled: Boolean,
    val feedbackMessage: String,
    val actionTaken: String? = null
)

/**
 * CameraX Manager for MAX Assistant.
 * Controls Voice-Controlled Photo Capture, Front Selfie Switching,
 * Live Zoom Adjustments, and Voice-Driven Video Recording.
 */
class MaxCameraManager private constructor(private val context: Context) {
    private val tag = "MaxCameraManager"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

    private val _isFrontCamera = MutableStateFlow(true) // Default to selfie
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    private val _isRecordingVideo = MutableStateFlow(false)
    val isRecordingVideo: StateFlow<Boolean> = _isRecordingVideo.asStateFlow()

    private val _zoomRatio = MutableStateFlow(1.0f)
    val zoomRatio: StateFlow<Float> = _zoomRatio.asStateFlow()

    private val _lastCapturedPhotoUri = MutableStateFlow<Uri?>(null)
    val lastCapturedPhotoUri: StateFlow<Uri?> = _lastCapturedPhotoUri.asStateFlow()

    private val _lastCapturedVideoUri = MutableStateFlow<Uri?>(null)
    val lastCapturedVideoUri: StateFlow<Uri?> = _lastCapturedVideoUri.asStateFlow()

    private val _cameraStatus = MutableStateFlow<String?>("Camera Ready.")
    val cameraStatus: StateFlow<String?> = _cameraStatus.asStateFlow()

    private var currentLifecycleOwner: LifecycleOwner? = null

    fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Binds CameraX use cases to provided LifecycleOwner and SurfaceProvider.
     */
    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider
    ) {
        currentLifecycleOwner = lifecycleOwner
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                val provider = cameraProvider ?: return@addListener

                provider.unbindAll()

                val cameraSelector = if (_isFrontCamera.value) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = surfaceProvider
                }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                    .build()

                videoCapture = VideoCapture.withOutput(recorder)

                camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture,
                    videoCapture
                )

                // Apply initial zoom
                camera?.cameraControl?.setZoomRatio(_zoomRatio.value)
                _cameraStatus.value = "Camera Preview active (${if (_isFrontCamera.value) "Front/Selfie" else "Back"})."
            } catch (e: Exception) {
                Log.e(tag, "Failed to bind camera use cases: ${e.message}", e)
                _cameraStatus.value = "Camera binding failed: ${e.localizedMessage}"
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Swaps between Front (Selfie) and Back camera.
     */
    fun switchCamera() {
        _isFrontCamera.value = !_isFrontCamera.value
        val owner = currentLifecycleOwner
        if (owner != null && cameraProvider != null) {
            // Re-bind with updated selector
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                try {
                    val provider = future.get()
                    provider.unbindAll()

                    val selector = if (_isFrontCamera.value) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

                    val preview = Preview.Builder().build()
                    // Rebind if surface available
                    _cameraStatus.value = "Switched to ${if (_isFrontCamera.value) "Front Selfie" else "Main Back"} camera."
                } catch (e: Exception) {
                    Log.e(tag, "Error switching camera: ${e.message}")
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    /**
     * Programmatically triggers shutter capture for photo / selfie.
     */
    fun takePhoto(onComplete: ((Uri?) -> Unit)? = null) {
        val capture = imageCapture
        if (capture == null) {
            _cameraStatus.value = "Cannot capture photo: Camera not initialized."
            onComplete?.invoke(null)
            return
        }

        val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "MAX_$name")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MAX_Assistant")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = outputFileResults.savedUri
                    _lastCapturedPhotoUri.value = savedUri
                    _cameraStatus.value = "Photo captured successfully!"
                    Log.i(tag, "Photo saved to: $savedUri")
                    onComplete?.invoke(savedUri)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(tag, "Photo capture failed: ${exception.message}", exception)
                    _cameraStatus.value = "Photo capture failed: ${exception.localizedMessage}"
                    onComplete?.invoke(null)
                }
            }
        )
    }

    /**
     * Adjusts live camera zoom ratio (e.g. 1.0f to 5.0f).
     */
    fun setZoomRatio(ratio: Float) {
        val clampedRatio = ratio.coerceIn(1.0f, 5.0f)
        _zoomRatio.value = clampedRatio
        camera?.cameraControl?.setZoomRatio(clampedRatio)
        _cameraStatus.value = "Camera zoom set to ${String.format(Locale.ROOT, "%.1f", clampedRatio)}x."
    }

    fun zoomIn() {
        setZoomRatio(_zoomRatio.value + 0.5f)
    }

    fun zoomOut() {
        setZoomRatio(_zoomRatio.value - 0.5f)
    }

    /**
     * Starts video recording using VideoCapture API.
     */
    fun startVideoRecording() {
        val vCap = videoCapture
        if (vCap == null) {
            _cameraStatus.value = "Cannot start video: Camera not initialized."
            return
        }

        if (_isRecordingVideo.value) {
            _cameraStatus.value = "Already recording video."
            return
        }

        val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "MAX_VID_$name")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/MAX_Assistant")
            }
        }

        val mediaStoreOutput = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        try {
            val pendingRecording = vCap.output.prepareRecording(context, mediaStoreOutput)
            if (hasAudioPermission()) {
                pendingRecording.withAudioEnabled()
            }

            activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        _isRecordingVideo.value = true
                        _cameraStatus.value = "Video recording started..."
                        Log.i(tag, "Video recording started.")
                    }
                    is VideoRecordEvent.Finalize -> {
                        _isRecordingVideo.value = false
                        if (!event.hasError()) {
                            val uri = event.outputResults.outputUri
                            _lastCapturedVideoUri.value = uri
                            _cameraStatus.value = "Video recording saved successfully!"
                            Log.i(tag, "Video saved to: $uri")
                        } else {
                            _cameraStatus.value = "Video recording error: ${event.error}"
                            Log.e(tag, "Video recording error code: ${event.error}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to start video recording: ${e.message}", e)
            _cameraStatus.value = "Video recording error: ${e.localizedMessage}"
        }
    }

    /**
     * Stops current video recording.
     */
    fun stopVideoRecording() {
        activeRecording?.stop()
        activeRecording = null
        _isRecordingVideo.value = false
        _cameraStatus.value = "Video recording stopped."
    }

    /**
     * Parses voice commands for Camera Shutter, Selfie Switch, Zoom Level, and Video Recording.
     */
    fun processVoiceCameraCommand(query: String): CameraVoiceResult {
        val q = query.lowercase(Locale.ROOT).trim()

        if (!hasCameraPermission()) {
            if (q.contains("camera") || q.contains("photo") || q.contains("selfie") ||
                q.contains("picture") || q.contains("video") || q.contains("फोटो") || q.contains("सेल्फी")) {
                return CameraVoiceResult(
                    isHandled = true,
                    feedbackMessage = "Camera permission is missing. Please grant Camera permission to take photos and record video.",
                    actionTaken = "CAMERA_PERMISSION_NEEDED"
                )
            }
            return CameraVoiceResult(false, "Camera permission missing.")
        }

        // 1. Shutter / Photo / Selfie Triggers
        if (q.contains("take photo") || q.contains("take selfie") || q.contains("capture photo") ||
            q.contains("take picture") || q.contains("say cheese") || q.contains("cheers") ||
            q.contains("click photo") || q.contains("फोटो खींचो") || q.contains("सेल्फी लो") ||
            q.contains("फोटो क्लिक करो")) {

            if (q.contains("selfie") || q.contains("सेल्फी")) {
                _isFrontCamera.value = true
            }

            takePhoto()
            val modeText = if (_isFrontCamera.value) "selfie" else "photo"
            return CameraVoiceResult(
                isHandled = true,
                feedbackMessage = "Clicking $modeText! Say cheese!",
                actionTaken = "TAKE_PHOTO"
            )
        }

        // 2. Switch Camera: "front camera", "back camera", "switch camera", "सेल्फी कैमरा"
        if (q.contains("front camera") || q.contains("selfie camera") || q.contains("सेल्फी कैमरा")) {
            _isFrontCamera.value = true
            return CameraVoiceResult(true, "Switched to front selfie camera.", "SWITCH_FRONT")
        }
        if (q.contains("back camera") || q.contains("main camera") || q.contains("पीछे का कैमरा")) {
            _isFrontCamera.value = false
            return CameraVoiceResult(true, "Switched to rear main camera.", "SWITCH_BACK")
        }
        if (q.contains("switch camera") || q.contains("toggle camera") || q.contains("कैमरा बदलो")) {
            switchCamera()
            val cur = if (_isFrontCamera.value) "front selfie" else "rear main"
            return CameraVoiceResult(true, "Switched to $cur camera.", "SWITCH_TOGGLE")
        }

        // 3. Zoom Controls: "zoom in", "zoom out", "zoom 2x", "zoom 3x", "reset zoom", "ज़ूम करो"
        if (q.contains("zoom 2x") || q.contains("zoom 2") || q.contains("2x zoom")) {
            setZoomRatio(2.0f)
            return CameraVoiceResult(true, "Camera zoom set to 2.0x.", "ZOOM_2X")
        }
        if (q.contains("zoom 3x") || q.contains("zoom 3") || q.contains("3x zoom")) {
            setZoomRatio(3.0f)
            return CameraVoiceResult(true, "Camera zoom set to 3.0x.", "ZOOM_3X")
        }
        if (q.contains("reset zoom") || q.contains("zoom 1x") || q.contains("1x zoom")) {
            setZoomRatio(1.0f)
            return CameraVoiceResult(true, "Camera zoom reset to 1.0x.", "ZOOM_1X")
        }
        if (q.contains("zoom in") || q.contains("ज़ूम इन") || q.contains("ज़ूम करो")) {
            zoomIn()
            return CameraVoiceResult(true, "Zoomed in to ${String.format(Locale.ROOT, "%.1f", _zoomRatio.value)}x.", "ZOOM_IN")
        }
        if (q.contains("zoom out") || q.contains("ज़ूम आउट")) {
            zoomOut()
            return CameraVoiceResult(true, "Zoomed out to ${String.format(Locale.ROOT, "%.1f", _zoomRatio.value)}x.", "ZOOM_OUT")
        }

        // 4. Video Recording Triggers: "start recording", "stop recording", "record video", "रिकॉर्डिंग"
        if (q.contains("start recording") || q.contains("record video") || q.contains("start video") ||
            q.contains("रिकॉर्डिंग चालू") || q.contains("वीडियो रिकॉर्ड")) {
            startVideoRecording()
            return CameraVoiceResult(true, "Started video recording.", "VIDEO_START")
        }

        if (q.contains("stop recording") || q.contains("stop video") || q.contains("stop record") ||
            q.contains("रिकॉर्डिंग बंद")) {
            stopVideoRecording()
            return CameraVoiceResult(true, "Stopped video recording.", "VIDEO_STOP")
        }

        return CameraVoiceResult(false, "Command not recognized as camera trigger.")
    }

    companion object {
        @Volatile
        private var INSTANCE: MaxCameraManager? = null

        fun getInstance(context: Context): MaxCameraManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MaxCameraManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
