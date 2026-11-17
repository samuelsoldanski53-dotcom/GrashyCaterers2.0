package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlignHorizontalCenter
import androidx.compose.material.icons.filled.AlignHorizontalLeft
import androidx.compose.material.icons.filled.AlignHorizontalRight
import androidx.compose.material.icons.filled.AlignVerticalBottom
import androidx.compose.material.icons.filled.AlignVerticalCenter
import androidx.compose.material.icons.filled.AlignVerticalTop
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.RestorePage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.DesignAnalyzer
import com.example.domain.Geometry
import com.example.model.AuditSeverity
import com.example.model.DesignSuggestion
import com.example.model.ElementType
import com.example.model.LetterheadElement
import com.example.model.PageMargins
import com.example.model.PageSettings
import com.example.model.ShapeStyleConfig
import com.example.model.TextAlignOption
import com.example.ui.theme.StudioColors
import com.example.ui.theme.parseHexColor
import kotlin.math.roundToInt

private val BRAND_COLOR_SWATCHES = listOf(
    "#C45911", "#9A3412", "#0F172A", "#1E293B",
    "#334155", "#64748B", "#FFF7ED", "#F8FAFC",
    "#FDBA74", "#CBD5E1", "#FFFFFF", "transparent"
)

private val AVAILABLE_FONTS = listOf(
    "Plus Jakarta Sans",
    "DM Sans",
    "Playfair Display",
    "Lora",
    "Outfit",
    "Montserrat",
    "Source Serif 4",
    "JetBrains Mono"
)

@Composable
fun VisualEditorRightPanel(
    selectedElement: LetterheadElement?,
    suggestions: List<DesignSuggestion>,
    pageSettings: PageSettings,
    pageCount: Int,
    onUpdateElement: (id: String, commit: Boolean, transform: (LetterheadElement) -> LetterheadElement) -> Unit,
    onAlignSelected: (Geometry.AlignmentTarget) -> Unit,
    onReorderSelected: (Geometry.ZOrderAction) -> Unit,
    onReplaceImage: (String) -> Unit,
    onLocateElements: (List<String>) -> Unit,
    onApplySuggestionFix: (DesignSuggestion) -> Unit,
    onApplyFullRedesign: () -> Unit,
    onResetOriginalLayout: () -> Unit,
    onUpdatePageSettings: ((PageSettings) -> PageSettings) -> Unit,
    onUpdateMargins: ((PageMargins) -> PageMargins) -> Unit,
    onAddPage: () -> Unit,
    onDeleteActivePage: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember(selectedElement?.id) {
        mutableIntStateOf(if (selectedElement != null) 1 else 0)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(StudioColors.Surface)
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = StudioColors.Surface,
            contentColor = StudioColors.BrandAccent
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Assistant (${suggestions.size})", fontSize = 11.sp) },
                modifier = Modifier.testTag("right_tab_assistant")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Format", fontSize = 11.sp) },
                modifier = Modifier.testTag("right_tab_format")
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Page", fontSize = 11.sp) },
                modifier = Modifier.testTag("right_tab_page")
            )
        }
        HorizontalDivider(color = StudioColors.Border)

        when (selectedTab) {
            0 -> AssistantTab(
                suggestions = suggestions,
                onLocateElements = onLocateElements,
                onApplySuggestionFix = onApplySuggestionFix,
                onApplyFullRedesign = onApplyFullRedesign,
                onResetOriginalLayout = onResetOriginalLayout
            )
            1 -> FormatTab(
                el = selectedElement,
                onUpdateElement = onUpdateElement,
                onAlignSelected = onAlignSelected,
                onReorderSelected = onReorderSelected,
                onReplaceImage = onReplaceImage
            )
            2 -> PageTab(
                pageSettings = pageSettings,
                pageCount = pageCount,
                onUpdatePageSettings = onUpdatePageSettings,
                onUpdateMargins = onUpdateMargins,
                onAddPage = onAddPage,
                onDeleteActivePage = onDeleteActivePage
            )
        }
    }
}

