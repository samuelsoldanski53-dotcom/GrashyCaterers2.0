package com.example.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.example.R

object StudioColors {
    val BrandAccent = Color(0xFFC45911)
    val BrandAccentHover = Color(0xFFA8490C)
    val BrandSoftBg = Color(0xFFFFF7ED)
    val BrandSoftBorder = Color(0xFFFDBA74)
    val StudioBg = Color(0xFFEEF0F3)
    val CanvasTrayBg = Color(0xFFE5E8EC)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceMuted = Color(0xFFF8FAFC)
    val Border = Color(0xFFDDE1E7)
    val TextPrimary = Color(0xFF0F172A)
    val TextSecondary = Color(0xFF475569)
    val TextMuted = Color(0xFF64748B)
    val GuideSky = Color(0xFF38BDF8)
    val SuccessBg = Color(0xFFECFDF5)
    val SuccessBorder = Color(0xFFA7F3D0)
    val SuccessText = Color(0xFF065F46)
    val WarningBg = Color(0xFFFFFBEB)
    val WarningText = Color(0xFF92400E)
    val ErrorBg = Color(0xFFFEF2F2)
    val ErrorText = Color(0xFF991B1B)
}

val PlusJakartaSansFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold)
)

val DmSansFamily = FontFamily(
    Font(R.font.dm_sans, FontWeight.Normal),
    Font(R.font.dm_sans_medium, FontWeight.Medium),
    Font(R.font.dm_sans_bold, FontWeight.SemiBold),
    Font(R.font.dm_sans_bold, FontWeight.Bold)
)

val PlayfairDisplayFamily = FontFamily(Font(R.font.playfair_display, FontWeight.SemiBold))
val LoraFamily = FontFamily(Font(R.font.lora, FontWeight.Normal))
val OutfitFamily = FontFamily(Font(R.font.outfit, FontWeight.Medium))
val MontserratFamily = FontFamily(Font(R.font.montserrat, FontWeight.SemiBold))
val SourceSerif4Family = FontFamily(Font(R.font.source_serif_4, FontWeight.Normal))
val JetBrainsMonoFamily = FontFamily(Font(R.font.jetbrains_mono, FontWeight.Normal))

fun resolveComposeFontFamily(fontName: String): FontFamily {
    return when (fontName.lowercase()) {
        "plus jakarta sans" -> PlusJakartaSansFamily
        "dm sans" -> DmSansFamily
        "playfair display" -> PlayfairDisplayFamily
        "lora" -> LoraFamily
        "outfit" -> OutfitFamily
        "montserrat" -> MontserratFamily
        "source serif 4" -> SourceSerif4Family
        "jetbrains mono" -> JetBrainsMonoFamily
        else -> DmSansFamily
    }
}

fun resolveAndroidTypeface(context: Context, fontName: String, weight: Int, italic: Boolean): Typeface {
    val resId = when (fontName.lowercase()) {
        "plus jakarta sans" -> if (weight >= 600) R.font.plus_jakarta_sans_bold else R.font.plus_jakarta_sans
        "dm sans" -> when {
            weight >= 600 -> R.font.dm_sans_bold
            weight >= 500 -> R.font.dm_sans_medium
            else -> R.font.dm_sans
        }
        "playfair display" -> R.font.playfair_display
        "lora" -> R.font.lora
        "outfit" -> R.font.outfit
        "montserrat" -> R.font.montserrat
        "source serif 4" -> R.font.source_serif_4
        "jetbrains mono" -> R.font.jetbrains_mono
        else -> R.font.dm_sans
    }
    val base = runCatching { ResourcesCompat.getFont(context, resId) }.getOrNull() ?: Typeface.SANS_SERIF
    val style = when {
        weight >= 600 && italic -> Typeface.BOLD_ITALIC
        weight >= 600 -> Typeface.BOLD
        italic -> Typeface.ITALIC
        else -> Typeface.NORMAL
    }
    return Typeface.create(base, style)
}

fun parseHexColor(hex: String?, fallback: Color = StudioColors.TextPrimary): Color {
    if (hex.isNullOrBlank() || hex.equals("transparent", ignoreCase = true)) return Color.Transparent
    val clean = hex.removePrefix("#").trim()
    return runCatching {
        when (clean.length) {
            3 -> {
                val r = "${clean[0]}${clean[0]}".toInt(16)
                val g = "${clean[1]}${clean[1]}".toInt(16)
                val b = "${clean[2]}${clean[2]}".toInt(16)
                Color(r, g, b)
            }
            6 -> Color(android.graphics.Color.parseColor("#$clean"))
            8 -> Color(android.graphics.Color.parseColor("#$clean"))
            else -> fallback
        }
    }.getOrDefault(fallback)
}

private val LightColorScheme = lightColorScheme(
    primary = StudioColors.BrandAccent,
    onPrimary = Color.White,
    primaryContainer = StudioColors.BrandSoftBg,
    onPrimaryContainer = Color(0xFF7C2D12),
    secondary = Color(0xFF334155),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1F5F9),
    onSecondaryContainer = StudioColors.TextPrimary,
    background = StudioColors.StudioBg,
    onBackground = StudioColors.TextPrimary,
    surface = StudioColors.Surface,
    onSurface = StudioColors.TextPrimary,
    surfaceVariant = StudioColors.SurfaceMuted,
    onSurfaceVariant = StudioColors.TextSecondary,
    outline = StudioColors.Border
)

private val StudioTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    titleSmall = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = DmSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodySmall = TextStyle(
        fontFamily = DmSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp,
        lineHeight = 16.sp
    ),
    labelMedium = TextStyle(
        fontFamily = DmSansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

@Composable
fun GrashyStudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = StudioTypography,
        content = content
    )
}
