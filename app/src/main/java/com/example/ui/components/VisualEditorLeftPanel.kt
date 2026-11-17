package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.Geometry
import com.example.domain.GrashyInitialProject
import com.example.model.ElementType
import com.example.model.FontPairing
import com.example.model.LetterheadElement
import com.example.ui.theme.StudioColors
import com.example.ui.viewmodel.AddElementPreset

@Composable
fun VisualEditorLeftPanel(
    elements: List<LetterheadElement>,
    deletedElements: List<LetterheadElement>,
    activePage: Int,
    selectedId: String?,
    onSelectElement: (String) -> Unit,
    onToggleVisibility: (String) -> Unit,
    onToggleLock: (String) -> Unit,
    onReorderElement: (String, Geometry.ZOrderAction) -> Unit,
    onDeleteElement: (String) -> Unit,
    onRestoreElement: (String) -> Unit,
    onAddPreset: (AddElementPreset) -> Unit,
    onPickImage: () -> Unit,
    onApplyFontPairing: (FontPairing) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val pageLayers = remember(elements, activePage) {
        elements.filter { it.pageIndex == activePage }.sortedByDescending { it.zIndex }
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
                text = { Text("Layers (${pageLayers.size})", fontSize = 12.sp) },
                modifier = Modifier.testTag("left_tab_layers")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Add & Fonts", fontSize = 12.sp) },
                modifier = Modifier.testTag("left_tab_add")
            )
        }
        HorizontalDivider(color = StudioColors.Border)

        if (selectedTab == 0) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(pageLayers, key = { it.id }) { el ->
                    val isSelected = el.id == selectedId
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) StudioColors.BrandSoftBg else StudioColors.SurfaceMuted,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) StudioColors.BrandAccent else StudioColors.Border
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("layer_item_${el.id}")
                            .clickable { onSelectElement(el.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (el.type) {
                                    ElementType.IMAGE -> Icons.Default.Image
                                    ElementType.LINE -> Icons.Default.HorizontalRule
                                    ElementType.SHAPE -> Icons.Default.AddBox
                                    ElementType.TEXT -> Icons.Default.TextFields
                                },
                                contentDescription = null,
                                tint = if (isSelected) StudioColors.BrandAccent else StudioColors.TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = el.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = StudioColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = el.role.name.lowercase().replace('_', ' '),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 10.sp,
                                    color = StudioColors.TextMuted
                                )
                            }
                            IconButton(onClick = { onReorderElement(el.id, Geometry.ZOrderAction.FORWARD) }) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "Move Layer Up", modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { onReorderElement(el.id, Geometry.ZOrderAction.BACKWARD) }) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "Move Layer Down", modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { onToggleVisibility(el.id) }) {
                                Icon(
                                    if (el.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (el.visible) "Hide Layer" else "Show Layer",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(onClick = { onToggleLock(el.id) }) {
                                Icon(
                                    if (el.locked) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = if (el.locked) "Unlock" else "Lock",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(onClick = { onDeleteElement(el.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Layer", tint = StudioColors.ErrorText, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                if (deletedElements.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Recently Removed (Tap to Restore)",
                            style = MaterialTheme.typography.labelMedium,
                            color = StudioColors.TextMuted,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                    items(deletedElements, key = { "del-${it.id}" }) { removed ->
                        OutlinedButton(
                            onClick = { onRestoreElement(removed.id) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("restore_layer_${removed.id}")
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restore \"${removed.name}\"", fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = "ADD CANVAS ELEMENTS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = StudioColors.TextSecondary
                    )
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AddPresetCard("Heading", Icons.Default.Title, "add_heading_btn", Modifier.weight(1f)) {
                            onAddPreset(AddElementPreset.HEADING)
                        }
                        AddPresetCard("Subtitle", Icons.Default.TextFields, "add_subtitle_btn", Modifier.weight(1f)) {
                            onAddPreset(AddElementPreset.SUBTITLE)
                        }
                    }
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AddPresetCard("Text Box", Icons.Default.TextFields, "add_body_btn", Modifier.weight(1f)) {
                            onAddPreset(AddElementPreset.BODY)
                        }
                        AddPresetCard("Divider Line", Icons.Default.HorizontalRule, "add_line_btn", Modifier.weight(1f)) {
                            onAddPreset(AddElementPreset.LINE)
                        }
                    }
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AddPresetCard("Shape Box", Icons.Default.AddBox, "add_box_btn", Modifier.weight(1f)) {
                            onAddPreset(AddElementPreset.BOX)
                        }
                        AddPresetCard("Pick Image", Icons.Default.Image, "add_image_btn", Modifier.weight(1f)) {
                            onPickImage()
                        }
                    }
                }

                item {
                    HorizontalDivider(color = StudioColors.Border, modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "TYPOGRAPHY PAIRINGS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = StudioColors.TextSecondary
                    )
                }
                items(GrashyInitialProject.FONT_PAIRINGS, key = { it.id }) { pairing ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = StudioColors.SurfaceMuted,
                        border = BorderStroke(1.dp, StudioColors.Border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("font_pairing_${pairing.id}")
                            .clickable { onApplyFontPairing(pairing) }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = pairing.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = StudioColors.TextPrimary
                            )
                            Text(
                                text = pairing.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = StudioColors.TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddPresetCard(
    label: String,
    icon: ImageVector,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = StudioColors.SurfaceMuted,
        border = BorderStroke(1.dp, StudioColors.Border),
        modifier = modifier
            .defaultMinSize(minHeight = 56.dp)
            .testTag(testTag)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = label, tint = StudioColors.BrandAccent, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = StudioColors.TextPrimary)
        }
    }
}
