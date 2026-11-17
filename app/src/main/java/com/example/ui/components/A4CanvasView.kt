package com.example.ui.components

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.domain.Geometry
import com.example.domain.GrashyInitialProject
import com.example.model.ElementType
import com.example.model.LetterheadElement
import com.example.model.LetterheadProject
import com.example.model.SnapGuide
import com.example.model.TextAlignOption
import com.example.ui.theme.StudioColors
import com.example.ui.theme.parseHexColor
import com.example.ui.theme.resolveComposeFontFamily
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun A4CanvasView(
    project: LetterheadProject,
    activePage: Int,
    selectedId: String?,
    highlightedIds: List<String>,
    activeSnapGuides: List<SnapGuide>,
    zoom: Float,
    interactive: Boolean,
    onSelectElement: (String?) -> Unit,
    onDragElement: (String, Float, Float) -> Unit,
    onResizeElement: (String, Float, Float) -> Unit,
    onEndGesture: () -> Unit,
    onEditTextContent: (String, String) -> Unit,
    onToggleLock: (String) -> Unit,
    onDuplicateElement: (String) -> Unit,
    onDeleteElement: (String) -> Unit,
    onPageSelected: (Int) -> Unit,
    onAddPage: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingTextElement by remember { mutableStateOf<LetterheadElement?>(null) }
    var editingDraft by remember { mutableStateOf("") }

    val pageElements = remember(project.elements, activePage) {
        project.elements
            .filter { it.pageIndex == activePage && it.visible }
            .sortedBy { it.zIndex }
    }
    val selectedEl = remember(pageElements, selectedId) {
        pageElements.find { it.id == selectedId }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(StudioColors.CanvasTrayBg)
    ) {
        // Multi-page selector bar when in visual mode or multiple pages exist
        if (interactive || project.pageCount > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFDCE1E7))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (i in 0 until project.pageCount) {
                    val isActive = i == activePage
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isActive) StudioColors.Surface else Color(0xFFEFF2F6),
                        border = BorderStroke(
                            1.dp,
                            if (isActive) StudioColors.BrandAccent else StudioColors.Border
                        ),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 40.dp)
                            .testTag("page_tab_$i")
                            .clickable { onPageSelected(i) }
                    ) {
                        Text(
                            text = "Page ${i + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            color = if (isActive) StudioColors.BrandAccent else StudioColors.TextSecondary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
                if (interactive) {
                    OutlinedButton(
                        onClick = onAddPage,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 40.dp)
                            .testTag("canvas_add_page_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Page", fontSize = 11.sp)
                    }
                }
            }
        }

        // Scrollable A4 Tray
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val density = LocalDensity.current
            // Compute base px-per-mm so width fits nicely at zoom = 1f on tablets, or scales proportionally on phones
            val availWidthDp = (maxWidth - 28.dp).coerceAtLeast(240.dp)
            val effectiveZoom = if (interactive) zoom else (availWidthDp.value / 595f).coerceIn(0.45f, 0.95f)
            // Standard 96dpi-ish baseline: 1mm = 3.78dp at 100% zoom, clamped cleanly
            val pageWidthDp: Dp = (Geometry.A4_WIDTH_MM * 3.2f * effectiveZoom).dp
            val pageHeightDp: Dp = pageWidthDp * (Geometry.A4_HEIGHT_MM / Geometry.A4_WIDTH_MM)
            val pxPerMm = with(density) { pageWidthDp.toPx() / project.pageSettings.widthMm }

            val hScroll = rememberScrollState()
            val vScroll = rememberScrollState()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(vScroll)
                    .horizontalScroll(hScroll)
                    .pointerInput(interactive) {
                        if (interactive) {
                            detectTapGestures { onSelectElement(null) }
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                // Ensure centering when narrower than container
                Box(
                    modifier = Modifier.width(maxOf(availWidthDp, pageWidthDp)),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Top MM Ruler (optional)
                        if (interactive && project.pageSettings.showRulers) {
                            Canvas(
                                modifier = Modifier
                                    .width(pageWidthDp)
                                    .height(14.dp)
                            ) {
                                val stepPx = 10f * pxPerMm
                                var mm = 0
                                while (mm <= project.pageSettings.widthMm.toInt()) {
                                    val xPx = mm * pxPerMm
                                    drawLine(
                                        color = Color(0xFF94A3B8),
                                        start = Offset(xPx, size.height - 6.dp.toPx()),
                                        end = Offset(xPx, size.height),
                                        strokeWidth = 1f
                                    )
                                    mm += 10
                                }
                            }
                        }

                        // A4 Paper Sheet
                        Box(
                            modifier = Modifier
                                .size(pageWidthDp, pageHeightDp)
                                .shadow(10.dp, RoundedCornerShape(2.dp))
                                .background(Color.White)
                                .testTag("a4_paper_surface")
                        ) {
                            // Background Grid & Margins Canvas
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                if (interactive && project.pageSettings.showGrid) {
                                    val step = project.pageSettings.gridSizeMm * pxPerMm
                                    if (step >= 4f) {
                                        var x = step
                                        while (x < size.width) {
                                            drawLine(Color(0xFFF1F5F9), Offset(x, 0f), Offset(x, size.height), 1f)
                                            x += step
                                        }
                                        var y = step
                                        while (y < size.height) {
                                            drawLine(Color(0xFFF1F5F9), Offset(0f, y), Offset(size.width, y), 1f)
                                            y += step
                                        }
                                    }
                                }

                                if (interactive && project.pageSettings.showMargins) {
                                    val m = project.pageSettings.margins
                                    val left = m.left * pxPerMm
                                    val top = m.top * pxPerMm
                                    val right = size.width - m.right * pxPerMm
                                    val bottom = size.height - m.bottom * pxPerMm
                                    drawRect(
                                        color = Color(0xFFCBD5E1),
                                        topLeft = Offset(left, top),
                                        size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                                            width = 1.2f,
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                                        )
                                    )
                                }
                            }

                            // Render Document Elements
                            for (el in pageElements) {
                                val isSelected = interactive && el.id == selectedId
                                val isHighlighted = el.id in highlightedIds
                                CanvasElementNode(
                                    element = el,
                                    pxPerMm = pxPerMm,
                                    interactive = interactive,
                                    isSelected = isSelected,
                                    isHighlighted = isHighlighted,
                                    onTap = {
                                        if (interactive) {
                                            onSelectElement(el.id)
                                        }
                                    },
                                    onDoubleTap = {
                                        if (interactive && el.type == ElementType.TEXT && !el.locked) {
                                            editingTextElement = el
                                            editingDraft = el.content ?: ""
                                        }
                                    },
                                    onDrag = { newX, newY -> onDragElement(el.id, newX, newY) },
                                    onResize = { newW, newH -> onResizeElement(el.id, newW, newH) },
                                    onEndGesture = onEndGesture
                                )
                            }

                            // Active Snap Guides Overlay
                            if (interactive && activeSnapGuides.isNotEmpty()) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    for (guide in activeSnapGuides) {
                                        val color = if (guide.kind == "page-center") StudioColors.GuideSky else StudioColors.BrandAccent
                                        if (guide.axis == "x") {
                                            val xPx = guide.positionMm * pxPerMm
                                            drawLine(color, Offset(xPx, 0f), Offset(xPx, size.height), strokeWidth = 2f)
                                        } else {
                                            val yPx = guide.positionMm * pxPerMm
                                            drawLine(color, Offset(0f, yPx), Offset(size.width, yPx), strokeWidth = 2f)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Quick Floating Action Strip for Selected Element in Visual Mode
        if (interactive && selectedEl != null) {
            Surface(
                color = StudioColors.Surface,
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, StudioColors.Border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = selectedEl.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = StudioColors.BrandAccent
                    )
                    Text(
                        text = "${selectedEl.x.roundToInt()}×${selectedEl.y.roundToInt()}mm",
                        style = MaterialTheme.typography.bodySmall,
                        color = StudioColors.TextMuted
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    if (selectedEl.type == ElementType.TEXT && !selectedEl.locked) {
                        OutlinedButton(
                            onClick = {
                                editingTextElement = selectedEl
                                editingDraft = selectedEl.content ?: ""
                            },
                            modifier = Modifier
                                .defaultMinSize(minHeight = 40.dp)
                                .testTag("quick_edit_text_button")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit Text", fontSize = 12.sp)
                        }
                    }
                    IconButton(
                        onClick = { onToggleLock(selectedEl.id) },
                        modifier = Modifier.testTag("quick_lock_button")
                    ) {
                        Icon(
                            if (selectedEl.locked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = if (selectedEl.locked) "Unlock Element" else "Lock Element"
                        )
                    }
                    IconButton(
                        onClick = { onDuplicateElement(selectedEl.id) },
                        modifier = Modifier.testTag("quick_duplicate_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate Element")
                    }
                    IconButton(
                        onClick = { onDeleteElement(selectedEl.id) },
                        modifier = Modifier.testTag("quick_delete_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Element", tint = StudioColors.ErrorText)
                    }
                }
            }
        }
    }

    // Inline Text Editor Dialog
    editingTextElement?.let { target ->
        AlertDialog(
            onDismissRequest = { editingTextElement = null },
            title = { Text("Edit ${target.name}", style = MaterialTheme.typography.titleMedium) },
            text = {
                OutlinedTextField(
                    value = editingDraft,
                    onValueChange = { editingDraft = it },
                    minLines = 4,
                    maxLines = 10,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("inline_text_editor_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onEditTextContent(target.id, editingDraft)
                        editingTextElement = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StudioColors.BrandAccent),
                    modifier = Modifier.testTag("inline_text_save_button")
                ) {
                    Text("Apply Text")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { editingTextElement = null },
                    modifier = Modifier.testTag("inline_text_cancel_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CanvasElementNode(
    element: LetterheadElement,
    pxPerMm: Float,
    interactive: Boolean,
    isSelected: Boolean,
    isHighlighted: Boolean,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onResize: (Float, Float) -> Unit,
    onEndGesture: () -> Unit
) {
    val density = LocalDensity.current
    val xDp = with(density) { (element.x * pxPerMm).toDp() }
    val yDp = with(density) { (element.y * pxPerMm).toDp() }
    val wDp = with(density) { max(8f, element.width * pxPerMm).toDp() }
    val hDp = with(density) { max(6f, element.height * pxPerMm).toDp() }

    // Keep drag accumulator in local state synced with element coordinates when not dragging
    var dragOriginX by remember(element.id, element.x) { mutableFloatStateOf(element.x) }
    var dragOriginY by remember(element.id, element.y) { mutableFloatStateOf(element.y) }
    var accDxPx by remember(element.id) { mutableFloatStateOf(0f) }
    var accDyPx by remember(element.id) { mutableFloatStateOf(0f) }

    var resizeOriginW by remember(element.id, element.width) { mutableFloatStateOf(element.width) }
    var resizeOriginH by remember(element.id, element.height) { mutableFloatStateOf(element.height) }
    var accRwPx by remember(element.id) { mutableFloatStateOf(0f) }
    var accRhPx by remember(element.id) { mutableFloatStateOf(0f) }

    val selectionBorderModifier = when {
        isSelected -> Modifier.border(1.5.dp, StudioColors.BrandAccent)
        isHighlighted -> Modifier.border(2.dp, StudioColors.GuideSky)
        else -> Modifier
    }

    Box(
        modifier = Modifier
            .offset { IntOffset((element.x * pxPerMm).roundToInt(), (element.y * pxPerMm).roundToInt()) }
            .size(wDp, hDp)
            .rotate(element.rotation)
            .alpha(element.opacity.coerceIn(0f, 1f))
            .then(selectionBorderModifier)
            .then(
                if (interactive) {
                    Modifier
                        .pointerInput(element.id, element.locked) {
                            detectTapGestures(
                                onTap = { onTap() },
                                onDoubleTap = { onDoubleTap() }
                            )
                        }
                        .pointerInput(element.id, element.locked, pxPerMm) {
                            if (!element.locked) {
                                detectDragGestures(
                                    onDragStart = {
                                        dragOriginX = element.x
                                        dragOriginY = element.y
                                        accDxPx = 0f
                                        accDyPx = 0f
                                        onTap()
                                    },
                                    onDragEnd = { onEndGesture() },
                                    onDragCancel = { onEndGesture() },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        accDxPx += dragAmount.x
                                        accDyPx += dragAmount.y
                                        onDrag(
                                            dragOriginX + accDxPx / pxPerMm,
                                            dragOriginY + accDyPx / pxPerMm
                                        )
                                    }
                                )
                            }
                        }
                } else Modifier
            )
    ) {
        when (element.type) {
            ElementType.IMAGE -> {
                val scaleType = when (element.objectFit) {
                    "cover" -> ContentScale.Crop
                    "fill" -> ContentScale.FillBounds
                    else -> ContentScale.Fit
                }
                val src = element.imageSrc
                when {
                    src == GrashyInitialProject.ASSET_LOGO || src?.contains("grashy-logo") == true -> {
                        Image(
                            painter = painterResource(id = R.drawable.grashy_logo),
                            contentDescription = element.name,
                            contentScale = scaleType,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    src == GrashyInitialProject.ASSET_FOOTER_WAVE || src?.contains("grashy-footer-wave") == true -> {
                        Image(
                            painter = painterResource(id = R.drawable.grashy_footer_wave),
                            contentDescription = element.name,
                            contentScale = scaleType,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    !src.isNullOrBlank() -> {
                        AsyncImage(
                            model = Uri.parse(src),
                            contentDescription = element.name,
                            contentScale = scaleType,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            ElementType.LINE -> {
                val ss = element.shapeStyle
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (ss != null && !ss.stroke.equals("transparent", true)) {
                        val color = parseHexColor(ss.stroke, Color.Black)
                        val strokePx = max(1f, (ss.strokeWidth * Geometry.PT_TO_MM) * pxPerMm)
                        val effect = when (ss.lineStyle) {
                            "dashed" -> PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                            "dotted" -> PathEffect.dashPathEffect(floatArrayOf(4f, 6f), 0f)
                            else -> null
                        }
                        drawLine(
                            color = color,
                            start = Offset(0f, size.height / 2f),
                            end = Offset(size.width, size.height / 2f),
                            strokeWidth = strokePx,
                            pathEffect = effect
                        )
                    }
                }
            }
            ElementType.SHAPE, ElementType.TEXT -> {
                val ss = element.shapeStyle
                val radiusDp = with(density) { ((ss?.borderRadius ?: 0f) * pxPerMm).toDp() }
                val fillColor = parseHexColor(ss?.fill, Color.Transparent)
                val strokeColor = parseHexColor(ss?.stroke, Color.Transparent)
                val strokeDp = with(density) { (((ss?.strokeWidth ?: 0f) * Geometry.PT_TO_MM) * pxPerMm).toDp() }

                val shapeBgMod = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(radiusDp))
                    .background(fillColor)
                    .then(
                        if (strokeColor != Color.Transparent && strokeDp > 0.dp) {
                            Modifier.border(strokeDp.coerceAtLeast(0.5.dp), strokeColor, RoundedCornerShape(radiusDp))
                        } else Modifier
                    )

                Box(modifier = shapeBgMod) {
                    if (element.type == ElementType.TEXT && element.textStyle != null && !element.content.isNullOrEmpty()) {
                        val ts = element.textStyle
                        // Scale pt to canvas sp accurately: 1pt = 0.3527mm
                        val fontSp = (ts.fontSize * Geometry.PT_TO_MM * pxPerMm / density.fontScale / density.density).coerceAtLeast(3f).sp
                        val align = when (ts.textAlign) {
                            TextAlignOption.CENTER -> TextAlign.Center
                            TextAlignOption.RIGHT -> TextAlign.End
                            TextAlignOption.JUSTIFY -> TextAlign.Justify
                            TextAlignOption.LEFT -> TextAlign.Start
                        }
                        val weight = when {
                            ts.fontWeight >= 700 -> FontWeight.Bold
                            ts.fontWeight >= 600 -> FontWeight.SemiBold
                            ts.fontWeight >= 500 -> FontWeight.Medium
                            else -> FontWeight.Normal
                        }
                        val padL = with(density) { (ts.paddingLeft * pxPerMm).toDp() }
                        val padR = with(density) { (ts.paddingRight * pxPerMm).toDp() }
                        val padT = with(density) { (ts.paddingTop * pxPerMm).toDp() }
                        val padB = with(density) { (ts.paddingBottom * pxPerMm).toDp() }

                        Text(
                            text = element.content,
                            style = TextStyle(
                                fontFamily = resolveComposeFontFamily(ts.fontFamily),
                                fontSize = fontSp,
                                fontWeight = weight,
                                fontStyle = if (ts.fontStyle == "italic") FontStyle.Italic else FontStyle.Normal,
                                lineHeight = fontSp * ts.lineHeight.coerceAtLeast(1.0f),
                                letterSpacing = (ts.letterSpacing * 10f).sp,
                                color = parseHexColor(ts.color, StudioColors.TextPrimary),
                                textAlign = align
                            ),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = padL, top = padT, end = padR, bottom = padB)
                        )
                    }
                }
            }
        }

        // Bottom-Right Resize Handle when selected and unlocked
        if (isSelected && !element.locked) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp)
                    .background(StudioColors.BrandAccent, CircleShape)
                    .border(2.dp, Color.White, CircleShape)
                    .pointerInput(element.id, pxPerMm) {
                        detectDragGestures(
                            onDragStart = {
                                resizeOriginW = element.width
                                resizeOriginH = element.height
                                accRwPx = 0f
                                accRhPx = 0f
                            },
                            onDragEnd = { onEndGesture() },
                            onDragCancel = { onEndGesture() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                accRwPx += dragAmount.x
                                accRhPx += dragAmount.y
                                val nextW = max(8f, Geometry.roundMm(resizeOriginW + accRwPx / pxPerMm))
                                val nextH = max(4f, Geometry.roundMm(resizeOriginH + accRhPx / pxPerMm))
                                onResize(nextW, nextH)
                            }
                        )
                    }
            )
        }
    }
}
