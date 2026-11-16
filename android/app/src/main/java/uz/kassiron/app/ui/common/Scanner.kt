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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import uz.kassiron.app.ui.theme.Brand
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
        Box(Modifier.fillMaxSize().background(Color.Black)) {
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
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.NoPhotography, null, tint = Color.White, modifier = Modifier.size(56.dp))
            Text(stringResource(R.string.scan_permission), color = Color.White, textAlign = TextAlign.Center)
            if (asked) {
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.scan_allow)) }
            }
        }
        CloseButton(onDismiss)
    }
}

@Composable
private fun CloseButton(onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
        FilledTonalIconButton(
            onClick = onDismiss,
            modifier = Modifier.size(52.dp).align(Alignment.TopStart),
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color(0x66000000), contentColor = Color.White),
        ) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.scan_close)) }
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
        AimOverlay()
        CloseButton(onDismiss)
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                lastCode?.let { stringResource(R.string.scan_last, it) } ?: stringResource(R.string.scan_hint),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
                    .background(Color(0x88000000), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (camera?.cameraInfo?.hasFlashUnit() == true) {
                    FilledTonalIconButton(
                        onClick = {
                            torch = !torch
                            camera?.cameraControl?.enableTorch(torch)
                        },
                        modifier = Modifier.size(64.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (torch) Brand.Lime else Color(0x66000000),
                            contentColor = if (torch) Brand.OnLime else Color.White,
                        ),
                    ) {
                        Icon(
                            if (torch) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                            contentDescription = stringResource(R.string.scan_torch),
                        )
                    }
                }
            }
        }
    }
}

/** Dims everything but a centred aim box. */
@Composable
private fun AimOverlay() {
    Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        val w = size.width * 0.78f
        val h = minOf(size.height * 0.28f, w * 0.62f)
        val topLeft = Offset((size.width - w) / 2, (size.height - h) / 2.4f)
        drawRect(Color(0x99000000))
        drawRoundRect(Color.Transparent, topLeft, Size(w, h), CornerRadius(24f, 24f), blendMode = BlendMode.Clear)
        drawRoundRect(Brand.Lime, topLeft, Size(w, h), CornerRadius(24f, 24f), style = Stroke(width = 6f))
    }
}