@Composable
private fun AssistantTab(
    suggestions: List<DesignSuggestion>,
    onLocateElements: (List<String>) -> Unit,
    onApplySuggestionFix: (DesignSuggestion) -> Unit,
    onApplyFullRedesign: () -> Unit,
    onResetOriginalLayout: () -> Unit
) {
    val summary = remember(suggestions) { DesignAnalyzer.computeLayoutHealthSummary(suggestions) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (summary.highCount > 0) StudioColors.WarningBg else StudioColors.SuccessBg,
                border = BorderStroke(
                    1.dp,
                    if (summary.highCount > 0) Color(0xFFFDE68A) else StudioColors.SuccessBorder
                )
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            if (suggestions.isEmpty()) Icons.Default.CheckCircle else Icons.Default.AutoFixHigh,
                            contentDescription = null,
                            tint = if (summary.highCount > 0) StudioColors.WarningText else StudioColors.SuccessText,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = summary.statusText,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (summary.highCount > 0) StudioColors.WarningText else StudioColors.SuccessText
                        )
                    }
                    Button(
                        onClick = onApplyFullRedesign,
                        colors = ButtonDefaults.buttonColors(containerColor = StudioColors.BrandAccent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("apply_full_redesign_button")
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Apply Professional Redesign", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onResetOriginalLayout,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("revert_original_layout_button")
                    ) {
                        Icon(Icons.Default.RestorePage, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Revert to Original PDF Layout", fontSize = 12.sp)
                    }
                }
            }
        }

        items(suggestions, key = { it.id }) { item ->
            val badgeBg = when (item.severity) {
                AuditSeverity.HIGH -> StudioColors.ErrorBg
                AuditSeverity.MEDIUM -> StudioColors.WarningBg
                AuditSeverity.LOW -> Color(0xFFF1F5F9)
            }
            val badgeFg = when (item.severity) {
                AuditSeverity.HIGH -> StudioColors.ErrorText
                AuditSeverity.MEDIUM -> StudioColors.WarningText
                AuditSeverity.LOW -> StudioColors.TextSecondary
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = StudioColors.SurfaceMuted,
                border = BorderStroke(1.dp, StudioColors.Border)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(shape = RoundedCornerShape(4.dp), color = badgeBg) {
                            Text(
                                text = item.severity.name,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeFg,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = item.category.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = StudioColors.TextMuted
                        )
                    }
                    Text(item.title, style = MaterialTheme.typography.titleSmall, color = StudioColors.TextPrimary)
                    Text(item.explanation, style = MaterialTheme.typography.bodySmall, color = StudioColors.TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onLocateElements(item.affectedElementIds) },
                            modifier = Modifier
                                .defaultMinSize(minHeight = 44.dp)
                                .testTag("locate_${item.id}")
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Locate", fontSize = 11.sp)
                        }
                        if (item.canAutoFix) {
                            Button(
                                onClick = { onApplySuggestionFix(item) },
                                colors = ButtonDefaults.buttonColors(containerColor = StudioColors.BrandAccent),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 44.dp)
                                    .testTag("fix_${item.id}")
                            ) {
                                Text(item.fixLabel, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatTab(
    el: LetterheadElement?,
    onUpdateElement: (id: String, commit: Boolean, transform: (LetterheadElement) -> LetterheadElement) -> Unit,
    onAlignSelected: (Geometry.AlignmentTarget) -> Unit,
    onReorderSelected: (Geometry.ZOrderAction) -> Unit,
    onReplaceImage: (String) -> Unit
) {
    if (el == null) {
        Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "Tap any element on the A4 sheet (or in Layers) to edit coordinates, alignment, typography, and colors.",
                style = MaterialTheme.typography.bodyMedium,
                color = StudioColors.TextMuted
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = el.name,
            onValueChange = { next -> onUpdateElement(el.id, true) { it.copy(name = next) } },
            label = { Text("Layer Name", fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("inspector_layer_name")
        )

        Text(
            text = DesignAnalyzer.getElementBoundsSummary(el),
            style = MaterialTheme.typography.bodySmall,
            color = StudioColors.TextMuted
        )

        // Position & Size Sliders
        MmStepperRow("X (mm)", el.x, 0f..200f) { nx ->
            onUpdateElement(el.id, true) { it.copy(x = Geometry.roundMm(nx)) }
        }
        MmStepperRow("Y (mm)", el.y, 0f..290f) { ny ->
            onUpdateElement(el.id, true) { it.copy(y = Geometry.roundMm(ny)) }
        }
        MmStepperRow("Width (mm)", el.width, 6f..210f) { nw ->
            onUpdateElement(el.id, true) { it.copy(width = Geometry.roundMm(nw)) }
        }
        MmStepperRow("Height (mm)", el.height, 2f..297f) { nh ->
            onUpdateElement(el.id, true) { it.copy(height = Geometry.roundMm(nh)) }
        }

        Text("Opacity: ${(el.opacity * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = el.opacity,
            onValueChange = { v -> onUpdateElement(el.id, false) { it.copy(opacity = v) } },
            onValueChangeFinished = { onUpdateElement(el.id, true) { it } },
            valueRange = 0.1f..1f,
            colors = SliderDefaults.colors(thumbColor = StudioColors.BrandAccent, activeTrackColor = StudioColors.BrandAccent)
        )

        // Page Alignment & Z-Order
        Text("ALIGN TO MARGINS / PAGE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { onAlignSelected(Geometry.AlignmentTarget.LEFT) }) {
                Icon(Icons.Default.AlignHorizontalLeft, contentDescription = "Align Left")
            }
            IconButton(onClick = { onAlignSelected(Geometry.AlignmentTarget.CENTER_X) }) {
                Icon(Icons.Default.AlignHorizontalCenter, contentDescription = "Center Horizontally")
            }
            IconButton(onClick = { onAlignSelected(Geometry.AlignmentTarget.RIGHT) }) {
                Icon(Icons.Default.AlignHorizontalRight, contentDescription = "Align Right")
            }
            IconButton(onClick = { onAlignSelected(Geometry.AlignmentTarget.TOP) }) {
                Icon(Icons.Default.AlignVerticalTop, contentDescription = "Align Top")
            }
            IconButton(onClick = { onAlignSelected(Geometry.AlignmentTarget.MIDDLE_Y) }) {
                Icon(Icons.Default.AlignVerticalCenter, contentDescription = "Center Vertically")
            }
            IconButton(onClick = { onAlignSelected(Geometry.AlignmentTarget.BOTTOM) }) {
                Icon(Icons.Default.AlignVerticalBottom, contentDescription = "Align Bottom")
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onReorderSelected(Geometry.ZOrderAction.FRONT) },
                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp)
            ) {
                Icon(Icons.Default.FlipToFront, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("To Front", fontSize = 11.sp)
            }
            OutlinedButton(
                onClick = { onReorderSelected(Geometry.ZOrderAction.BACK) },
                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp)
            ) {
                Icon(Icons.Default.FlipToBack, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("To Back", fontSize = 11.sp)
            }
        }

        if (el.type == ElementType.IMAGE) {
            Button(
                onClick = { onReplaceImage(el.id) },
                colors = ButtonDefaults.buttonColors(containerColor = StudioColors.BrandAccent),
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("replace_image_button")
            ) {
                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Replace Image Asset")
            }
        }

        // Typography Controls
        el.textStyle?.let { ts ->
            HorizontalDivider(color = StudioColors.Border)
            Text("TYPOGRAPHY", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (font in AVAILABLE_FONTS) {
                    val active = ts.fontFamily.equals(font, ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (active) StudioColors.BrandSoftBg else StudioColors.SurfaceMuted,
                        border = BorderStroke(1.dp, if (active) StudioColors.BrandAccent else StudioColors.Border),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 40.dp)
                            .clickable {
                                onUpdateElement(el.id, true) { item ->
                                    item.copy(textStyle = item.textStyle?.copy(fontFamily = font))
                                }
                            }
                    ) {
                        Text(
                            text = font,
                            fontSize = 11.sp,
                            color = if (active) StudioColors.BrandAccent else StudioColors.TextPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            MmStepperRow("Size (pt)", ts.fontSize, 6f..36f) { pt ->
                onUpdateElement(el.id, true) { item -> item.copy(textStyle = item.textStyle?.copy(fontSize = pt)) }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextAlignOption.entries.forEach { alignOpt ->
                    val active = ts.textAlign == alignOpt
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (active) StudioColors.BrandAccent else StudioColors.SurfaceMuted,
                        border = BorderStroke(1.dp, StudioColors.Border),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 40.dp)
                            .clickable {
                                onUpdateElement(el.id, true) { item ->
                                    item.copy(textStyle = item.textStyle?.copy(textAlign = alignOpt))
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(
                                text = alignOpt.name.take(1),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (active) Color.White else StudioColors.TextPrimary
                            )
                        }
                    }
                }
            }

            Text("Text Color", style = MaterialTheme.typography.labelMedium)
            ColorSwatchRow(selectedHex = ts.color) { hex ->
                onUpdateElement(el.id, true) { item -> item.copy(textStyle = item.textStyle?.copy(color = hex)) }
            }
        }

        // Fill & Stroke
        if (el.type == ElementType.SHAPE || el.type == ElementType.LINE || el.shapeStyle != null) {
            HorizontalDivider(color = StudioColors.Border)
            val ss = el.shapeStyle ?: ShapeStyleConfig()
            if (el.type != ElementType.LINE) {
                Text("Fill Color", style = MaterialTheme.typography.labelMedium)
                ColorSwatchRow(selectedHex = ss.fill) { hex ->
                    onUpdateElement(el.id, true) { item -> item.copy(shapeStyle = (item.shapeStyle ?: ss).copy(fill = hex)) }
                }
            }
            Text("Stroke / Rule Color", style = MaterialTheme.typography.labelMedium)
            ColorSwatchRow(selectedHex = ss.stroke) { hex ->
                onUpdateElement(el.id, true) { item -> item.copy(shapeStyle = (item.shapeStyle ?: ss).copy(stroke = hex)) }
            }
        }
    }
}

@Composable
private fun PageTab(
    pageSettings: PageSettings,
    pageCount: Int,
    onUpdatePageSettings: ((PageSettings) -> PageSettings) -> Unit,
    onUpdateMargins: ((PageMargins) -> PageMargins) -> Unit,
    onAddPage: () -> Unit,
    onDeleteActivePage: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("A4 MARGINS (MM)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        MmStepperRow("Top Margin", pageSettings.margins.top, 5f..40f) { v -> onUpdateMargins { it.copy(top = v) } }
        MmStepperRow("Bottom Margin", pageSettings.margins.bottom, 5f..45f) { v -> onUpdateMargins { it.copy(bottom = v) } }
        MmStepperRow("Left Margin", pageSettings.margins.left, 5f..35f) { v -> onUpdateMargins { it.copy(left = v) } }
        MmStepperRow("Right Margin", pageSettings.margins.right, 5f..35f) { v -> onUpdateMargins { it.copy(right = v) } }

        HorizontalDivider(color = StudioColors.Border)
        Text("CANVAS GUIDES & SNAP", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

        ToggleRow("Show Printable Margins", pageSettings.showMargins) { b -> onUpdatePageSettings { it.copy(showMargins = b) } }
        ToggleRow("Show Millimeter Grid", pageSettings.showGrid) { b -> onUpdatePageSettings { it.copy(showGrid = b) } }
        ToggleRow("Show Top Ruler", pageSettings.showRulers) { b -> onUpdatePageSettings { it.copy(showRulers = b) } }
        ToggleRow("Snap to Smart Guides", pageSettings.snapToGuides) { b -> onUpdatePageSettings { it.copy(snapToGuides = b) } }
        ToggleRow("Snap to 5mm Grid", pageSettings.snapToGrid) { b -> onUpdatePageSettings { it.copy(snapToGrid = b) } }

        HorizontalDivider(color = StudioColors.Border)
        Text("PAGES ($pageCount)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Button(
            onClick = onAddPage,
            colors = ButtonDefaults.buttonColors(containerColor = StudioColors.BrandAccent),
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("page_tab_add_page")
        ) {
            Text("Add Blank A4 Page")
        }
        if (pageCount > 1) {
            OutlinedButton(
                onClick = onDeleteActivePage,
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("page_tab_delete_page")
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = StudioColors.ErrorText, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Delete Active Page", color = StudioColors.ErrorText)
            }
        }
    }
}

@Composable
private fun MmStepperRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = StudioColors.TextSecondary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
                onClick = { onChange((value - 1f).coerceIn(range.start, range.endInclusive)) },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(40.dp)
            ) { Text("-") }
            Text(
                text = String.format("%.1f", value),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(40.dp)
            )
            OutlinedButton(
                onClick = { onChange((value + 1f).coerceIn(range.start, range.endInclusive)) },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(40.dp)
            ) { Text("+") }
        }
    }
}

@Composable
private fun ColorSwatchRow(selectedHex: String, onPick: (String) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (hex in BRAND_COLOR_SWATCHES) {
            val selected = selectedHex.equals(hex, ignoreCase = true)
            val color = if (hex == "transparent") Color.White else parseHexColor(hex, Color.Black)
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (selected) 2.5.dp else 1.dp,
                        color = if (selected) StudioColors.BrandAccent else StudioColors.Border,
                        shape = CircleShape
                    )
                    .clickable { onPick(hex) },
                contentAlignment = Alignment.Center
            ) {
                if (hex == "transparent") {
                    Text("×", fontSize = 13.sp, color = StudioColors.ErrorText, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = StudioColors.TextPrimary)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
