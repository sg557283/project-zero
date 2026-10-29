package com.zero.assistant

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.zero.assistant.core.LocalNote
import com.zero.assistant.core.NoteRepository
import com.zero.assistant.core.ScannerText
import java.io.File

@Composable
internal fun ScannerScreen(
    modifier: Modifier,
    noteRepository: NoteRepository,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewVisible by remember { mutableStateOf(false) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var extractedText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Offline Latin-script OCR. Hindi requires a compatible Devanagari model.") }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        previewVisible = granted
        if (!granted) status = "Camera permission was denied. You can still select an image."
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { loadBitmap(context, it) }
                .onSuccess { loaded ->
                    bitmap = loaded
                    extractedText = ""
                    status = "Image loaded. Review, crop, rotate, then run OCR."
                }
                .onFailure { status = it.message ?: "The selected image could not be opened." }
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Knowledge Scanner", style = MaterialTheme.typography.titleLarge)
        Text("Capture or select an image and extract text locally with the bundled ML Kit Latin recognizer.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    previewVisible = true
                } else {
                    cameraPermission.launch(Manifest.permission.CAMERA)
                }
            }) { Text(if (previewVisible) "Camera ready" else "Open camera") }
            TextButton(onClick = {
                previewVisible = false
                imagePicker.launch(arrayOf("image/*"))
            }) { Text("Choose image") }
        }
        if (previewVisible) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
                factory = { viewContext ->
                    PreviewView(viewContext).also { previewView ->
                        val providerFuture = ProcessCameraProvider.getInstance(viewContext)
                        providerFuture.addListener({
                            val provider = providerFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val capture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .build()
                            imageCapture = capture
                            provider.unbindAll()
                            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                        }, ContextCompat.getMainExecutor(viewContext))
                    }
                },
            )
            Button(onClick = {
                val capture = imageCapture ?: return@Button
                val file = File(context.cacheDir, "zero_scan_${System.currentTimeMillis()}.jpg")
                capture.takePicture(
                    ImageCapture.OutputFileOptions.Builder(file).build(),
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            runCatching { loadBitmap(context, Uri.fromFile(file)) }
                                .onSuccess { loaded ->
                                    previewVisible = false
                                    bitmap = loaded
                                    status = "Photo captured. Review before OCR."
                                }
                                .onFailure { status = it.message ?: "Captured image could not be loaded." }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            status = "Capture failed: ${exception.message ?: "camera error"}"
                        }
                    },
                )
            }) { Text("Capture page") }
        }
        bitmap?.let { source ->
            Image(source.asImageBitmap(), contentDescription = "Scanned page preview", modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { bitmap = rotate(source, 90f) }) { Text("Rotate") }
                TextButton(onClick = { bitmap = cropCenter(source) }) { Text("Crop") }
                TextButton(onClick = { bitmap = null; extractedText = ""; previewVisible = false }) { Text("Retake") }
            }
            Button(onClick = {
                status = "Recognizing locally..."
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(InputImage.fromBitmap(source, 0))
                    .addOnSuccessListener { result ->
                        extractedText = ScannerText.normalize(result.text)
                        status = if (extractedText.isBlank()) {
                            "No text was found. Try another angle or a tighter crop."
                        } else "Offline OCR complete. Review and edit the result."
                        recognizer.close()
                    }
                    .addOnFailureListener { failure ->
                        status = "OCR failed: ${failure.message ?: "processing error"}"
                        recognizer.close()
                    }
            }) { Text("Run offline OCR") }
        }
        if (bitmap != null) {
            OutlinedTextField(
                value = extractedText,
                onValueChange = { extractedText = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 8,
                label = { Text("Extracted text") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("ZERO scan", extractedText))
                    status = "Copied to the clipboard."
                }, enabled = extractedText.isNotBlank()) { Text("Copy") }
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, extractedText)
                    })
                }, enabled = extractedText.isNotBlank()) { Text("Share") }
                Button(onClick = {
                    runCatching {
                        noteRepository.save(LocalNote(text = "[Scanned text]\n$extractedText"))
                        status = "Scan saved to local notes."
                    }.onFailure { status = it.message ?: "Scan could not be saved." }
                }, enabled = extractedText.isNotBlank()) { Text("Save to notes") }
            }
        }
    }
}

private fun loadBitmap(context: Context, uri: Uri): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: throw IllegalArgumentException("Image could not be decoded.")
    val rotation = context.contentResolver.openInputStream(uri)?.use { stream ->
        runCatching { ExifInterface(stream).rotationDegrees }.getOrDefault(0)
    } ?: 0
    return if (rotation == 0) bitmap else rotate(bitmap, rotation.toFloat())
}

private fun rotate(source: Bitmap, degrees: Float): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

private fun cropCenter(source: Bitmap): Bitmap {
    val left = (source.width * 0.08f).toInt()
    val top = (source.height * 0.08f).toInt()
    val width = source.width - left * 2
    val height = source.height - top * 2
    return Bitmap.createBitmap(source, left, top, width.coerceAtLeast(1), height.coerceAtLeast(1))
}
