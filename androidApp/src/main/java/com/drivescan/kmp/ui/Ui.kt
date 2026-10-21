package com.drivescan.kmp.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/**
 * v1.3.0 design system — the user-supplied Google Stitch export, implemented
 * natively (supersedes the v1.2.0 Gulmohar/Midnight reskin):
 *  - LIGHT = "Frosted Teal Glass": warm alabaster canvas #FCF9F2 with soft
 *    mint/teal gradient washes; glass = white at 55–85% opacity, white 0.8
 *    borders, soft teal-tinted shadows; primary deep emerald teal
 *    #0C6251/#00483B; marigold + navy accents.
 *  - DARK  = "Obsidian Glass": canvas #0B0F12 with a faint top mint glow;
 *    glass = smoked rgba(18,24,28,0.65–0.85), white 0.08–0.12 borders and a
 *    1px top specular highlight; radiant mint #00E5A3 (bright #6EFFC3),
 *    seafoam #4DD0B8, amber #F59E0B; mono meta text.
 *
 * True backdrop blur is not available per-view on minSdk 26, so glass is
 * rendered the standard native way: translucent layered surfaces over a
 * themed gradient backdrop (see [BackdropDrawable]) + border + specular top
 * edge + soft elevation shadow. Dialog windows additionally get real
 * blur-behind on API 31+ (see [styleGlassDialog]).
 */
class Palette(val dark: Boolean) {
    /** Canvas base colour (mostly covered by the gradient backdrop). */
    val bg: Int = if (dark) Color.rgb(11, 15, 18) else Color.rgb(252, 249, 242) // #0B0F12 / #FCF9F2

    /** Glass card fill (Level 2): white .65 in light, smoked obsidian .65 in dark. */
    val glassCard: Int = if (dark) Color.argb(166, 18, 24, 28) else Color.argb(166, 255, 255, 255)

    /** Floating glass fill (Level 3: nav, dialogs, HUD bars): white .75 / obsidian .85. */
    val glassFloat: Int = if (dark) Color.argb(217, 18, 24, 28) else Color.argb(191, 255, 255, 255)

    /** Subtle glass fill (chips, wells on glass): white .55 / white .05. */
    val glassSubtle: Int = if (dark) Color.argb(13, 255, 255, 255) else Color.argb(140, 255, 255, 255)

    /** Standard glass border: white .80 in light, white .10 in dark. */
    val glassBorder: Int = if (dark) Color.argb(26, 255, 255, 255) else Color.argb(204, 255, 255, 255)

    /** Floating glass border: white .80 in light, white .12 in dark. */
    val glassBorderFloat: Int = if (dark) Color.argb(31, 255, 255, 255) else Color.argb(204, 255, 255, 255)

    /** 1px top specular highlight colour. */
    val specular: Int = if (dark) Color.argb(31, 255, 255, 255) else Color.argb(170, 255, 255, 255)

    /** Legacy surface colour = the glass card fill. */
    val card: Int = glassCard

    /** Legacy border colour = the standard glass border. */
    val cardBorder: Int = glassBorder

    val text: Int = if (dark) Color.rgb(224, 227, 231) else Color.rgb(28, 28, 23) // #E0E3E7 / #1C1C17
    val textSecondary: Int = if (dark) Color.rgb(186, 203, 191) else Color.rgb(63, 73, 69) // #BACBBF / #3F4945

    /** Accent for text/icons: deep teal in light, bright mint in dark. */
    val primary: Int = if (dark) Color.rgb(110, 255, 195) else Color.rgb(0, 72, 59) // #6EFFC3 / #00483B

    /** Solid action fill: emerald container in light, radiant mint in dark. */
    val primaryContainer: Int = if (dark) Color.rgb(0, 229, 163) else Color.rgb(12, 98, 81) // #00E5A3 / #0C6251
    val onPrimaryContainer: Int = if (dark) Color.rgb(0, 56, 37) else Color.WHITE // #003825 / #FFFFFF
    val primaryDark: Int = if (dark) Color.rgb(0, 201, 138) else Color.rgb(0, 56, 45) // pressed shade (derived)
    val onPrimary: Int = if (dark) Color.rgb(0, 56, 37) else Color.WHITE // #003825 / #FFFFFF

    /** Icon-well / chip tint: primary-fixed in light, mint 12% in dark. */
    val chipBg: Int = if (dark) Color.argb(31, 0, 229, 163) else Color.rgb(166, 241, 219) // rgba mint .12 / #A6F1DB

