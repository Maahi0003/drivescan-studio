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

/**
 * Two palettes, one per appearance mode (v1.2.0 reskin):
 *  - DARK  = "Midnight Console": charcoal surfaces, one signal-lime accent,
 *    hairline borders, sharp 9dp corners, monospace meta text.
 *  - LIGHT = "Gulmohar": warm off-white, coral primary with teal/marigold
 *    reserved for folder tints, big 20dp corners.
 * Hex values are taken exactly from the approved design samples.
 */
class Palette(val dark: Boolean) {
    val bg: Int = if (dark) Color.rgb(12, 15, 20) else Color.rgb(255, 247, 236) // #0C0F14 / #FFF7EC
    val card: Int = if (dark) Color.rgb(21, 26, 33) else Color.WHITE // #151A21 / #FFFFFF
    val cardBorder: Int = if (dark) Color.rgb(36, 44, 56) else Color.rgb(241, 226, 206) // #242C38 / #F1E2CE
    val text: Int = if (dark) Color.rgb(233, 237, 243) else Color.rgb(34, 48, 60) // #E9EDF3 / #22303C
    val textSecondary: Int = if (dark) Color.rgb(139, 150, 165) else Color.rgb(124, 136, 148) // #8B96A5 / #7C8894
    val primary: Int = if (dark) Color.rgb(163, 230, 53) else Color.rgb(255, 107, 74) // #A3E635 lime / #FF6B4A coral
    val primaryDark: Int = if (dark) Color.rgb(132, 204, 22) else Color.rgb(224, 82, 46) // pressed shade (derived)
    val onPrimary: Int = if (dark) Color.rgb(19, 24, 3) else Color.WHITE // #131803 / #FFFFFF
    val chipBg: Int = if (dark) Color.rgb(27, 34, 44) else Color.rgb(255, 233, 219) // #1B222C / #FFE9DB
    val thumbBg: Int = if (dark) Color.rgb(30, 38, 49) else Color.rgb(255, 227, 210) // #1E2631 / #FFE3D2
    /** "Go / connected" signal: lime in Midnight, teal in Gulmohar. */
    val success: Int = if (dark) Color.rgb(163, 230, 53) else Color.rgb(14, 149, 148) // #A3E635 / #0E9594
    val onSuccess: Int = if (dark) Color.rgb(19, 24, 3) else Color.WHITE
    val danger: Int = Color.rgb(198, 40, 40)
    val scannerBg: Int = if (dark) Color.rgb(12, 15, 20) else Color.rgb(10, 10, 14)

    /** Corner radius (dp) for cards, buttons and rows: sharp 9 in Midnight, round 20 in Gulmohar. */
    val radius: Int = if (dark) 9 else 20

    /** Corner radius (dp) for small inner elements (thumbnails, selected rows). */
    val radiusSmall: Int = if (dark) 7 else 14

    /**
     * Pastel fills cycled across dashboard folder chips (Gulmohar: coral /
     * teal / marigold tints). Midnight folders stay on the plain chip tone.
     */
    val folderTints: List<Int> = if (dark) listOf(chipBg, chipBg, chipBg) else listOf(
        Color.rgb(255, 225, 214), // #FFE1D6 coral tint
        Color.rgb(216, 239, 234), // #D8EFEA teal tint
        Color.rgb(252, 235, 200) // #FCEBC8 marigold tint
    )
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
        // Midnight Console sets meta text (counts, dates, sizes) in mono —
        // approximated with the system monospace face (IBM Plex Mono stand-in).
        if (palette.dark) typeface = Typeface.MONOSPACE
    }

fun Context.primaryButton(text: String): Button {
    val palette = palette()
    return Button(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(palette.onPrimary)
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        isAllCaps = false
        background = rounded(palette.primary, palette.radius)
        setPadding(28, 14, 28, 14)
    }
}

fun Context.ghostButton(text: String, palette: Palette): Button =
    Button(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(palette.primary)
        isAllCaps = false
        background = rounded(palette.chipBg, palette.radius)
        setPadding(22, 12, 22, 12)
    }

fun Context.inputField(hint: String, palette: Palette): EditText =
    EditText(this).apply {
        this.hint = hint
        textSize = 15f
        setTextColor(palette.text)
        setHintTextColor(palette.textSecondary)
        background = rounded(palette.card, palette.radius, palette.cardBorder)
        setPadding(24, 16, 24, 16)
    }

/** A card container with the app's standard padding and border. */
fun Context.card(palette: Palette): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        // Gulmohar cards are borderless with a soft warm shadow (approximated
        // with elevation); Midnight cards keep the hairline border, no shadow.
        background = rounded(palette.card, palette.radius, if (palette.dark) palette.cardBorder else null)
        if (!palette.dark) elevation = 2.5f * resources.displayMetrics.density
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
