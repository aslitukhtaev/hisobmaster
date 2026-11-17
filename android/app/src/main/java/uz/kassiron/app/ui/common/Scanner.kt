package uz.kassiron.app.ui.common

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import uz.kassiron.app.R
import uz.kassiron.app.ui.kit.KButton
import uz.kassiron.app.ui.kit.KIconButton
import uz.kassiron.app.ui.theme.K
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * PUBLIC CONTRACT (owned by the till work): a full-screen camera barcode
 * scanner. Calls [onResult] with the code (EAN-13, EAN-8, UPC, Code128, QR...)
 * and closes; [onDismiss] when cancelled. Handles the camera permission
 * itself. [continuous] = keep scanning (the till adds item after item, with
 * a beep), calling onResult for each new code.
 *
 * Single mode calls onResult once and then onDismiss, so the dialog closes
 * whichever of the two the caller hides it in. Continuous mode ignores the
 * same code for 1.5 s (a can held under the camera is one scan).
 */
@Composable
fun BarcodeScannerDialog(onResult: (String) -> Unit, onDismiss: () -> Unit, continuous: Boolean = false) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(CameraDark)) {
            ScannerContent(onResult, onDismiss, continuous)
        }
    }
}

@Composable
private fun ScannerContent(onResult: (String) -> Unit, onDismiss: () -> Unit, continuous: Boolean) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        asked = true
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    if (granted) {
        CameraScanner(onResult, onDismiss, continuous)
    } else {
        ScannerPermission(asked, onAllow = { launcher.launch(Manifest.permission.CAMERA) }, onClose = onDismiss)
    }
}

