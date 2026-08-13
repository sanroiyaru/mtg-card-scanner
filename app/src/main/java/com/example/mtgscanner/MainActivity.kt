package com.example.mtgscanner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import java.io.File
import android.graphics.BitmapFactory

class MainActivity : ComponentActivity() {

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
    }

    private lateinit var previewView: PreviewView
    private lateinit var resultTextView: TextView
    private var imageCapture: ImageCapture? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        previewView = PreviewView(this)

        resultTextView = TextView(this).apply {
            text = "OCR結果がここに表示されます"
            textSize = 16f
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xCCFFFFFF.toInt())
        }

        val captureButton = Button(this).apply {
            text = "撮影してOCR"
            setOnClickListener {
                takePhoto()
            }
        }

        val container = FrameLayout(this)

        container.addView(
            previewView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val resultParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            400
        ).apply {
            gravity = Gravity.TOP
        }

        container.addView(resultTextView, resultParams)

        val buttonParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = 80
        }

        container.addView(captureButton, buttonParams)

        setContentView(container)

        if (
            checkSelfPermission(Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (
            requestCode == CAMERA_PERMISSION_REQUEST_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            Toast.makeText(
                this,
                "カメラの使用を許可してください",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({

            val cameraProvider =
                cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(
                        previewView.surfaceProvider
                    )
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(
                    ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                )
                .build()

            val cameraSelector =
                CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
                )

            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "カメラを起動できませんでした",
                    Toast.LENGTH_LONG
                ).show()
            }

        }, mainExecutor)
    }

    private fun takePhoto() {
        val imageCapture =
            imageCapture ?: return

        val photoFile =
            File(filesDir, "mtg_card.jpg")

        val outputOptions =
            ImageCapture.OutputFileOptions
                .Builder(photoFile)
                .build()

        imageCapture.takePicture(
            outputOptions,
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(
                    outputFileResults:
                    ImageCapture.OutputFileResults
                ) {
                    runOcr(photoFile)
                }

                override fun onError(
                    exception: ImageCaptureException
                ) {
                    Toast.makeText(
                        this@MainActivity,
                        "撮影に失敗しました",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

private fun runOcr(photoFile: File) {
    try {
        val image =
            InputImage.fromFilePath(
                this,
                android.net.Uri.fromFile(photoFile)
            )

        val recognizer =
            TextRecognition.getClient(
                JapaneseTextRecognizerOptions.Builder()
                    .build()
            )

        resultTextView.text = "OCR処理中..."

        recognizer.process(image)
            .addOnSuccessListener { visionText ->

            val bitmapOptions =
    BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }

BitmapFactory.decodeFile(
    photoFile.absolutePath,
    bitmapOptions
)

val imageWidth = bitmapOptions.outWidth
val imageHeight = bitmapOptions.outHeight

                val fullText = visionText.text

                if (fullText.isBlank()) {
                    resultTextView.text =
                        "文字を認識できませんでした"
                    return@addOnSuccessListener
                }

                val lines = fullText
                    .lines()
                    .map { line -> line.trim() }
                    .filter { line -> line.isNotBlank() }

                    val titleLines =
    visionText.textBlocks
        .flatMap { block -> block.lines }
        .filter { line ->
            val box = line.boundingBox
            box != null &&
            box.top < imageHeight * 0.20
        }
        .sortedBy { line ->
            line.boundingBox?.top ?: Int.MAX_VALUE
        }

val cardNameCandidate =
    titleLines
        .joinToString(" ") { line -> line.text }
        .trim()
        .ifBlank { "不明" }

                val collectorRegex =
                    Regex("""\b(\d{1,4})\s*/\s*(\d{1,4})\b""")

                val collectorMatch =
                    collectorRegex.find(fullText)

                val collectorNumber =
                    collectorMatch?.groupValues?.get(1)
                        ?: "見つかりませんでした"

                resultTextView.text = """
                    カード名候補:
                    $cardNameCandidate

                    コレクター番号候補:
                    $collectorNumber

                    --- OCR全文 ---
                    $fullText
                """.trimIndent()
            }
            .addOnFailureListener { exception ->
                resultTextView.text =
                    "OCRエラー: ${exception.message}"
            }

    } catch (exception: Exception) {
        resultTextView.text =
            "画像読み込みエラー: ${exception.message}"
    }
}}
