package com.example.mtgscanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
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
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var cardNameInput: EditText
    private lateinit var setCodeInput: EditText
    private lateinit var collectorInput: EditText
    private lateinit var languageSpinner: Spinner
    private lateinit var foilSwitch: Switch
    private lateinit var resultImage: ImageView
    private lateinit var resultSummary: TextView
    private lateinit var lookupButton: Button
    private lateinit var saveButton: Button

    private var imageCapture: ImageCapture? = null
    private var currentCard: CardRecord? = null
    private val scryfallClient = ScryfallClient()
    private val repository by lazy { CardRepository(this) }
    private val languages = listOf(
        LanguageOption("英語", "en"), LanguageOption("日本語", "ja"),
        LanguageOption("ドイツ語", "de"), LanguageOption("フランス語", "fr"),
        LanguageOption("イタリア語", "it"), LanguageOption("スペイン語", "es"),
        LanguageOption("ポルトガル語", "pt"), LanguageOption("韓国語", "ko"),
        LanguageOption("ロシア語", "ru"), LanguageOption("簡体字", "zhs"),
        LanguageOption("繁体字", "zht")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = COLOR_BACKGROUND
        setContentView(buildScreen())
        requestCameraOrStart()
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_BACKGROUND)
        }
        root.addView(buildToolbar())

        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        val cameraFrame = FrameLayout(this).apply {
            addView(previewView, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(View(this@MainActivity).apply {
                background = roundedBorder(Color.TRANSPARENT, COLOR_GOLD, 2f, 14f)
            }, FrameLayout.LayoutParams(dp(252), dp(352), Gravity.CENTER))
            addView(TextView(this@MainActivity).apply {
                text = "カード全体を枠に合わせてください"
                textSize = 13f
                setTextColor(Color.WHITE)
                setPadding(dp(12), dp(6), dp(12), dp(6))
                background = roundedBackground(0xB3000000.toInt(), 16f)
            }, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = dp(12)
            })
        }
        root.addView(cameraFrame, LinearLayout.LayoutParams(MATCH, 0, 0.44f))

        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(28))
        }
        val captureButton = primaryButton("カードを撮影して読み取る").apply {
            setOnClickListener { takePhoto() }
        }
        form.addView(captureButton, LinearLayout.LayoutParams(MATCH, dp(52)))
        form.addView(sectionTitle("認識結果を確認・修正"))

        cardNameInput = inputField("カード名（セット情報が読めない場合に使用）")
        form.addView(cardNameInput, LinearLayout.LayoutParams(MATCH, WRAP))

        val idRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        setCodeInput = inputField("セットコード").apply {
            filters = arrayOf(InputFilter.AllCaps(), InputFilter.LengthFilter(5))
        }
        collectorInput = inputField("コレクター番号")
        idRow.addView(setCodeInput, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(8) })
        idRow.addView(collectorInput, LinearLayout.LayoutParams(0, WRAP, 1.25f))
        form.addView(idRow)

        val optionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        languageSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                languages.map { it.label }
            )
            background = roundedBorder(COLOR_PANEL, COLOR_BORDER, 1f, 8f)
            setPadding(dp(10), 0, dp(10), 0)
        }
        foilSwitch = Switch(this).apply {
            text = "Foil"
            setTextColor(Color.WHITE)
        }
        optionRow.addView(languageSpinner, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginEnd = dp(12) })
        optionRow.addView(foilSwitch, LinearLayout.LayoutParams(WRAP, WRAP))
        form.addView(optionRow, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) })

        lookupButton = secondaryButton("Scryfallでカード情報を取得").apply {
            setOnClickListener { lookupCard() }
        }
        form.addView(lookupButton, LinearLayout.LayoutParams(MATCH, dp(50)).apply { topMargin = dp(12) })

        resultImage = ImageView(this).apply {
            visibility = View.GONE
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(COLOR_PANEL)
        }
        form.addView(resultImage, LinearLayout.LayoutParams(MATCH, dp(250)).apply { topMargin = dp(14) })

        resultSummary = TextView(this).apply {
            text = "撮影するか、情報を入力してScryfallで検索してください。"
            textSize = 14f
            setTextColor(COLOR_TEXT_MUTED)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedBackground(COLOR_PANEL, 10f)
        }
        form.addView(resultSummary, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(10) })

        saveButton = primaryButton("コレクションに追加").apply {
            isEnabled = false
            alpha = 0.45f
            setOnClickListener { saveCurrentCard() }
        }
        form.addView(saveButton, LinearLayout.LayoutParams(MATCH, dp(52)).apply { topMargin = dp(12) })

        val scrollView = ScrollView(this).apply { addView(form) }
        root.addView(scrollView, LinearLayout.LayoutParams(MATCH, 0, 0.56f))
        return root
    }

    private fun buildToolbar(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(18), dp(10), dp(10), dp(10))
        setBackgroundColor(COLOR_TOOLBAR)
        addView(TextView(this@MainActivity).apply {
            text = "MTG SCANNER"
            textSize = 20f
            setTextColor(COLOR_GOLD)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
        addView(secondaryButton("コレクション").apply {
            setOnClickListener { startActivity(Intent(this@MainActivity, CollectionActivity::class.java)) }
        }, LinearLayout.LayoutParams(WRAP, dp(42)))
    }

    private fun requestCameraOrStart() {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            Toast.makeText(this, "カード撮影にはカメラの使用許可が必要です", Toast.LENGTH_LONG).show()
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            }.onFailure {
                Toast.makeText(this, "カメラを起動できませんでした", Toast.LENGTH_LONG).show()
            }
        }, mainExecutor)
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        val photo = File(cacheDir, "mtg_card_${System.currentTimeMillis()}.jpg")
        resultSummary.text = "撮影中…"
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(photo).build(),
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) = runOcr(photo)
                override fun onError(exception: ImageCaptureException) {
                    resultSummary.text = "撮影に失敗しました: ${exception.message}"
                }
            }
        )
    }

    private fun runOcr(photo: File) {
        val image = runCatching { InputImage.fromFilePath(this, android.net.Uri.fromFile(photo)) }
            .getOrElse {
                resultSummary.text = "画像を読み込めませんでした: ${it.message}"
                return
            }
        val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
        resultSummary.text = "カードの文字を認識しています…"
        recognizer.process(image)
            .addOnSuccessListener { text ->
                if (text.text.isBlank()) {
                    resultSummary.text = "文字を認識できませんでした。明るい場所でカード全体を撮影してください。"
                    return@addOnSuccessListener
                }
                val candidate = OcrParser.parse(text, image.height)
                cardNameInput.setText(candidate.cardName)
                setCodeInput.setText(candidate.setCode.uppercase(Locale.US))
                collectorInput.setText(candidate.collectorNumber)
                languageSpinner.setSelection(languages.indexOfFirst { it.code == candidate.language }.coerceAtLeast(0))
                resultSummary.text = "OCRが完了しました。認識結果を確認して検索してください。"
                if ((candidate.setCode.isNotBlank() && candidate.collectorNumber.isNotBlank()) || candidate.cardName.isNotBlank()) {
                    lookupCard()
                }
            }
            .addOnFailureListener { resultSummary.text = "OCRエラー: ${it.message}" }
            .addOnCompleteListener {
                recognizer.close()
                photo.delete()
            }
    }

    private fun lookupCard() {
        val language = languages[languageSpinner.selectedItemPosition].code
        currentCard = null
        setLookupState(true)
        scryfallClient.lookup(
            setCodeInput.text.toString().trim(),
            collectorInput.text.toString().trim(),
            language,
            cardNameInput.text.toString().trim()
        ) { result ->
            runOnUiThread {
                setLookupState(false)
                result.onSuccess { showCard(it) }
                    .onFailure {
                        resultSummary.text = "カードを特定できませんでした。認識結果を修正して再検索してください。\n${it.message}"
                    }
            }
        }
    }

    private fun showCard(card: CardRecord) {
        currentCard = card
        cardNameInput.setText(card.displayName)
        setCodeInput.setText(card.setCode)
        collectorInput.setText(card.collectorNumber)
        languageSpinner.setSelection(languages.indexOfFirst { it.code == card.language }.coerceAtLeast(0))
        resultImage.visibility = View.VISIBLE
        ImageLoader.load(card.imageUrl, resultImage)
        resultSummary.text = buildString {
            append(card.displayName)
            append("\n${card.setName} (${card.setCode}) #${card.collectorNumber}")
            append("\n言語: ${languages.firstOrNull { it.code == card.language }?.label ?: card.language}")
            append("\n参考価格: ${if (foilSwitch.isChecked) card.usdFoil?.let { "$$it" } ?: "情報なし" else card.displayPrice}")
        }
        saveButton.isEnabled = true
        saveButton.alpha = 1f
    }

    private fun saveCurrentCard() {
        val card = currentCard ?: return
        repository.addOrIncrement(card.copy(foil = foilSwitch.isChecked))
        Toast.makeText(this, "${card.displayName}をコレクションに追加しました", Toast.LENGTH_SHORT).show()
    }

    private fun setLookupState(loading: Boolean) {
        lookupButton.isEnabled = !loading
        lookupButton.text = if (loading) "Scryfallで検索中…" else "Scryfallでカード情報を取得"
        if (loading) resultSummary.text = "Scryfallからカード情報と価格を取得しています…"
        saveButton.isEnabled = false
        saveButton.alpha = 0.45f
    }

    private fun inputField(hintText: String) = EditText(this).apply {
        hint = hintText
        textSize = 15f
        setTextColor(Color.WHITE)
        setHintTextColor(COLOR_TEXT_MUTED)
        setSingleLine(true)
        setPadding(dp(12), 0, dp(12), 0)
        background = roundedBorder(COLOR_PANEL, COLOR_BORDER, 1f, 8f)
    }

    private fun sectionTitle(value: String) = TextView(this).apply {
        text = value
        textSize = 16f
        setTextColor(Color.WHITE)
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(16), 0, dp(8))
    }

    private fun primaryButton(value: String) = Button(this).apply {
        text = value
        textSize = 15f
        setTextColor(COLOR_BACKGROUND)
        background = roundedBackground(COLOR_GOLD, 8f)
        isAllCaps = false
    }

    private fun secondaryButton(value: String) = Button(this).apply {
        text = value
        textSize = 14f
        setTextColor(COLOR_GOLD)
        background = roundedBorder(Color.TRANSPARENT, COLOR_GOLD, 1f, 8f)
        isAllCaps = false
    }

    private fun roundedBackground(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius.toInt()).toFloat()
    }

    private fun roundedBorder(fill: Int, stroke: Int, width: Float, radius: Float) = GradientDrawable().apply {
        setColor(fill)
        setStroke(dp(width.toInt().coerceAtLeast(1)), stroke)
        cornerRadius = dp(radius.toInt()).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class LanguageOption(val label: String, val code: String)

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
        private const val MATCH = android.view.ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        private const val COLOR_BACKGROUND = 0xFF11151B.toInt()
        private const val COLOR_TOOLBAR = 0xFF090C10.toInt()
        private const val COLOR_PANEL = 0xFF1B222C.toInt()
        private const val COLOR_BORDER = 0xFF3A4656.toInt()
        private const val COLOR_GOLD = 0xFFD6A84B.toInt()
        private const val COLOR_TEXT_MUTED = 0xFFAAB2BE.toInt()
    }
}