/** No camera permission yet: a short explanation and the "Ruxsat berish" button. */
@Composable
internal fun ScannerPermission(asked: Boolean, onAllow: () -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(CameraDark)) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.NoPhotography, null, tint = HintWhite, modifier = Modifier.size(56.dp))
            Text(
                stringResource(R.string.scan_permission), color = HintWhite, textAlign = TextAlign.Center,
                style = K.t.body.copy(fontSize = 15.sp),
            )
            if (asked) KButton(stringResource(R.string.scan_allow), onAllow, height = 48.dp, fontSize = 15.sp)
        }
        Row(Modifier.fillMaxWidth().safeDrawingPadding().padding(start = 14.dp, end = 14.dp, top = 12.dp)) {
            KIconButton(Icons.Outlined.Close, onClose, contentDescription = stringResource(R.string.scan_close))
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraScanner(onResult: (String) -> Unit, onDismiss: () -> Unit, continuous: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    var lastCode by remember { mutableStateOf<String?>(null) }

    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val executor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val scanner: BarcodeScanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(
                Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39, Barcode.FORMAT_CODE_93, Barcode.FORMAT_CODABAR,
                Barcode.FORMAT_ITF, Barcode.FORMAT_QR_CODE, Barcode.FORMAT_DATA_MATRIX,
            ).build()
        )
    }
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90) }.getOrNull() }
    val main = remember { Handler(Looper.getMainLooper()) }
    val done = remember { AtomicBoolean(false) }
    // Last accepted code and when (continuous mode debounce).
    val recent = remember { arrayOf<String?>(null); }
    val recentAt = remember { longArrayOf(0L) }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        providerFuture.addListener({
            val p = runCatching { providerFuture.get() }.getOrNull() ?: return@addListener
            provider = p
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { proxy: ImageProxy ->
                val media = proxy.image
                if (media == null || done.get()) {
                    proxy.close(); return@setAnalyzer
                }
                val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                scanner.process(image)
                    .addOnSuccessListener { codes ->
                        val code = codes.firstNotNullOfOrNull { it.rawValue?.trim()?.takeIf(String::isNotEmpty) }
                            ?: return@addOnSuccessListener
                        val now = System.currentTimeMillis()
                        if (continuous && code == recent[0] && now - recentAt[0] < 1500) {
                            recentAt[0] = now // still held under the camera
                            return@addOnSuccessListener
                        }
                        if (!continuous && !done.compareAndSet(false, true)) return@addOnSuccessListener
                        recent[0] = code
                        recentAt[0] = now
                        main.post {
                            runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120) }
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            lastCode = code
                            currentOnResult(code)
                            if (!continuous) currentOnDismiss()
                        }
                    }
                    .addOnCompleteListener { proxy.close() }
            }
            try {
                p.unbindAll()
                camera = p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (_: Exception) {
                camera = null
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { provider?.unbindAll() }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            done.set(true)
            runCatching { scanner.close() }
            executor.shutdown()
            runCatching { tone?.release() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        ScannerOverlay(
            hint = lastCode?.let { stringResource(R.string.scan_last, it) } ?: stringResource(R.string.scan_hint),
            torch = torch,
            hasTorch = camera?.cameraInfo?.hasFlashUnit() == true,
            onClose = onDismiss,
            onTorch = {
                torch = !torch
                camera?.cameraControl?.enableTorch(torch)
            },
        )
    }
}

/** The camera is dark in both themes (mockup 31): fixed colours on purpose. */
private val CameraDark = Color(0xFF0B0D09)
private val Dim = Color(0x8C000000)
private val HintWhite = Color(0xFFFFFFFF)

/**
 * Everything drawn over the camera picture (mockup 31): the dimmed area around a
 * lime frame with a moving scan line, close and torch buttons at the top, the
 * hint at the bottom.
 */
@Composable
internal fun ScannerOverlay(hint: String, torch: Boolean, hasTorch: Boolean, onClose: () -> Unit, onTorch: () -> Unit) {
    val lime = K.c.primary
    val glow = K.c.glow
    val phase by rememberInfiniteTransition(label = "scan").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "line",
    )
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = minOf(size.width * 0.72f, 300.dp.toPx())
            val h = w * 0.65f
            val topLeft = Offset((size.width - w) / 2, (size.height - h) / 2)
            val r = CornerRadius(18.dp.toPx())
            // Dim everything but the frame (an even-odd path: the screen minus the frame).
            drawPath(
                Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(Offset.Zero, size))
                    addRoundRect(RoundRect(Rect(topLeft, Size(w, h)), r))
                },
                Dim,
            )
            // Soft glow around the frame, then the frame itself.
            for (i in 1..3) {
                val g = (i * 4).dp.toPx()
                drawRoundRect(
                    glow.copy(alpha = glow.alpha * (0.5f / i)), Offset(topLeft.x - g / 2, topLeft.y - g / 2), Size(w + g, h + g),
                    CornerRadius(r.x + g / 2), style = Stroke(width = g),
                )
            }
            drawRoundRect(lime, topLeft, Size(w, h), r, style = Stroke(width = 2.dp.toPx()))
            // The scan line: starts in the middle, sweeps up and down.
            val y = topLeft.y + h / 2 + (h / 2 - 12.dp.toPx()) * kotlin.math.sin(phase * 2 * Math.PI).toFloat()
            val inset = 10.dp.toPx()
            drawLine(glow, Offset(topLeft.x + inset, y), Offset(topLeft.x + w - inset, y), strokeWidth = 8.dp.toPx())
            drawLine(lime, Offset(topLeft.x + inset, y), Offset(topLeft.x + w - inset, y), strokeWidth = 2.dp.toPx())
        }
        Row(
            Modifier.fillMaxWidth().safeDrawingPadding().padding(start = 14.dp, end = 14.dp, top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            KIconButton(Icons.Outlined.Close, onClose, contentDescription = stringResource(R.string.scan_close))
            if (hasTorch) {
                KIconButton(
                    if (torch) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff, onTorch, primary = torch,
                    contentDescription = stringResource(R.string.scan_torch),
                )
            }
        }
        Text(
            hint, color = HintWhite, textAlign = TextAlign.Center, style = K.t.label.copy(fontSize = 14.sp),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 30.dp),
        )
    }
}
