package com.airgest.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.Preview
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.PreviewView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { AirgestApp() } }
    }
}

@Composable
private fun AirgestApp() {
    val context = LocalContext.current
    var message by remember { mutableStateOf("Ready. Enable Accessibility to control the tablet.") }
    var cameraEnabled by remember { mutableStateOf(false) }
    var gestureEnabled by remember { mutableStateOf(false) }
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        if (!granted) message = "Camera permission is needed for face gestures. Voice control still works."
    }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (spoken == null) {
            message = "No speech was recognized. Try again."
        } else {
            message = "Heard: “$spoken” — ${runCommand(spoken)}"
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Airgest", style = MaterialTheme.typography.headlineLarge)
            Text("Hands-free controls for your Android tablet", style = MaterialTheme.typography.titleMedium)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Status", style = MaterialTheme.typography.titleMedium)
                    Text(message)
                    Text(if (AirgestAccessibilityService.instance != null) "Accessibility service: connected" else "Accessibility service: not enabled")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Say: tap, scroll down, go home, or go back")
                    }
                    try { speechLauncher.launch(intent) } catch (_: Exception) { message = "Speech recognition is unavailable on this device." }
                }) { Text("Speak a command") }
                Button(onClick = {
                    context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) { Text("Accessibility") }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Face gesture controls", style = MaterialTheme.typography.titleMedium)
                    Text("On-device face detection. Camera frames are processed locally and are not saved.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Enable gesture actions", modifier = Modifier.weight(1f))
                        Switch(checked = gestureEnabled, onCheckedChange = { enabled ->
                            gestureEnabled = enabled
                            if (enabled && !permissionGranted) cameraPermission.launch(Manifest.permission.CAMERA)
                            if (enabled && permissionGranted) cameraEnabled = true
                            if (!enabled) cameraEnabled = false
                        })
                    }
                    if (!permissionGranted) Button(onClick = { cameraPermission.launch(Manifest.permission.CAMERA) }) { Text("Grant camera permission") }
                    if (cameraEnabled && permissionGranted) {
                        FaceCamera(
                            enabled = gestureEnabled,
                            onStatus = { message = it },
                            modifier = Modifier.fillMaxWidth().height(240.dp)
                        )
                        Text("Smile = tap · turn head left/right = swipe. Keep gestures enabled only when you want them active.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Voice commands", style = MaterialTheme.typography.titleMedium)
                    Text("“tap” · “scroll up/down” · “swipe left/right” · “go home” · “go back”")
                    Text("Airgest uses Android speech recognition and a local command parser; no paid AI API is required.", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun runCommand(raw: String): String {
    val service = AirgestAccessibilityService.instance
        ?: return "Enable Airgest in Accessibility settings first."
    val text = raw.lowercase(Locale.ROOT)
    return when {
        "home" in text -> { service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME); "going home" }
        "back" in text -> { service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK); "going back" }
        "tap" in text || "click" in text -> if (service.tap()) "tapping screen center" else "tap could not be dispatched"
        "scroll up" in text -> if (service.swipe(AirgestAccessibilityService.Direction.UP)) "scrolling up" else "scroll could not be dispatched"
        "scroll down" in text -> if (service.swipe(AirgestAccessibilityService.Direction.DOWN)) "scrolling down" else "scroll could not be dispatched"
        "left" in text -> if (service.swipe(AirgestAccessibilityService.Direction.LEFT)) "swiping left" else "swipe could not be dispatched"
        "right" in text -> if (service.swipe(AirgestAccessibilityService.Direction.RIGHT)) "swiping right" else "swipe could not be dispatched"
        else -> "command not recognized; try tap, scroll up/down, swipe left/right, home, or back"
    }
}

@Composable
private fun FaceCamera(enabled: Boolean, onStatus: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cooldown = remember { AtomicLong(0L) }
    val detector = remember {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .enableTracking()
            .build()
        FaceDetection.getClient(options)
    }
    val executor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        },
        update = { previewView ->
            val providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener({
                try {
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(executor) { proxy ->
                        val mediaImage = proxy.image
                        if (mediaImage == null) {
                            proxy.close()
                        } else {
                            val input = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
                            detector.process(input)
                                .addOnSuccessListener { faces ->
                                    if (enabled) handleFaces(faces, onStatus, cooldown)
                                    else if (faces.isEmpty()) onStatus("Looking for a face…")
                                }
                                .addOnFailureListener { onStatus("Face detection failed. Check camera access.") }
                                .addOnCompleteListener { proxy.close() }
                        }
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
                } catch (_: Exception) {
                    onStatus("Could not start the front camera on this device.")
                }
            }, ContextCompat.getMainExecutor(context))
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            detector.close()
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }
}

private fun handleFaces(faces: List<Face>, onStatus: (String) -> Unit, cooldown: AtomicLong) {
    val now = System.currentTimeMillis()
    if (now - cooldown.get() < 1400L) return
    val face = faces.firstOrNull()
    if (face == null) {
        onStatus("Looking for a face…")
        return
    }
    val service = AirgestAccessibilityService.instance
    val smile = (face.smilingProbability ?: 0f) > .85f
    val yaw = face.headEulerAngleY
    when {
        smile && service != null -> {
            if (cooldown.compareAndSet(cooldown.get(), now)) {
                service.tap()
                onStatus("Smile detected — tapped the center of the screen")
            }
        }
        yaw < -25f && service != null -> {
            if (cooldown.compareAndSet(cooldown.get(), now)) {
                service.swipe(AirgestAccessibilityService.Direction.LEFT)
                onStatus("Head turned left — swiped left")
            }
        }
        yaw > 25f && service != null -> {
            if (cooldown.compareAndSet(cooldown.get(), now)) {
                service.swipe(AirgestAccessibilityService.Direction.RIGHT)
                onStatus("Head turned right — swiped right")
            }
        }
        service == null -> onStatus("Enable Airgest Accessibility to perform gestures")
        else -> onStatus("Face detected — smile to tap or turn your head to swipe")
    }
}