    /** Thumbnail well behind page previews. */
    val thumbBg: Int = if (dark) Color.argb(230, 18, 24, 28) else Color.rgb(232, 245, 241)

    /** "Synced / connected" signal: sync emerald in light, radiant mint in dark. */
    val success: Int = if (dark) Color.rgb(0, 229, 163) else Color.rgb(5, 150, 105) // #00E5A3 / #059669
    val onSuccess: Int = if (dark) Color.rgb(0, 56, 37) else Color.WHITE

    /** Status pill: synced. */
    val syncedBg: Int = if (dark) Color.argb(38, 0, 229, 163) else Color.argb(166, 166, 241, 219)
    val syncedBorder: Int = if (dark) Color.argb(64, 0, 229, 163) else Color.argb(179, 255, 255, 255)
    val syncedText: Int = if (dark) Color.rgb(110, 255, 195) else Color.rgb(0, 32, 25) // #6EFFC3 / #002019

    /** Status pill: pending sync (marigold/amber). */
    val pendingBg: Int = if (dark) Color.argb(31, 245, 158, 11) else Color.argb(166, 255, 220, 195)
    val pendingBorder: Int = if (dark) Color.argb(64, 245, 158, 11) else Color.argb(179, 255, 255, 255)
    val pendingText: Int = if (dark) Color.rgb(255, 189, 106) else Color.rgb(47, 21, 0) // #FFBD6A / #2F1500
    val pendingDot: Int = if (dark) Color.rgb(245, 158, 11) else Color.rgb(144, 77, 0) // #F59E0B / #904D00

    /** Secondary accent: marigold (light) / amber (dark). */
    val secondary: Int = if (dark) Color.rgb(245, 158, 11) else Color.rgb(254, 147, 44) // #F59E0B / #FE932C
    val secondaryText: Int = if (dark) Color.rgb(255, 189, 106) else Color.rgb(144, 77, 0) // #FFBD6A / #904D00

    /** Tertiary accent: institutional navy (light) / seafoam (dark). */
    val tertiary: Int = if (dark) Color.rgb(77, 208, 184) else Color.rgb(11, 65, 108) // #4DD0B8 / #0B416C

    val danger: Int = if (dark) Color.rgb(255, 180, 171) else Color.rgb(186, 26, 26) // #FFB4AB / #BA1A1A
    val scannerBg: Int = Color.rgb(6, 8, 10)

    /** Card / panel corner radius (dp): 16 in both themes per the Stitch system. */
    val radius: Int = 16

    /** Small controls / thumbnails radius (dp). */
    val radiusSmall: Int = 8

    /** Sheets & dialogs radius (dp). */
    val radiusSheet: Int = 24

    /** Dashboard folder chips are uniform glass now; kept for compatibility. */
    val folderTints: List<Int> = listOf(glassSubtle, glassSubtle, glassSubtle)
}

fun Context.palette(): Palette {
    val dark = getSharedPreferences("drivescan_prefs", Context.MODE_PRIVATE)
        .getBoolean("dark_mode", false)
    return Palette(dark)
}

fun Activity.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
fun android.view.View.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
fun Context.dpF(v: Int): Float = v * resources.displayMetrics.density

fun Context.withAlpha(color: Int, alpha: Int): Int =
    Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

/** Plain rounded fill (+ optional 1dp border). For small inner elements. */
fun Context.rounded(color: Int, radiusDp: Int, borderColor: Int? = null): GradientDrawable {
    val d = GradientDrawable()
    d.shape = GradientDrawable.RECTANGLE
    d.cornerRadius = radiusDp * resources.displayMetrics.density
    d.setColor(color)
    if (borderColor != null) d.setStroke((1 * resources.displayMetrics.density).toInt().coerceAtLeast(1), borderColor)
    return d
}

/**
 * The glass surface drawable: translucent fill + 1dp border + a 1px specular
 * highlight along the top edge (the standard native stand-in for backdrop
 * blur glass).
 */
fun Context.glassDrawable(
    palette: Palette,
    radiusDp: Int,
    fill: Int,
    border: Int,
    highlight: Boolean = true
): LayerDrawable {
    val density = resources.displayMetrics.density
    val base = GradientDrawable()
    base.shape = GradientDrawable.RECTANGLE
    base.cornerRadius = radiusDp * density
    base.setColor(fill)
    base.setStroke((1 * density).toInt().coerceAtLeast(1), border)
    if (!highlight) return LayerDrawable(arrayOf(base))
    val r = radiusDp * density
    val hi = GradientDrawable()
    hi.shape = GradientDrawable.RECTANGLE
    hi.cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
    hi.setColor(palette.specular)
    val layers = LayerDrawable(arrayOf(base, hi))
    val inset = (r * 0.6f).toInt()
    layers.setLayerInset(1, inset, (1 * density).toInt(), inset, 0)
    layers.setLayerHeight(1, (1 * density).toInt().coerceAtLeast(1))
    layers.setLayerGravity(1, Gravity.TOP)
    return layers
}

