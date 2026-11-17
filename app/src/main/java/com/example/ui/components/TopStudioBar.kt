package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.EditorMode
import com.example.model.SyncConnectionState
import com.example.model.SyncStatusSnapshot
import com.example.ui.theme.StudioColors
import kotlin.math.roundToInt

@Composable
fun TopStudioBar(
    projectName: String,
    editorMode: EditorMode,
    canUndo: Boolean,
    canRedo: Boolean,
    zoom: Float,
    syncStatus: SyncStatusSnapshot,
    isExporting: Boolean,
    isCompactLayout: Boolean,
    onModeChange: (EditorMode) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onZoomChange: (Float) -> Unit,
    onOpenSyncModal: () -> Unit,
    onImportFile: () -> Unit,
    onSaveSnapshot: () -> Unit,
    onExportPdf: () -> Unit,
    onExportDocx: () -> Unit,
    onExportJson: () -> Unit,
    onOpenLayersSheet: () -> Unit,
    onOpenInspectorSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    var overflowExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = StudioColors.Surface,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            // Primary Studio Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Brand Mark + Title
                Image(
                    painter = painterResource(id = R.drawable.grashy_logo),
                    contentDescription = "Grashy Studio Logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(StudioColors.BrandSoftBg)
                        .border(1.dp, StudioColors.BrandSoftBorder, RoundedCornerShape(8.dp))
                        .padding(3.dp)
                )

                if (!isCompactLayout) {
                    Column(modifier = Modifier.width(175.dp)) {
                        Text(
                            text = "Grashy Studio",
                            style = MaterialTheme.typography.titleSmall,
                            color = StudioColors.TextPrimary,
                            maxLines = 1
                        )
                        Text(
                            text = projectName,
                            style = MaterialTheme.typography.bodySmall,
                            color = StudioColors.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Segmented Mode Switcher: Form / Visual
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = StudioColors.StudioBg,
                    border = BorderStroke(1.dp, StudioColors.Border)
                ) {
                    Row(modifier = Modifier.padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
                        ModeChip(
                            label = if (isCompactLayout) "Form" else "Form Generator",
                            icon = Icons.Default.Description,
                            selected = editorMode == EditorMode.FORM,
                            testTag = "mode_form_button",
                            onClick = { onModeChange(EditorMode.FORM) }
                        )
                        ModeChip(
                            label = if (isCompactLayout) "Visual" else "Visual Editor",
                            icon = Icons.Default.Edit,
                            selected = editorMode == EditorMode.VISUAL,
                            testTag = "mode_visual_button",
                            onClick = { onModeChange(EditorMode.VISUAL) }
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Sync Pill
                SyncStatusPill(
                    syncStatus = syncStatus,
                    compact = isCompactLayout,
                    onClick = onOpenSyncModal
                )

                if (!isCompactLayout) {
                    IconButton(
                        onClick = onUndo,
                        enabled = canUndo,
                        modifier = Modifier.testTag("undo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo"
                        )
                    }
                    IconButton(
                        onClick = onRedo,
                        enabled = canRedo,
                        modifier = Modifier.testTag("redo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo"
                        )
                    }

                    Button(
                        onClick = onExportPdf,
                        enabled = !isExporting,
                        colors = ButtonDefaults.buttonColors(containerColor = StudioColors.BrandAccent),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("export_pdf_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isExporting) "Exporting..." else "Export PDF", fontSize = 12.sp)
                    }
                }

                Box {
                    IconButton(
                        onClick = { overflowExpanded = true },
                        modifier = Modifier.testTag("toolbar_overflow_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Document Actions Menu"
                        )
                    }
                    DropdownMenu(
                        expanded = overflowExpanded,
                        onDismissRequest = { overflowExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Export Print-Ready PDF") },
                            leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = StudioColors.BrandAccent) },
                            onClick = { overflowExpanded = false; onExportPdf() }
                        )
                        DropdownMenuItem(
                            text = { Text("Export Word (.docx)") },
                            leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                            onClick = { overflowExpanded = false; onExportDocx() }
                        )
                        DropdownMenuItem(
                            text = { Text("Save Snapshot to Workspace") },
                            leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                            onClick = { overflowExpanded = false; onSaveSnapshot() }
                        )
                        DropdownMenuItem(
                            text = { Text("Export .grashy.json Backup") },
                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                            onClick = { overflowExpanded = false; onExportJson() }
                        )
                        DropdownMenuItem(
                            text = { Text("Import PDF / DOCX / Image / JSON") },
                            leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                            onClick = { overflowExpanded = false; onImportFile() }
                        )
                    }
                }
            }

            // Secondary Action Strip when in Visual Mode or Compact screen
            if (isCompactLayout || editorMode == EditorMode.VISUAL) {
                HorizontalDivider(color = StudioColors.Border)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StudioColors.SurfaceMuted)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isCompactLayout) {
                        IconButton(
                            onClick = onUndo,
                            enabled = canUndo,
                            modifier = Modifier.testTag("undo_button_compact")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", modifier = Modifier.size(19.dp))
                        }
                        IconButton(
                            onClick = onRedo,
                            enabled = canRedo,
                            modifier = Modifier.testTag("redo_button_compact")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", modifier = Modifier.size(19.dp))
                        }
                    }

                    if (editorMode == EditorMode.VISUAL) {
                        IconButton(
                            onClick = { onZoomChange(zoom - 0.1f) },
                            modifier = Modifier.testTag("zoom_out_button")
                        ) {
                            Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", modifier = Modifier.size(19.dp))
                        }
                        Text(
                            text = "${(zoom * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = StudioColors.TextSecondary,
                            modifier = Modifier
                                .clickable { onZoomChange(0.68f) }
                                .padding(horizontal = 4.dp, vertical = 10.dp)
                        )
                        IconButton(
                            onClick = { onZoomChange(zoom + 0.1f) },
                            modifier = Modifier.testTag("zoom_in_button")
                        ) {
                            Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", modifier = Modifier.size(19.dp))
                        }

                        if (isCompactLayout) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = StudioColors.Surface,
                                border = BorderStroke(1.dp, StudioColors.Border),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("open_layers_sheet_button")
                                    .clickable { onOpenLayersSheet() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp), tint = StudioColors.TextPrimary)
                                    Text("Layers & Add", style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = StudioColors.BrandSoftBg,
                                border = BorderStroke(1.dp, StudioColors.BrandSoftBorder),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("open_inspector_sheet_button")
                                    .clickable { onOpenInspectorSheet() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp), tint = StudioColors.BrandAccent)
                                    Text("Inspect & Polish", style = MaterialTheme.typography.labelMedium, color = StudioColors.BrandAccent)
                                }
                            }
                        }
                    }

                    if (isCompactLayout && editorMode == EditorMode.FORM) {
                        Spacer(modifier = Modifier.weight(1f))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = StudioColors.BrandAccent,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("compact_export_pdf_pill")
                                .clickable(enabled = !isExporting) { onExportPdf() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color.White)
                                Text(if (isExporting) "..." else "PDF", style = MaterialTheme.typography.labelMedium, color = Color.White)
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = StudioColors.Border)
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) StudioColors.Surface else Color.Transparent,
        shadowElevation = if (selected) 1.dp else 0.dp,
        modifier = Modifier
            .defaultMinSize(minHeight = 40.dp)
            .testTag(testTag)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = if (selected) StudioColors.BrandAccent else StudioColors.TextSecondary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) StudioColors.TextPrimary else StudioColors.TextSecondary
            )
        }
    }
}

