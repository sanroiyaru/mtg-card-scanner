package com.example.mtgscanner

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import java.util.Locale

class CollectionActivity : ComponentActivity() {
    private val repository by lazy { CardRepository(this) }
    private lateinit var summaryText: TextView
    private lateinit var emptyText: TextView
    private lateinit var grid: GridLayout
    private lateinit var searchInput: EditText
    private var allCards: List<CardRecord> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = COLOR_BACKGROUND
        setContentView(buildScreen())
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_BACKGROUND)
        }
        root.addView(buildToolbar())

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(12))
        }
        summaryText = TextView(this).apply {
            textSize = 14f
            setTextColor(COLOR_TEXT_MUTED)
        }
        header.addView(summaryText)
        searchInput = EditText(this).apply {
            hint = "カード名・セット名・セットコードで検索"
            textSize = 15f
            setTextColor(Color.WHITE)
            setHintTextColor(COLOR_TEXT_MUTED)
            setSingleLine(true)
            setPadding(dp(14), 0, dp(14), 0)
            background = roundedBorder(COLOR_PANEL, COLOR_BORDER, 1, 9)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = renderCards()
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        header.addView(searchInput, LinearLayout.LayoutParams(MATCH, dp(50)).apply { topMargin = dp(10) })
        root.addView(header)

        grid = GridLayout(this).apply {
            columnCount = 2
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
            setPadding(dp(6), dp(4), dp(6), dp(24))
        }
        emptyText = TextView(this).apply {
            text = "まだカードがありません。\nスキャン画面からカードを追加してください。"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(COLOR_TEXT_MUTED)
            setPadding(dp(24), dp(80), dp(24), dp(24))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(emptyText, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(grid, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(MATCH, 0, 1f))
        return root
    }

    private fun buildToolbar(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(10), dp(16), dp(10))
        setBackgroundColor(COLOR_TOOLBAR)
        addView(Button(this@CollectionActivity).apply {
            text = "‹ スキャン"
            textSize = 14f
            setTextColor(COLOR_GOLD)
            background = roundedBorder(Color.TRANSPARENT, COLOR_GOLD, 1, 8)
            isAllCaps = false
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(WRAP, dp(42)))
        addView(TextView(this@CollectionActivity).apply {
            text = "MY COLLECTION"
            textSize = 20f
            gravity = Gravity.END
            setTextColor(COLOR_GOLD)
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
    }

    private fun reload() {
        allCards = repository.getAll().sortedByDescending { it.addedAt }
        val totalQuantity = allCards.sumOf { it.quantity }
        val totalUsd = allCards.sumOf { card ->
            val price = if (card.foil) card.usdFoil ?: card.usd else card.usd
            (price?.toDoubleOrNull() ?: 0.0) * card.quantity
        }
        summaryText.text = "${allCards.size}種類  •  $totalQuantity枚  •  参考総額 $${String.format(Locale.US, "%.2f", totalUsd)} USD"
        renderCards()
    }

    private fun renderCards() {
        if (!::grid.isInitialized) return
        val query = searchInput.text.toString().trim().lowercase()
        val cards = allCards.filter { card ->
            query.isBlank() || listOf(card.displayName, card.name, card.setName, card.setCode)
                .any { it.lowercase().contains(query) }
        }
        emptyText.visibility = if (cards.isEmpty()) View.VISIBLE else View.GONE
        grid.removeAllViews()
        cards.forEachIndexed { index, card ->
            val item = buildCardItem(card)
            val params = GridLayout.LayoutParams(
                GridLayout.spec(index / 2),
                GridLayout.spec(index % 2, 1f)
            ).apply {
                width = 0
                height = WRAP
                setMargins(dp(5), dp(5), dp(5), dp(8))
            }
            grid.addView(item, params)
        }
    }

    private fun buildCardItem(card: CardRecord): View {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(10))
            background = roundedBorder(COLOR_PANEL, COLOR_BORDER, 1, 10)
        }
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0xFF090C10.toInt())
        }
        item.addView(image, LinearLayout.LayoutParams(MATCH, dp(232)))
        ImageLoader.load(card.imageUrl, image)

        item.addView(TextView(this).apply {
            text = card.displayName
            textSize = 14f
            maxLines = 2
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(8), 0, 0)
        })
        item.addView(TextView(this).apply {
            text = "${card.setCode} #${card.collectorNumber}  •  ${card.language.uppercase()}${if (card.foil) "  •  FOIL" else ""}"
            textSize = 11f
            maxLines = 1
            setTextColor(COLOR_TEXT_MUTED)
        })
        item.addView(TextView(this).apply {
            text = card.displayPrice
            textSize = 15f
            setTextColor(COLOR_GOLD)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(5), 0, dp(4))
        })

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        controls.addView(smallButton("−") {
            repository.updateQuantity(card, card.quantity - 1)
            reload()
        })
        controls.addView(TextView(this).apply {
            text = card.quantity.toString()
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, dp(38), 1f))
        controls.addView(smallButton("＋") {
            repository.updateQuantity(card, card.quantity + 1)
            reload()
        })
        controls.addView(ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_delete)
            setColorFilter(COLOR_TEXT_MUTED)
            contentDescription = "削除"
            background = ColorDrawableCompat.transparent
            setOnClickListener {
                repository.delete(card)
                reload()
            }
        }, LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginStart = dp(4) })
        item.addView(controls, LinearLayout.LayoutParams(MATCH, WRAP))
        return item
    }

    private fun smallButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 17f
        setTextColor(COLOR_GOLD)
        background = roundedBorder(Color.TRANSPARENT, COLOR_GOLD, 1, 7)
        minWidth = 0
        minimumWidth = 0
        setPadding(0, 0, 0, 0)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
    }

    private fun roundedBorder(fill: Int, stroke: Int, width: Int, radius: Int) = GradientDrawable().apply {
        setColor(fill)
        setStroke(dp(width), stroke)
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private object ColorDrawableCompat {
        val transparent = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
    }

    companion object {
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