/** Glass background for cards (floating=false) or floating panels (nav, sheets, HUD). */
fun Context.glassBg(palette: Palette, radiusDp: Int = palette.radius, floating: Boolean = false): Drawable =
    glassDrawable(
        palette, radiusDp,
        fill = if (floating) palette.glassFloat else palette.glassCard,
        border = if (floating) palette.glassBorderFloat else palette.glassBorder
    )

/** Subtle glass background for chips and wells sitting on other surfaces. */
fun Context.glassSubtleBg(palette: Palette, radiusDp: Int): Drawable =
    glassDrawable(palette, radiusDp, fill = palette.glassSubtle, border = palette.glassBorder, highlight = false)

/**
 * Themed gradient canvas every screen sits on: mint/teal washes over
 * alabaster in light mode; near-black with a faint top mint glow in dark.
 */
class BackdropDrawable(private val palette: Palette) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shaders: List<Shader> = emptyList()
    private var baseColor: Int = palette.bg

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return
        shaders = if (palette.dark) {
            listOf(
                // Faint radiant-mint glow at the very top (Obsidian L0).
                RadialGradient(w * 0.5f, 0f, w * 0.95f, Color.argb(12, 0, 229, 163), Color.TRANSPARENT, Shader.TileMode.CLAMP),
                // Seafoam breath, upper right.
                RadialGradient(w * 0.95f, h * 0.18f, w * 0.55f, Color.argb(9, 77, 208, 184), Color.TRANSPARENT, Shader.TileMode.CLAMP),
                // Deep mint breath, lower left.
                RadialGradient(w * 0.05f, h * 0.9f, w * 0.6f, Color.argb(7, 0, 229, 163), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            )
        } else {
            listOf(
                // Base linear wash (145deg: #E8F5F1 → #F4FBF8 → #DFF7EA).
                LinearGradient(
                    0f, 0f, w * 0.25f, h,
                    intArrayOf(Color.rgb(232, 245, 241), Color.rgb(244, 251, 248), Color.rgb(223, 247, 234)),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
                ),
                RadialGradient(w * 0.10f, h * 0.20f, w * 0.5f, Color.argb(115, 167, 243, 208), Color.TRANSPARENT, Shader.TileMode.CLAMP),
                RadialGradient(w * 0.90f, h * 0.10f, w * 0.45f, Color.argb(140, 209, 250, 229), Color.TRANSPARENT, Shader.TileMode.CLAMP),
                RadialGradient(w * 0.85f, h * 0.75f, w * 0.55f, Color.argb(80, 153, 246, 228), Color.TRANSPARENT, Shader.TileMode.CLAMP),
                RadialGradient(w * 0.15f, h * 0.85f, w * 0.5f, Color.argb(70, 187, 247, 208), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            )
        }
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        paint.shader = null
        paint.color = baseColor
        canvas.drawRect(b, paint)
        for (shader in shaders) {
            paint.shader = shader
            canvas.drawRect(b, paint)
        }
        paint.shader = null
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Drawable opacity")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** Applies the themed gradient backdrop to a screen root view. */
fun View.applyBackdrop(palette: Palette) {
    background = BackdropDrawable(palette)
}

/**
 * Gives a shown AlertDialog the floating-glass frame (24dp radius) and, on
 * API 31+, real blur-behind as progressive enhancement. Call after show().
 */
fun Activity.styleGlassDialog(dialog: AlertDialog, palette: Palette) {
    try {
        val window = dialog.window ?: return
        window.setBackgroundDrawable(glassBg(palette, palette.radiusSheet, floating = true))
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val attrs = window.attributes
                attrs.blurBehindRadius = (20 * resources.displayMetrics.density).toInt()
                window.attributes = attrs
            } catch (e: Exception) {
                // Blur-behind is an enhancement only; the glass frame stands alone.
            }
        }
    } catch (e: Exception) {
        // Dialog styling must never break the dialog itself.
    }
}