@Composable
private fun SyncStatusPill(
    syncStatus: SyncStatusSnapshot,
    compact: Boolean,
    onClick: () -> Unit
) {
    val (bg, border, fg, icon) = when (syncStatus.state) {
        SyncConnectionState.SYNCED -> Quad(StudioColors.SuccessBg, StudioColors.SuccessBorder, StudioColors.SuccessText, Icons.Default.CloudDone)
        SyncConnectionState.OFFLINE -> Quad(Color(0xFFF1F5F9), Color(0xFFCBD5E1), Color(0xFF475569), Icons.Default.CloudOff)
        SyncConnectionState.SYNCING -> Quad(Color(0xFFEFF6FF), Color(0xFFBFDBFE), Color(0xFF1D4ED8), Icons.Default.CloudSync)
        SyncConnectionState.PENDING -> Quad(StudioColors.WarningBg, Color(0xFFFDE68A), StudioColors.WarningText, Icons.Default.CloudQueue)
        SyncConnectionState.ERROR -> Quad(StudioColors.ErrorBg, Color(0xFFFECACA), StudioColors.ErrorText, Icons.Default.ErrorOutline)
    }

    Surface(
        shape = CircleShape,
        color = bg,
        border = BorderStroke(1.dp, border),
        modifier = Modifier
            .defaultMinSize(minHeight = 40.dp)
            .testTag("sync_status_button")
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, contentDescription = syncStatus.label, tint = fg, modifier = Modifier.size(15.dp))
            Text(
                text = if (compact && syncStatus.shortLabel.length > 12) syncStatus.shortLabel.take(10) else syncStatus.shortLabel,
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
