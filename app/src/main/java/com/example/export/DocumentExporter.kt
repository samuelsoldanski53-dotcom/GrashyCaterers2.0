package com.example.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.R
import com.example.domain.Geometry
import com.example.domain.GrashyInitialProject
import com.example.model.ElementRole
import com.example.model.ElementType
import com.example.model.LetterheadElement
import com.example.model.LetterheadProject
import com.example.model.TextAlignOption
import com.example.model.TextStyleConfig
import com.example.ui.theme.resolveAndroidTypeface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.StringReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object DocumentExporter {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    data class ExportFileResult(
        val uri: Uri,
        val fileName: String,
        val mimeType: String,
        val savedFolderLabel: String = "Downloads/GrashyStudio"
    )

    private fun copyToPublicDownloads(
        context: Context,
        sourceFile: File,
        fileName: String,
        mimeType: String
    ): String {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/GrashyStudio")
                }
                val targetUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (targetUri != null) {
                    resolver.openOutputStream(targetUri)?.use { out ->
                        sourceFile.inputStream().use { inp -> inp.copyTo(out) }
                    }
                    "Downloads/GrashyStudio/$fileName"
                } else {
                    "App Exports/$fileName"
                }
            } else {
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "GrashyStudio"
                ).apply { mkdirs() }
                val dest = File(dir, fileName)
                sourceFile.copyTo(dest, overwrite = true)
                "Downloads/GrashyStudio/$fileName"
            }
        }.getOrDefault("App Exports/$fileName")
    }

    sealed class ImportResult {
        data class LoadedProject(val project: LetterheadProject, val message: String) : ImportResult()
        data class AddedElements(val elements: List<LetterheadElement>, val message: String) : ImportResult()
    }

    private fun slugify(name: String): String {
        return name.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifEmpty { "letterhead" }
    }

    private fun exportsDir(context: Context): File {
        return File(context.cacheDir, "exports").apply { mkdirs() }
    }

    private fun importedAssetsDir(context: Context): File {
        return File(context.filesDir, "imported_assets").apply { mkdirs() }
    }

    fun copyUriToInternalImageFile(context: Context, uri: Uri): String {
        val rawExt = context.contentResolver.getType(uri)
            ?.substringAfter('/')
            ?.substringBefore(';')
            ?.substringBefore('+')
            ?.lowercase()
            ?.replace(Regex("[^a-z0-9]"), "")
            ?.takeIf { it.isNotBlank() } ?: "png"
        val outFile = File(importedAssetsDir(context), "img_${System.currentTimeMillis()}.$rawExt")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(outFile).use { output -> input.copyTo(output) }
        } ?: error("Unable to read selected image.")
        return Uri.fromFile(outFile).toString()
    }

    fun loadBitmapForImageSrc(context: Context, imageSrc: String?): Bitmap? {
        if (imageSrc.isNullOrBlank()) return null
        return when {
            imageSrc == GrashyInitialProject.ASSET_LOGO || imageSrc.contains("grashy-logo") ->
                BitmapFactory.decodeResource(context.resources, R.drawable.grashy_logo)
            imageSrc == GrashyInitialProject.ASSET_FOOTER_WAVE || imageSrc.contains("grashy-footer-wave") ->
                BitmapFactory.decodeResource(context.resources, R.drawable.grashy_footer_wave)
            imageSrc.startsWith("data:") -> {
                val b64 = imageSrc.substringAfter("base64,", "")
                if (b64.isEmpty()) null else runCatching {
                    val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }.getOrNull()
            }
            else -> runCatching {
                val uri = Uri.parse(imageSrc)
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
    }

    private fun parseColorInt(hex: String?, fallback: Int): Int {
        if (hex.isNullOrBlank() || hex.equals("transparent", ignoreCase = true)) return android.graphics.Color.TRANSPARENT
        return runCatching {
            val clean = hex.removePrefix("#").trim()
            if (clean.length == 3) {
                val expanded = "${clean[0]}${clean[0]}${clean[1]}${clean[1]}${clean[2]}${clean[2]}"
                android.graphics.Color.parseColor("#$expanded")
            } else {
                android.graphics.Color.parseColor("#$clean")
            }
        }.getOrDefault(fallback)
    }

    suspend fun exportToPdf(context: Context, project: LetterheadProject): ExportFileResult = withContext(Dispatchers.IO) {
        // A4 at 144 dpi (~2x points: 1190 x 1684) for crisp typography & graphics
        val scale = 2f
        val mmToPx = (72f / 25.4f) * scale
        val pageW = (Geometry.A4_WIDTH_MM * mmToPx).roundToInt() // ~1191
        val pageH = (Geometry.A4_HEIGHT_MM * mmToPx).roundToInt() // ~1684

        val pdf = PdfDocument()
        try {
            for (pageIdx in 0 until project.pageCount) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageIdx + 1).create()
            val page = pdf.startPage(pageInfo)
            try {
            val canvas: Canvas = page.canvas
            canvas.drawColor(android.graphics.Color.WHITE)

            val pageElements = project.elements
                .filter { it.pageIndex == pageIdx && it.visible }
                .sortedBy { it.zIndex }

            // Measure BODY text overflow so menu items are never cut off
            var bodyOverflowPx = 0f
            val bodyEl = pageElements.find { it.role == ElementRole.BODY && it.type == ElementType.TEXT }
            val bodyBottomY = bodyEl?.let { (it.y + it.height) * mmToPx } ?: Float.MAX_VALUE
            if (bodyEl != null && bodyEl.textStyle != null && !bodyEl.content.isNullOrEmpty()) {
                val ts = bodyEl.textStyle
                val innerW = max(12, ((bodyEl.width - ts.paddingLeft - ts.paddingRight) * mmToPx).roundToInt())
                val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = ts.fontSize * scale
                    typeface = resolveAndroidTypeface(context, ts.fontFamily, ts.fontWeight, ts.fontStyle == "italic")
                    letterSpacing = ts.letterSpacing
                }
                val safeText = bodyEl.content.replace(Regex("💫\\s*"), "• ")
                val measured = StaticLayout.Builder.obtain(safeText, 0, safeText.length, tp, innerW)
                    .setLineSpacing(0f, ts.lineHeight.coerceAtLeast(0.9f))
                    .setIncludePad(false)
                    .build()
                val neededH = measured.height + (ts.paddingTop + ts.paddingBottom) * mmToPx + 6f
                val boxH = bodyEl.height * mmToPx
                if (neededH > boxH) {
                    bodyOverflowPx = neededH - boxH
                }
            }

            for (el in pageElements) {
                val shiftDownPx = if (
                    bodyOverflowPx > 0f &&
                    el.role != ElementRole.BODY &&
                    el.role != ElementRole.FOOTER_WAVE &&
                    (el.y * mmToPx) >= bodyBottomY - 4f
                ) {
                    bodyOverflowPx
                } else {
                    0f
                }
                val left = el.x * mmToPx
                val top = el.y * mmToPx + shiftDownPx
                val width = el.width * mmToPx
                val height = if (el.role == ElementRole.BODY && bodyOverflowPx > 0f) {
                    el.height * mmToPx + bodyOverflowPx
                } else {
                    el.height * mmToPx
                }
                val alphaInt = (el.opacity.coerceIn(0f, 1f) * 255).roundToInt()

                canvas.save()
                if (el.rotation != 0f) {
                    canvas.rotate(el.rotation, left + width / 2f, top + height / 2f)
                }

                when (el.type) {
                    ElementType.IMAGE -> {
                        val bmp = loadBitmapForImageSrc(context, el.imageSrc)
                        if (bmp != null) {
                            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                                alpha = alphaInt
                            }
                            val dst = if (el.objectFit == "contain") {
                                val imgRatio = bmp.width.toFloat() / max(1, bmp.height)
                                val boxRatio = width / max(1f, height)
                                if (imgRatio > boxRatio) {
                                    val fitH = width / imgRatio
                                    val dy = (height - fitH) / 2f
                                    RectF(left, top + dy, left + width, top + dy + fitH)
                                } else {
                                    val fitW = height * imgRatio
                                    val dx = (width - fitW) / 2f
                                    RectF(left + dx, top, left + dx + fitW, top + height)
                                }
                            } else {
                                RectF(left, top, left + width, top + height)
                            }
                            canvas.drawBitmap(bmp, null, dst, paint)
                        }
                    }
                    ElementType.LINE -> {
                        val ss = el.shapeStyle
                        if (ss != null && !ss.stroke.equals("transparent", true) && ss.strokeWidth > 0f) {
                            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                style = Paint.Style.STROKE
                                color = parseColorInt(ss.stroke, android.graphics.Color.BLACK)
                                alpha = alphaInt
                                strokeWidth = max(1f, ss.strokeWidth * scale)
                                pathEffect = when (ss.lineStyle) {
                                    "dashed" -> DashPathEffect(floatArrayOf(6f * scale, 4f * scale), 0f)
                                    "dotted" -> DashPathEffect(floatArrayOf(2f * scale, 3f * scale), 0f)
                                    else -> null
                                }
                            }
                            val midY = top + height / 2f
                            canvas.drawLine(left, midY, left + width, midY, paint)
                        }
                    }
                    ElementType.SHAPE, ElementType.TEXT -> {
                        val ss = el.shapeStyle
                        if (ss != null) {
                            val rect = RectF(left, top, left + width, top + height)
                            val radiusPx = ss.borderRadius * mmToPx
                            val fillColor = parseColorInt(ss.fill, android.graphics.Color.TRANSPARENT)
                            if (fillColor != android.graphics.Color.TRANSPARENT) {
                                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    style = Paint.Style.FILL
                                    color = fillColor
                                    alpha = alphaInt
                                }
                                canvas.drawRoundRect(rect, radiusPx, radiusPx, fillPaint)
                            }
                            val strokeColor = parseColorInt(ss.stroke, android.graphics.Color.TRANSPARENT)
                            if (strokeColor != android.graphics.Color.TRANSPARENT && ss.strokeWidth > 0f) {
                                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    style = Paint.Style.STROKE
                                    color = strokeColor
                                    alpha = alphaInt
                                    strokeWidth = ss.strokeWidth * scale
                                    pathEffect = when (ss.lineStyle) {
                                        "dashed" -> DashPathEffect(floatArrayOf(6f * scale, 4f * scale), 0f)
                                        "dotted" -> DashPathEffect(floatArrayOf(2f * scale, 3f * scale), 0f)
                                        else -> null
                                    }
                                }
                                canvas.drawRoundRect(rect, radiusPx, radiusPx, strokePaint)
                            }
                        }

                        if (el.type == ElementType.TEXT && el.textStyle != null && !el.content.isNullOrEmpty()) {
                            val ts = el.textStyle
                            val padL = ts.paddingLeft * mmToPx
                            val padR = ts.paddingRight * mmToPx
                            val padT = ts.paddingTop * mmToPx
                            val innerW = max(12, (width - padL - padR).roundToInt())
                            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = parseColorInt(ts.color, android.graphics.Color.BLACK)
                                alpha = alphaInt
                                textSize = ts.fontSize * scale
                                typeface = resolveAndroidTypeface(
                                    context,
                                    ts.fontFamily,
                                    ts.fontWeight,
                                    ts.fontStyle == "italic"
                                )
                                letterSpacing = ts.letterSpacing
                            }
                            val align = when (ts.textAlign) {
                                TextAlignOption.CENTER -> Layout.Alignment.ALIGN_CENTER
                                TextAlignOption.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                                else -> Layout.Alignment.ALIGN_NORMAL
                            }
                            val safeText = el.content.replace(Regex("💫\\s*"), "• ")
                            val staticLayout = StaticLayout.Builder.obtain(
                                safeText, 0, safeText.length, textPaint, innerW
                            )
                                .setAlignment(align)
                                .setLineSpacing(0f, ts.lineHeight.coerceAtLeast(0.9f))
                                .setIncludePad(false)
                                .build()

                            canvas.save()
                            val clipBottom = max(top + height, top + padT + staticLayout.height + 6f)
                            canvas.clipRect(left, top, left + width, clipBottom)
                            canvas.translate(left + padL, top + padT)
                            staticLayout.draw(canvas)
                            canvas.restore()
                        }
                    }
                }
                canvas.restore()
            }
            } finally {
                pdf.finishPage(page)
            }
        }

            val fileName = "${slugify(project.name)}.pdf"
            val outFile = File(exportsDir(context), fileName)
            FileOutputStream(outFile).use { pdf.writeTo(it) }
            val savedLabel = copyToPublicDownloads(context, outFile, fileName, "application/pdf")

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
            ExportFileResult(uri, fileName, "application/pdf", savedLabel)
        } finally {
            runCatching { pdf.close() }
        }
    }

    private fun xmlEscape(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun normalizeDocxHex(hex: String?, fallback: String = "1E293B"): String {
        val clean = (hex ?: "").removePrefix("#").trim()
        return when {
            clean.matches(Regex("^[0-9a-fA-F]{3}$")) ->
                "${clean[0]}${clean[0]}${clean[1]}${clean[1]}${clean[2]}${clean[2]}".uppercase()
            clean.matches(Regex("^[0-9a-fA-F]{6}$")) -> clean.uppercase()
            else -> fallback
        }
    }

    private fun mmToTwips(mm: Float): Int = ((mm / 25.4f) * 1440f).roundToInt()
    private fun mmToEmu(mm: Float): Long = ((mm / 25.4f) * 914400f).toLong()

    suspend fun exportToDocx(context: Context, project: LetterheadProject): ExportFileResult = withContext(Dispatchers.IO) {
        val margins = project.pageSettings.margins
        val pageZero = project.elements.filter { it.pageIndex == 0 && it.visible }

        val logoEl = pageZero.find { it.role == ElementRole.LOGO }
        val titleEl = pageZero.find { it.role == ElementRole.COMPANY_TITLE }
        val subtitleEl = pageZero.find { it.role == ElementRole.COMPANY_SUBTITLE }
        val contactEl = pageZero.find { it.role == ElementRole.CONTACT }
        val metaEl = pageZero.find { it.role == ElementRole.EVENT_META }
        val closingEl = pageZero.find { it.role == ElementRole.CLOSING }
        val bankEl = pageZero.find { it.role == ElementRole.PAYMENT_BANK }
        val mpesaEl = pageZero.find { it.role == ElementRole.PAYMENT_MPESA }
        val waveEl = pageZero.find { it.role == ElementRole.FOOTER_WAVE }

        val mediaEntries = mutableListOf<Triple<String, String, ByteArray>>() // rId, fileName, bytes
        var nextRelId = 1

        fun addImageMedia(el: LetterheadElement?, prefix: String): String? {
            if (el?.imageSrc == null) return null
            val bmp = loadBitmapForImageSrc(context, el.imageSrc) ?: return null
            val baos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
            val rId = "rId${nextRelId++}"
            val mediaName = "${prefix}_${rId}.png"
            mediaEntries.add(Triple(rId, mediaName, baos.toByteArray()))
            return rId
        }

        fun imageRunXml(rId: String, widthMm: Float, heightMm: Float, docPrId: Int): String {
            val cx = mmToEmu(widthMm)
            val cy = mmToEmu(heightMm)
            return """
                <w:r>
                  <w:drawing>
                    <wp:inline distT="0" distB="0" distL="0" distR="0">
                      <wp:extent cx="$cx" cy="$cy"/>
                      <wp:docPr id="$docPrId" name="Image $docPrId"/>
                      <wp:cNvGraphicFramePr/>
                      <a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                        <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
                          <pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
                            <pic:nvPicPr><pic:cNvPr id="$docPrId" name="pic$docPrId"/><pic:cNvPicPr/></pic:nvPicPr>
                            <pic:blipFill><a:blip r:embed="$rId"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>
                            <pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr>
                          </pic:pic>
                        </a:graphicData>
                      </a:graphic>
                    </wp:inline>
                  </w:drawing>
                </w:r>
            """.trimIndent()
        }

        fun textParagraphXml(
            text: String,
            font: String,
            sizePt: Float,
            colorHex: String,
            bold: Boolean = false,
            italic: Boolean = false,
            align: String = "left",
            afterSpacing: Int = 60,
            beforeSpacing: Int = 0
        ): String {
            val sz = (sizePt * 2f).roundToInt()
            val jc = when (align) {
                "right" -> """<w:jc w:val="right"/>"""
                "center" -> """<w:jc w:val="center"/>"""
                else -> ""
            }
            val bXml = if (bold) "<w:b/>" else ""
            val iXml = if (italic) "<w:i/>" else ""
            val cleanHex = normalizeDocxHex(colorHex)
            return """
                <w:p>
                  <w:pPr><w:spacing w:before="$beforeSpacing" w:after="$afterSpacing"/>$jc</w:pPr>
                  <w:r>
                    <w:rPr><w:rFonts w:ascii="${xmlEscape(font)}" w:hAnsi="${xmlEscape(font)}"/><w:color w:val="$cleanHex"/><w:sz w:val="$sz"/>$bXml$iXml</w:rPr>
                    <w:t xml:space="preserve">${xmlEscape(text)}</w:t>
                  </w:r>
                </w:p>
            """.trimIndent()
        }

        val bodyXml = StringBuilder()
        var docPrCounter = 1

        // 1. Header Table
        val logoRId = addImageMedia(logoEl, "logo")
        val leftCellXml = if (logoRId != null && logoEl != null) {
            "<w:p>${imageRunXml(logoRId, logoEl.width, logoEl.height, docPrCounter++)}</w:p>"
        } else {
            "<w:p/>"
        }

        val rightHeader = StringBuilder()
        if (titleEl != null) {
            rightHeader.append(
                textParagraphXml(
                    titleEl.content ?: "Grashy Caterers",
                    titleEl.textStyle?.fontFamily ?: "DM Sans",
                    titleEl.textStyle?.fontSize ?: 21f,
                    titleEl.textStyle?.color ?: "C45911",
                    bold = true,
                    align = "right",
                    afterSpacing = 30
                )
            )
        }
        if (subtitleEl != null) {
            rightHeader.append(
                textParagraphXml(
                    subtitleEl.content ?: "",
                    subtitleEl.textStyle?.fontFamily ?: "DM Sans",
                    subtitleEl.textStyle?.fontSize ?: 8f,
                    subtitleEl.textStyle?.color ?: "64748B",
                    bold = true,
                    align = "right",
                    afterSpacing = 60
                )
            )
        }
        if (contactEl != null) {
            for (line in (contactEl.content ?: "").split("\n")) {
                rightHeader.append(
                    textParagraphXml(
                        line,
                        contactEl.textStyle?.fontFamily ?: "DM Sans",
                        contactEl.textStyle?.fontSize ?: 8.5f,
                        contactEl.textStyle?.color ?: "334155",
                        align = "right",
                        afterSpacing = 20
                    )
                )
            }
        }
        if (rightHeader.isEmpty()) rightHeader.append("<w:p/>")

        bodyXml.append(
            """
            <w:tbl>
              <w:tblPr>
                <w:tblW w:w="5000" w:type="pct"/>
                <w:tblBorders>
                  <w:top w:val="none"/><w:left w:val="none"/><w:right w:val="none"/>
                  <w:insideH w:val="none"/><w:insideV w:val="none"/>
                  <w:bottom w:val="single" w:sz="12" w:space="0" w:color="C45911"/>
                </w:tblBorders>
              </w:tblPr>
              <w:tr>
                <w:tc><w:tcPr><w:tcW w:w="2000" w:type="pct"/></w:tcPr>$leftCellXml</w:tc>
                <w:tc><w:tcPr><w:tcW w:w="3000" w:type="pct"/></w:tcPr>$rightHeader</w:tc>
              </w:tr>
            </w:tbl>
            <w:p><w:pPr><w:spacing w:before="80" w:after="80"/></w:pPr></w:p>
            """.trimIndent()
        )

        // 2. Event Metadata Card
        val dateEl = pageZero.find { it.role == ElementRole.EVENT_DATE }
        val paxEl = pageZero.find { it.role == ElementRole.EVENT_PAX }
        val clientEl = pageZero.find { it.role == ElementRole.CLIENT_NAME }
        val venueEl = pageZero.find { it.role == ElementRole.EVENT_VENUE }
        val splitMeta = listOfNotNull(
            listOfNotNull(dateEl?.content, paxEl?.content).joinToString("   |   ").takeIf { it.isNotBlank() },
            clientEl?.content?.takeIf { it.isNotBlank() },
            venueEl?.content?.takeIf { it.isNotBlank() }
        )
        val metaLines = if (metaEl != null) (metaEl.content ?: "").split("\n") else splitMeta
        if (metaLines.isNotEmpty()) {
            val refStyle = metaEl?.textStyle ?: clientEl?.textStyle
            val metaParas = metaLines.joinToString("") { line ->
                textParagraphXml(
                    line,
                    refStyle?.fontFamily ?: "DM Sans",
                    refStyle?.fontSize ?: 10f,
                    refStyle?.color ?: "9A3412",
                    bold = true,
                    afterSpacing = 40
                )
            }
            bodyXml.append(
                """
                <w:tbl>
                  <w:tblPr>
                    <w:tblW w:w="5000" w:type="pct"/>
                    <w:tblBorders>
                      <w:top w:val="single" w:sz="4" w:color="FDBA74"/><w:bottom w:val="single" w:sz="4" w:color="FDBA74"/>
                      <w:left w:val="single" w:sz="4" w:color="FDBA74"/><w:right w:val="single" w:sz="4" w:color="FDBA74"/>
                    </w:tblBorders>
                  </w:tblPr>
                  <w:tr><w:tc>
                    <w:tcPr><w:shd w:val="clear" w:fill="FFF7ED"/><w:tcMar><w:top w:w="120" w:type="dxa"/><w:bottom w:w="120" w:type="dxa"/><w:left w:w="160" w:type="dxa"/><w:right w:w="160" w:type="dxa"/></w:tcMar></w:tcPr>
                    $metaParas
                  </w:tc></w:tr>
                </w:tbl>
                <w:p><w:pPr><w:spacing w:after="120"/></w:pPr></w:p>
                """.trimIndent()
            )
        }

        // 3. Body & Custom text
        val bodyAndCustom = pageZero
            .filter { it.type == ElementType.TEXT && (it.role == ElementRole.BODY || it.role == ElementRole.CUSTOM) }
            .sortedBy { it.y }
        for (textEl in bodyAndCustom) {
            val ts = textEl.textStyle
            for (line in (textEl.content ?: "").split("\n")) {
                bodyXml.append(
                    textParagraphXml(
                        line.replace("💫", "•"),
                        ts?.fontFamily ?: "DM Sans",
                        ts?.fontSize ?: 10.5f,
                        ts?.color ?: "1E293B",
                        bold = (ts?.fontWeight ?: 400) >= 600,
                        italic = ts?.fontStyle == "italic",
                        afterSpacing = 60
                    )
                )
            }
        }

        // 4. Cost summary box
        val costEl = pageZero.find { it.role == ElementRole.COST_SUMMARY }
        if (costEl != null) {
            val lines = (costEl.content ?: "").split("\n")
            val costParas = lines.mapIndexed { idx, line ->
                textParagraphXml(
                    line,
                    costEl.textStyle?.fontFamily ?: "Plus Jakarta Sans",
                    costEl.textStyle?.fontSize ?: 9.5f,
                    costEl.textStyle?.color ?: "0F172A",
                    bold = idx == 0 || idx == lines.lastIndex,
                    afterSpacing = 40
                )
            }.joinToString("")
            bodyXml.append(
                """
                <w:p><w:pPr><w:spacing w:before="80"/></w:pPr></w:p>
                <w:tbl>
                  <w:tblPr>
                    <w:tblW w:w="5000" w:type="pct"/>
                    <w:tblBorders>
                      <w:top w:val="single" w:sz="4" w:color="CBD5E1"/><w:bottom w:val="single" w:sz="4" w:color="CBD5E1"/>
                      <w:left w:val="single" w:sz="4" w:color="CBD5E1"/><w:right w:val="single" w:sz="4" w:color="CBD5E1"/>
                    </w:tblBorders>
                  </w:tblPr>
                  <w:tr><w:tc>
                    <w:tcPr><w:shd w:val="clear" w:fill="F8FAFC"/><w:tcMar><w:top w:w="100" w:type="dxa"/><w:bottom w:w="100" w:type="dxa"/><w:left w:w="160" w:type="dxa"/><w:right w:w="160" w:type="dxa"/></w:tcMar></w:tcPr>
                    $costParas
                  </w:tc></w:tr>
                </w:tbl>
                """.trimIndent()
            )
        }

        // 5. Closing note
        if (closingEl != null) {
            for (line in (closingEl.content ?: "").split("\n")) {
                bodyXml.append(
                    textParagraphXml(
                        line,
                        closingEl.textStyle?.fontFamily ?: "DM Sans",
                        closingEl.textStyle?.fontSize ?: 9.5f,
                        closingEl.textStyle?.color ?: "334155",
                        bold = true,
                        italic = true,
                        beforeSpacing = 120,
                        afterSpacing = 60
                    )
                )
            }
        }

        // 6. Bank & M-Pesa 2-column table
        if (bankEl != null || mpesaEl != null) {
            val bankParas = (bankEl?.content ?: "").split("\n").joinToString("") {
                textParagraphXml(it, bankEl?.textStyle?.fontFamily ?: "DM Sans", bankEl?.textStyle?.fontSize ?: 9.5f, bankEl?.textStyle?.color ?: "1E293B", afterSpacing = 40)
            }.ifEmpty { "<w:p/>" }
            val mpesaParas = (mpesaEl?.content ?: "").split("\n").joinToString("") {
                textParagraphXml(it, mpesaEl?.textStyle?.fontFamily ?: "DM Sans", mpesaEl?.textStyle?.fontSize ?: 9.5f, mpesaEl?.textStyle?.color ?: "1E293B", afterSpacing = 40)
            }.ifEmpty { "<w:p/>" }
            bodyXml.append(
                """
                <w:tbl>
                  <w:tblPr>
                    <w:tblW w:w="5000" w:type="pct"/>
                    <w:tblBorders>
                      <w:top w:val="single" w:sz="4" w:color="E2E8F0"/><w:bottom w:val="single" w:sz="4" w:color="E2E8F0"/>
                      <w:left w:val="single" w:sz="4" w:color="E2E8F0"/><w:right w:val="single" w:sz="4" w:color="E2E8F0"/>
                      <w:insideV w:val="single" w:sz="4" w:color="E2E8F0"/>
                    </w:tblBorders>
                  </w:tblPr>
                  <w:tr>
                    <w:tc><w:tcPr><w:tcW w:w="2750" w:type="pct"/><w:shd w:val="clear" w:fill="F8FAFC"/><w:tcMar><w:top w:w="100" w:type="dxa"/><w:bottom w:w="100" w:type="dxa"/><w:left w:w="140" w:type="dxa"/><w:right w:w="140" w:type="dxa"/></w:tcMar></w:tcPr>$bankParas</w:tc>
                    <w:tc><w:tcPr><w:tcW w:w="2250" w:type="pct"/><w:shd w:val="clear" w:fill="F8FAFC"/><w:tcMar><w:top w:w="100" w:type="dxa"/><w:bottom w:w="100" w:type="dxa"/><w:left w:w="140" w:type="dxa"/><w:right w:w="140" w:type="dxa"/></w:tcMar></w:tcPr>$mpesaParas</w:tc>
                  </w:tr>
                </w:tbl>
                """.trimIndent()
            )
        }

        // 7. Footer wave
        val waveRId = addImageMedia(waveEl, "wave")
        if (waveRId != null) {
            bodyXml.append(
                """<w:p><w:pPr><w:spacing w:before="180"/><w:jc w:val="center"/></w:pPr>${imageRunXml(waveRId, 180f, 18f, docPrCounter++)}</w:p>"""
            )
        }

        // 8. Additional pages
        for (pageIdx in 1 until project.pageCount) {
            bodyXml.append("""<w:p><w:r><w:br w:type="page"/></w:r></w:p>""")
            val els = project.elements.filter { it.pageIndex == pageIdx && it.visible }.sortedBy { it.y }
            for (el in els) {
                if (el.type == ElementType.IMAGE) {
                    val rId = addImageMedia(el, "page${pageIdx}")
                    if (rId != null) {
                        bodyXml.append("<w:p>${imageRunXml(rId, el.width, el.height, docPrCounter++)}</w:p>")
                    }
                } else if (el.type == ElementType.TEXT && el.textStyle != null) {
                    for (line in (el.content ?: "").split("\n")) {
                        bodyXml.append(
                            textParagraphXml(
                                line,
                                el.textStyle.fontFamily,
                                el.textStyle.fontSize,
                                el.textStyle.color,
                                bold = el.textStyle.fontWeight >= 600,
                                italic = el.textStyle.fontStyle == "italic",
                                afterSpacing = 60
                            )
                        )
                    }
                }
            }
        }

        val documentXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                        xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                        xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
                        xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                        xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
              <w:body>
                $bodyXml
                <w:sectPr>
                  <w:pgSz w:w="${mmToTwips(210f)}" w:h="${mmToTwips(297f)}"/>
                  <w:pgMar w:top="${mmToTwips(margins.top)}" w:right="${mmToTwips(margins.right)}" w:bottom="${mmToTwips(margins.bottom)}" w:left="${mmToTwips(margins.left)}"/>
                </w:sectPr>
              </w:body>
            </w:document>
        """.trimIndent()

        val relsXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
            for ((rId, mediaName, _) in mediaEntries) {
                append("""<Relationship Id="$rId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/$mediaName"/>""")
            }
            append("</Relationships>")
        }

        val rootRels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
            </Relationships>
        """.trimIndent()

        val contentTypes = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Default Extension="png" ContentType="image/png"/>
              <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
            </Types>
        """.trimIndent()

        val fileName = "${slugify(project.name)}.docx"
        val outFile = File(exportsDir(context), fileName)
        ZipOutputStream(FileOutputStream(outFile)).use { zos ->
            fun putText(path: String, text: String) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(text.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            putText("[Content_Types].xml", contentTypes)
            putText("_rels/.rels", rootRels)
            putText("word/document.xml", documentXml)
            putText("word/_rels/document.xml.rels", relsXml)
            for ((_, mediaName, bytes) in mediaEntries) {
                zos.putNextEntry(ZipEntry("word/media/$mediaName"))
                zos.write(bytes)
                zos.closeEntry()
            }
        }

        val mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        val savedLabel = copyToPublicDownloads(context, outFile, fileName, mimeType)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        ExportFileResult(uri, fileName, mimeType, savedLabel)
    }

    suspend fun exportProjectJson(context: Context, project: LetterheadProject): ExportFileResult = withContext(Dispatchers.IO) {
        val fileName = "${slugify(project.name)}.grashy.json"
        val outFile = File(exportsDir(context), fileName)
        outFile.writeText(json.encodeToString(project), Charsets.UTF_8)
        val savedLabel = copyToPublicDownloads(context, outFile, fileName, "application/json")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        ExportFileResult(uri, fileName, "application/json", savedLabel)
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx) ?: "imported_file"
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "imported_file"
    }

    suspend fun importFromUri(
        context: Context,
        uri: Uri,
        activePageIndex: Int,
        nextZIndex: Int
    ): ImportResult = withContext(Dispatchers.IO) {
        val name = resolveDisplayName(context, uri)
        val mime = context.contentResolver.getType(uri) ?: ""
        val ext = name.substringAfterLast('.', "").lowercase()

        // 1. Saved .grashy.json project
        if (ext == "json" || mime.contains("json")) {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Could not read JSON file.")
            val project = json.decodeFromString<LetterheadProject>(raw)
            return@withContext ImportResult.LoadedProject(project, "Loaded saved project \"${project.name}\"")
        }

        // 2. Image import
        if (ext in listOf("png", "jpg", "jpeg", "webp") || mime.startsWith("image/")) {
            val localUriStr = copyUriToInternalImageFile(context, uri)
            val bmp = loadBitmapForImageSrc(context, localUriStr)
            val ratio = if (bmp != null && bmp.height > 0) bmp.width.toFloat() / bmp.height else 1.5f
            val widthMm = 55f
            val heightMm = Geometry.roundMm(widthMm / ratio).coerceAtLeast(10f)
            val el = LetterheadElement(
                id = "imported-img-${System.currentTimeMillis()}",
                pageIndex = activePageIndex,
                type = ElementType.IMAGE,
                role = ElementRole.CUSTOM,
                name = name,
                x = 20f,
                y = 20f,
                width = widthMm,
                height = heightMm,
                zIndex = nextZIndex,
                imageSrc = localUriStr,
                objectFit = "contain"
            )
            return@withContext ImportResult.AddedElements(listOf(el), "Imported image \"$name\"")
        }

        // 3. DOCX import
        if (ext == "docx" || mime.contains("wordprocessingml")) {
            var docXmlText: String? = null
            var mediaImageBytes: ByteArray? = null
            var mediaExt = "png"

            context.contentResolver.openInputStream(uri)?.use { stream ->
                ZipInputStream(stream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == "word/document.xml") {
                            docXmlText = zis.readBytes().toString(Charsets.UTF_8)
                        } else if (entry.name.startsWith("word/media/") && mediaImageBytes == null) {
                            mediaExt = entry.name.substringAfterLast('.', "png")
                            mediaImageBytes = zis.readBytes()
                        }
                        entry = zis.nextEntry
                    }
                }
            }

            val xml = docXmlText ?: error("Invalid DOCX: missing word/document.xml")
            val extractedLines = parseDocxParagraphs(xml)
            val added = mutableListOf<LetterheadElement>()

            if (extractedLines.isNotEmpty()) {
                added.add(
                    LetterheadElement(
                        id = "docx-text-${System.currentTimeMillis()}",
                        pageIndex = activePageIndex,
                        type = ElementType.TEXT,
                        role = ElementRole.CUSTOM,
                        name = "DOCX Text ($name)",
                        x = 15f,
                        y = 80f,
                        width = 180f,
                        height = min(130f, max(24f, extractedLines.size * 7f)),
                        zIndex = nextZIndex,
                        content = extractedLines.joinToString("\n"),
                        textStyle = TextStyleConfig(
                            fontFamily = "DM Sans",
                            fontSize = 10.5f,
                            lineHeight = 1.45f,
                            paragraphSpacing = 2f,
                            color = "#1E293B",
                            paddingTop = 2f,
                            paddingRight = 2f,
                            paddingBottom = 2f,
                            paddingLeft = 2f
                        )
                    )
                )
            }

            mediaImageBytes?.let { bytes ->
                val imgFile = File(importedAssetsDir(context), "docx_img_${System.currentTimeMillis()}.$mediaExt")
                imgFile.writeBytes(bytes)
                added.add(
                    LetterheadElement(
                        id = "docx-img-${System.currentTimeMillis()}",
                        pageIndex = activePageIndex,
                        type = ElementType.IMAGE,
                        role = ElementRole.CUSTOM,
                        name = "DOCX Asset",
                        x = 15f,
                        y = 14f,
                        width = 45f,
                        height = 30f,
                        zIndex = nextZIndex + 5,
                        imageSrc = Uri.fromFile(imgFile).toString(),
                        objectFit = "contain"
                    )
                )
            }

            if (added.isEmpty()) error("No readable text or images found in DOCX.")
            return@withContext ImportResult.AddedElements(added, "Imported ${added.size} element(s) from \"$name\"")
        }

        // 4. PDF import via Android PdfRenderer
        if (ext == "pdf" || mime == "application/pdf") {
            val tmpPdf = File(exportsDir(context), "import_${System.currentTimeMillis()}.pdf")
            val outImg = File(importedAssetsDir(context), "pdf_page_${System.currentTimeMillis()}.png")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tmpPdf).use { output -> input.copyTo(output) }
                } ?: error("Could not read PDF file.")

                val bmp = ParcelFileDescriptor.open(tmpPdf, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (renderer.pageCount == 0) error("PDF has no pages.")
                        renderer.openPage(0).use { page ->
                            Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888).also { bitmap ->
                                val canvas = Canvas(bitmap)
                                canvas.drawColor(android.graphics.Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                    }
                }

                try {
                    FileOutputStream(outImg).use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
                } finally {
                    bmp.recycle()
                }
            } finally {
                tmpPdf.delete()
            }

            val el = LetterheadElement(
                id = "pdf-page-${System.currentTimeMillis()}",
                pageIndex = activePageIndex,
                type = ElementType.IMAGE,
                role = ElementRole.CUSTOM,
                name = "PDF Page ($name)",
                x = 0f,
                y = 0f,
                width = 210f,
                height = 297f,
                zIndex = 1,
                imageSrc = Uri.fromFile(outImg).toString(),
                objectFit = "contain"
            )
            return@withContext ImportResult.AddedElements(listOf(el), "Imported PDF page from \"$name\"")
        }

        error("Unsupported file type \".$ext\". Use PDF, DOCX, PNG/JPG/WEBP, or .grashy.json.")
    }

    private fun parseDocxParagraphs(xml: String): List<String> {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))
        val lines = mutableListOf<String>()
        var currentPara = StringBuilder()
        var inParagraph = false
        var inTextRun = false

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "w:p" -> {
                        inParagraph = true
                        currentPara = StringBuilder()
                    }
                    "w:t" -> if (inParagraph) inTextRun = true
                }
                XmlPullParser.TEXT -> if (inParagraph && inTextRun) {
                    currentPara.append(parser.text)
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "w:t" -> inTextRun = false
                    "w:p" -> {
                        inParagraph = false
                        val line = currentPara.toString().trim()
                        if (line.isNotEmpty()) lines.add(line)
                    }
                }
            }
            eventType = parser.next()
        }
        return lines
    }
}
