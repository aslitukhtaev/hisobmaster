package uz.kassiron.app.ui.common

import androidx.compose.runtime.Composable

/**
 * PUBLIC CONTRACT (owned by the till work): a full-screen camera barcode
 * scanner. Calls [onResult] with the code (EAN-13, EAN-8, UPC, Code128, QR...)
 * and closes; [onDismiss] when cancelled. Handles the camera permission
 * itself. [continuous] = keep scanning (the till adds item after item, with
 * a beep), calling onResult for each new code.
 */
@Composable
fun BarcodeScannerDialog(onResult: (String) -> Unit, onDismiss: () -> Unit, continuous: Boolean = false) {
}
