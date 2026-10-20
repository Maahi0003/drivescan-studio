package com.drivescan.kmp.ui

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Two palettes — light, and a dark palette applied when the user enables dark mode. */
class Palette(val dark: Boolean) {
    val bg: Int = if (dark) Color.rgb(16, 17, 23) else Color.rgb(247, 248, 250)
    val card: Int = if (dark) Color.rgb(30, 31, 40) else Color.WHITE
    val cardBorder: Int = if (dark) Color.rgb(52, 54, 68) else Color.rgb(226, 229, 235)
    val text: Int = if (dark) Color.rgb(238, 239, 244) else Color.rgb(24, 26, 34)
    val textSecondary: Int = if (dark) Color.rgb(164, 167, 182) else Color.rgb(104, 110, 124)
    val primary: Int = Color.rgb(21, 101, 192)
    val primaryDark: Int = Color.rgb(13, 66, 130)
    val onPrimary: Int = Color.WHITE
    val chipBg: Int = if (dark) Color.rgb(38, 40, 52) else Color.rgb(232, 238, 247)
    val danger: Int = Color.rgb(198, 40, 40)
    val scannerBg: Int = Color.rgb(10, 10, 14)
}

fun Context.palette(): Palette {
    val dark = getSharedPreferences("drivescan_prefs", Context.MODE_PRIVATE)
        .getBoolean("dark_mode", false)
    return Palette(dark)
}

fun Activity.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
fun android.view.View.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.rounded(color: Int, radiusDp: Int, borderColor: Int? = null): GradientDrawable {
    val d = GradientDrawable()
    d.shape = GradientDrawable.RECTANGLE
    d.cornerRadius = radiusDp * resources.displayMetrics.density
    d.setColor(color)
    if (borderColor != null) d.setStroke((1 * resources.displayMetrics.density).toInt(), borderColor)
    return d
}

fun Context.titleText(text: String, palette: Palette, size: Float = 22f): TextView =
    TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(palette.text)
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

fun Context.bodyText(text: String, palette: Palette, size: Float = 14f): TextView =
    TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(palette.text)
    }

fun Context.smallText(text: String, palette: Palette, size: Float = 12.5f): TextView =
    TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(palette.textSecondary)
    }

fun Context.primaryButton(text: String): Button =
    Button(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(Color.WHITE)
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        isAllCaps = false
        background = rounded(Color.rgb(21, 101, 192), 12)
        setPadding(28, 14, 28, 14)
    }

fun Context.ghostButton(text: String, palette: Palette): Button =
    Button(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(palette.primary)
        isAllCaps = false
        background = rounded(palette.chipBg, 12)
        setPadding(22, 12, 22, 12)
    }

fun Context.inputField(hint: String, palette: Palette): EditText =
    EditText(this).apply {
        this.hint = hint
        textSize = 15f
        setTextColor(palette.text)
        setHintTextColor(palette.textSecondary)
        background = rounded(palette.card, 10, palette.cardBorder)
        setPadding(24, 16, 24, 16)
    }

/** A card container with the app's standard padding and border. */
fun Context.card(palette: Palette): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(palette.card, 14, palette.cardBorder)
        setPadding(28, 24, 28, 24)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 22 }
    }

/** Section heading used above lists and cards. */
fun Context.sectionTitle(text: String, palette: Palette): TextView =
    TextView(this).apply {
        this.text = text
        textSize = 16.5f
        setTextColor(palette.text)
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        setPadding(0, 26, 0, 12)
    }

/** Bottom navigation row shared by the top-level screens. */
fun Activity.bottomNav(palette: Palette, active: String): LinearLayout {
    val bar = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(8, 10, 8, 10)
        setBackgroundColor(palette.card)
    }
    val items = listOf(
        "home" to tr(Str.NAV_HOME),
        "vault" to tr(Str.NAV_VAULT),
        "bin" to tr(Str.NAV_BIN),
        "settings" to tr(Str.NAV_SETTINGS)
    )
    for ((key, label) in items) {
        val b = Button(this).apply {
            text = label
            textSize = 13f
            isAllCaps = false
            setTextColor(if (key == active) palette.primary else palette.textSecondary)
            typeface = Typeface.create(
                Typeface.SANS_SERIF,
                if (key == active) Typeface.BOLD else Typeface.NORMAL
            )
            background = null
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        b.setOnClickListener {
            if (key == active) return@setOnClickListener
            val target = when (key) {
                "home" -> com.drivescan.kmp.MainActivity::class.java
                "vault" -> com.drivescan.kmp.VaultActivity::class.java
                "bin" -> com.drivescan.kmp.BinActivity::class.java
                else -> com.drivescan.kmp.SettingsActivity::class.java
            }
            startActivity(android.content.Intent(this, target))
            if (key != "home") finish()
        }
        bar.addView(b)
    }
    return bar
}