/** Center-crops [bmp] into a circle of [sizePx] (profile avatars). */
fun Bitmap.toCircle(sizePx: Int): Bitmap {
    val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val scale = maxOf(sizePx.toFloat() / width, sizePx.toFloat() / height)
    val dw = width * scale
    val dh = height * scale
    val shader = android.graphics.BitmapShader(this, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    val matrix = android.graphics.Matrix()
    matrix.setScale(scale, scale)
    matrix.postTranslate((sizePx - dw) / 2f, (sizePx - dh) / 2f)
    shader.setLocalMatrix(matrix)
    paint.shader = shader
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)
    return out
}

/** Compact byte formatter shared by dashboard/settings ("2.4 MB"). */
fun formatBytesShort(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> String.format(Locale.ENGLISH, "%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format(Locale.ENGLISH, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
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
        // Obsidian sets meta text (counts, dates, sizes) in mono — approximated
        // with the system monospace face (JetBrains Mono stand-in).
        if (palette.dark) typeface = Typeface.MONOSPACE
    }

/** Solid primary action: emerald gradient in light, radiant mint gradient in dark. */
fun Context.primaryButton(text: String): Button {
    val palette = palette()
    return Button(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(palette.onPrimaryContainer)
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        isAllCaps = false
        val gradient = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            if (palette.dark) {
                intArrayOf(Color.rgb(110, 255, 195), Color.rgb(0, 229, 163))
            } else {
                intArrayOf(Color.rgb(12, 98, 81), Color.rgb(0, 72, 59))
            }
        )
        gradient.cornerRadius = 12 * resources.displayMetrics.density
        background = gradient
        setPadding(28, 14, 28, 14)
    }
}

/** Secondary glass action. */
fun Context.ghostButton(text: String, palette: Palette): Button =
    Button(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(palette.text)
        isAllCaps = false
        background = glassSubtleBg(palette, 12)
        setPadding(22, 12, 22, 12)
    }

fun Context.inputField(hint: String, palette: Palette): EditText =
    EditText(this).apply {
        this.hint = hint
        textSize = 15f
        setTextColor(palette.text)
        setHintTextColor(palette.textSecondary)
        val fill = if (palette.dark) Color.argb(153, 11, 15, 18) else Color.rgb(246, 243, 236)
        val border = if (palette.dark) palette.glassBorder else Color.rgb(190, 201, 196)
        background = rounded(fill, 12, border)
        setPadding(24, 16, 24, 16)
    }

/** A glass card container with the app's standard padding. */
fun Context.card(palette: Palette): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = glassBg(palette, palette.radius)
        elevation = (if (palette.dark) 7f else 5f) * resources.displayMetrics.density
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

/**
 * Floating glass bottom navigation (Stitch): each item is a pill icon well
 * over its label; the active item's pill fills with the primary tint and its
 * label turns accent + bold.
 */
fun Activity.bottomNav(palette: Palette, active: String): LinearLayout {
    val bar = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(8), dp(8), dp(10))
        background = glassDrawable(palette, 0, palette.glassFloat, palette.glassBorderFloat, highlight = false)
        elevation = dp(10).toFloat()
    }
    val items = listOf(
        Triple("home", "⌂", Str.NAV_HOME),
        Triple("vault", "🔒", Str.NAV_VAULT),
        Triple("bin", "🗑", Str.NAV_BIN),
        Triple("settings", "⚙", Str.NAV_SETTINGS)
    )
    for ((key, glyph, labelKey) in items) {
        val isActive = key == active
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            isClickable = true
        }
        val pill = TextView(this).apply {
            text = glyph
            textSize = 16f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(32))
            if (isActive) {
                background = rounded(
                    if (palette.dark) Color.argb(51, 0, 229, 163) else Color.argb(217, 166, 241, 219),
                    999
                )
                setTextColor(if (palette.dark) palette.primary else Color.rgb(0, 32, 25))
            } else {
                setTextColor(palette.textSecondary)
            }
        }
        item.addView(pill)
        item.addView(TextView(this).apply {
            text = tr(labelKey).dropWhile { !it.isLetter() }.trim()
            textSize = 11.5f
            gravity = Gravity.CENTER
            setTextColor(if (isActive) palette.primary else palette.textSecondary)
            if (isActive) typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(2) }
        })
        item.setOnClickListener {
            if (isActive) return@setOnClickListener
            val target = when (key) {
                "home" -> com.drivescan.kmp.MainActivity::class.java
                "vault" -> com.drivescan.kmp.VaultActivity::class.java
                "bin" -> com.drivescan.kmp.BinActivity::class.java
                else -> com.drivescan.kmp.SettingsActivity::class.java
            }
            startActivity(android.content.Intent(this, target))
            if (key != "home") finish()
        }
        bar.addView(item)
    }
    return bar
}
