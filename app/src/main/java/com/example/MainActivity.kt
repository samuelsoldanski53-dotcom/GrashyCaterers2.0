package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.export.DocumentExporter
import com.example.model.EditorMode
import com.example.ui.components.A4CanvasView
import com.example.ui.components.FormGeneratorPanel
import com.example.ui.components.TopStudioBar
import com.example.ui.components.VisualEditorLeftPanel
import com.example.ui.components.VisualEditorRightPanel
import com.example.ui.components.WorkspaceSyncModal
import com.example.ui.theme.GrashyStudioTheme
import com.example.ui.theme.StudioColors
import com.example.ui.viewmodel.ShareEvent
import com.example.ui.viewmodel.StudioViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GrashyStudioTheme {
                GrashyStudioApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrashyStudioApp(viewModel: StudioViewModel = viewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val clients by viewModel.clients.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val versions by viewModel.activeDocVersions.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showLayersSheet by remember { mutableStateOf(false) }
    var showInspectorSheet by remember { mutableStateOf(false) }
    var replacingImageElementId by remember { mutableStateOf<String?>(null) }

    // Android Zero-Permission Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching { DocumentExporter.copyUriToInternalImageFile(context, uri) }
                .onSuccess { localUriStr ->
                    val replaceTarget = replacingImageElementId
                    if (replaceTarget != null) {
                        viewModel.replaceElementImage(replaceTarget, localUriStr)
                    } else {
                        viewModel.addImportedImage(localUriStr, "Photo Asset")
                    }
                    replacingImageElementId = null
                    showLayersSheet = false
                }
                .onFailure { err ->
                    viewModel.showNotice(err.message ?: "Failed to load selected photo.")
                }
        }
    }

    // SAF Document Importer (PDF, DOCX, Image, .grashy.json)
    val documentImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importFromUri(uri)
        }
    }

    // Share / Open exported PDF, DOCX, or JSON
    LaunchedEffect(Unit) {
        viewModel.shareEvents.collect { event ->
            when (event) {
                is ShareEvent.ExportReady -> {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = event.result.mimeType
                        putExtra(Intent.EXTRA_STREAM, event.result.uri)
                        putExtra(Intent.EXTRA_SUBJECT, event.result.fileName)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share ${event.result.fileName}"))
                }
            }
        }
    }

    LaunchedEffect(uiState.noticeMessage) {
        val msg = uiState.noticeMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearNotice()
        }
    }

    // BackHandler for secondary overlay views / preview / sheets
    BackHandler(enabled = uiState.syncModalOpen || showLayersSheet || showInspectorSheet || uiState.showPreviewOnCompactForm || uiState.editorMode == EditorMode.VISUAL) {
        when {
            uiState.syncModalOpen -> viewModel.setSyncModalOpen(false)
            showLayersSheet -> showLayersSheet = false
            showInspectorSheet -> showInspectorSheet = false
            uiState.showPreviewOnCompactForm -> viewModel.setCompactFormPreview(false)
            uiState.editorMode == EditorMode.VISUAL -> viewModel.setEditorMode(EditorMode.FORM)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 900.dp
        val isMedium = maxWidth >= 600.dp && !isExpanded
        val isCompact = maxWidth < 600.dp

        Scaffold(
            containerColor = StudioColors.StudioBg,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopStudioBar(
                    projectName = uiState.project.name,
                    editorMode = uiState.editorMode,
                    canUndo = uiState.history.canUndo,
                    canRedo = uiState.history.canRedo,
                    zoom = uiState.zoom,
                    syncStatus = syncStatus,
                    isExporting = uiState.isExporting,
                    isCompactLayout = isCompact,
                    onModeChange = viewModel::setEditorMode,
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                    onZoomChange = viewModel::setZoom,
                    onOpenSyncModal = { viewModel.setSyncModalOpen(true) },
                    onImportFile = {
                        documentImportLauncher.launch(
                            arrayOf(
                                "application/pdf",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "application/json",
                                "image/*"
                            )
                        )
                    },
                    onSaveSnapshot = viewModel::saveManualProjectSnapshot,
                    onExportPdf = viewModel::exportPdf,
                    onExportDocx = viewModel::exportDocx,
                    onExportJson = viewModel::exportProjectJson,
                    onOpenLayersSheet = { showLayersSheet = true },
                    onOpenInspectorSheet = { showInspectorSheet = true }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (uiState.editorMode) {
                    EditorMode.FORM -> {
                        if (!isCompact) {
                            // Tablet / Foldable / Landscape Split: Form Left + Live A4 Preview Right
                            Row(modifier = Modifier.fillMaxSize()) {
                                FormGeneratorPanel(
                                    projectName = uiState.project.name,
                                    formData = uiState.project.formData,
                                    isCompactLayout = false,
                                    onProjectNameChange = viewModel::updateProjectName,
                                    onUpdateField = viewModel::updateFormField,
                                    onApplyRawQuote = viewModel::applyRawQuote,
                                    onCleanMenuBullets = viewModel::cleanMenuBullets,
                                    onGenerateDocument = viewModel::generateDocument,
                                    onStartNewClient = viewModel::startNewClient,
                                    onSwitchToVisual = { viewModel.setEditorMode(EditorMode.VISUAL) },
                                    onShowPreviewCompact = {},
                                    modifier = Modifier.width(if (isExpanded) 440.dp else 340.dp)
                                )
                                VerticalDivider(color = StudioColors.Border)
                                A4CanvasView(
                                    project = uiState.project,
                                    activePage = uiState.activePage,
                                    selectedId = null,
                                    highlightedIds = emptyList(),
                                    activeSnapGuides = emptyList(),
                                    zoom = uiState.zoom,
                                    interactive = false,
                                    onSelectElement = {},
                                    onDragElement = { _, _, _ -> },
                                    onResizeElement = { _, _, _ -> },
                                    onEndGesture = {},
                                    onEditTextContent = { _, _ -> },
                                    onToggleLock = {},
                                    onDuplicateElement = {},
                                    onDeleteElement = {},
                                    onPageSelected = viewModel::setActivePage,
                                    onAddPage = viewModel::addPage,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            // Phone Portrait: Fullscreen Form or Fullscreen Live Preview
                            if (uiState.showPreviewOnCompactForm) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    A4CanvasView(
                                        project = uiState.project,
                                        activePage = uiState.activePage,
                                        selectedId = null,
                                        highlightedIds = emptyList(),
                                        activeSnapGuides = emptyList(),
                                        zoom = uiState.zoom,
                                        interactive = false,
                                        onSelectElement = {},
                                        onDragElement = { _, _, _ -> },
                                        onResizeElement = { _, _, _ -> },
                                        onEndGesture = {},
                                        onEditTextContent = { _, _ -> },
                                        onToggleLock = {},
                                        onDuplicateElement = {},
                                        onDeleteElement = {},
                                        onPageSelected = viewModel::setActivePage,
                                        onAddPage = viewModel::addPage,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    FloatingActionButton(
                                        onClick = { viewModel.setCompactFormPreview(false) },
                                        containerColor = StudioColors.BrandAccent,
                                        contentColor = Color.White,
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .navigationBarsPadding()
                                            .padding(16.dp)
                                            .testTag("back_to_form_fab")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Form")
                                            Text("Back to Form", style = MaterialTheme.typography.labelMedium, color = Color.White)
                                        }
                                    }
                                }
                            } else {
                                FormGeneratorPanel(
                                    projectName = uiState.project.name,
                                    formData = uiState.project.formData,
                                    isCompactLayout = true,
                                    onProjectNameChange = viewModel::updateProjectName,
                                    onUpdateField = viewModel::updateFormField,
                                    onApplyRawQuote = viewModel::applyRawQuote,
                                    onCleanMenuBullets = viewModel::cleanMenuBullets,
                                    onGenerateDocument = viewModel::generateDocument,
                                    onStartNewClient = viewModel::startNewClient,
                                    onSwitchToVisual = { viewModel.setEditorMode(EditorMode.VISUAL) },
                                    onShowPreviewCompact = { viewModel.setCompactFormPreview(true) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    EditorMode.VISUAL -> {
                        Row(modifier = Modifier.fillMaxSize()) {
                            if (isExpanded) {
                                VisualEditorLeftPanel(
                                    elements = uiState.project.elements,
                                    deletedElements = uiState.deletedElements,
                                    activePage = uiState.activePage,
                                    selectedId = uiState.selectedId,
                                    onSelectElement = viewModel::selectElement,
                                    onToggleVisibility = { id ->
                                        viewModel.updateElement(id, commitHistory = true) { it.copy(visible = !it.visible) }
                                    },
                                    onToggleLock = { id ->
                                        viewModel.updateElement(id, commitHistory = true) { it.copy(locked = !it.locked) }
                                    },
                                    onReorderElement = viewModel::reorderElement,
                                    onDeleteElement = viewModel::deleteElement,
                                    onRestoreElement = viewModel::restoreDeletedElement,
                                    onAddPreset = viewModel::addPresetElement,
                                    onPickImage = {
                                        replacingImageElementId = null
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    onApplyFontPairing = viewModel::applyFontPairing,
                                    modifier = Modifier.width(270.dp).fillMaxHeight()
                                )
                                VerticalDivider(color = StudioColors.Border)
                            }

                            A4CanvasView(
                                project = uiState.project,
                                activePage = uiState.activePage,
                                selectedId = uiState.selectedId,
                                highlightedIds = uiState.highlightedIds,
                                activeSnapGuides = uiState.activeSnapGuides,
                                zoom = uiState.zoom,
                                interactive = true,
                                onSelectElement = viewModel::selectElement,
                                onDragElement = viewModel::dragElementWithSnap,
                                onResizeElement = { id, newW, newH ->
                                    viewModel.updateElement(id, commitHistory = false) { it.copy(width = newW, height = newH) }
                                },
                                onEndGesture = viewModel::endElementGesture,
                                onEditTextContent = { id, content ->
                                    viewModel.updateElement(id, commitHistory = true) { it.copy(content = content) }
                                },
                                onToggleLock = { id ->
                                    viewModel.updateElement(id, commitHistory = true) { it.copy(locked = !it.locked) }
                                },
                                onDuplicateElement = viewModel::duplicateElement,
                                onDeleteElement = viewModel::deleteElement,
                                onPageSelected = viewModel::setActivePage,
                                onAddPage = viewModel::addPage,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )

                            if (isExpanded || isMedium) {
                                VerticalDivider(color = StudioColors.Border)
                                VisualEditorRightPanel(
                                    selectedElement = uiState.selectedElement,
                                    suggestions = suggestions,
                                    pageSettings = uiState.project.pageSettings,
                                    pageCount = uiState.project.pageCount,
                                    onUpdateElement = viewModel::updateElement,
                                    onAlignSelected = viewModel::alignSelected,
                                    onReorderSelected = viewModel::reorderSelected,
                                    onReplaceImage = { targetId ->
                                        replacingImageElementId = targetId
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    onLocateElements = viewModel::highlightElements,
                                    onApplySuggestionFix = viewModel::applySuggestionFix,
                                    onApplyFullRedesign = viewModel::applyFullRedesign,
                                    onResetOriginalLayout = viewModel::resetToOriginalLayout,
                                    onUpdatePageSettings = viewModel::updatePageSettings,
                                    onUpdateMargins = viewModel::updateMargins,
                                    onAddPage = viewModel::addPage,
                                    onDeleteActivePage = viewModel::deleteActivePage,
                                    modifier = Modifier.width(if (isExpanded) 310.dp else 280.dp).fillMaxHeight()
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Sheet for Layers & Add (Compact / Medium)
        if (showLayersSheet) {
            ModalBottomSheet(
                onDismissRequest = { showLayersSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = StudioColors.Surface
            ) {
                Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.78f)) {
                    VisualEditorLeftPanel(
                        elements = uiState.project.elements,
                        deletedElements = uiState.deletedElements,
                        activePage = uiState.activePage,
                        selectedId = uiState.selectedId,
                        onSelectElement = { id ->
                            viewModel.selectElement(id)
                            showLayersSheet = false
                        },
                        onToggleVisibility = { id ->
                            viewModel.updateElement(id, commitHistory = true) { it.copy(visible = !it.visible) }
                        },
                        onToggleLock = { id ->
                            viewModel.updateElement(id, commitHistory = true) { it.copy(locked = !it.locked) }
                        },
                        onReorderElement = viewModel::reorderElement,
                        onDeleteElement = viewModel::deleteElement,
                        onRestoreElement = viewModel::restoreDeletedElement,
                        onAddPreset = { preset ->
                            viewModel.addPresetElement(preset)
                            showLayersSheet = false
                        },
                        onPickImage = {
                            replacingImageElementId = null
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onApplyFontPairing = { pairing ->
                            viewModel.applyFontPairing(pairing)
                            showLayersSheet = false
                        }
                    )
                }
            }
        }

        // Bottom Sheet for Design Assistant / Format Inspector / Page (Compact)
        if (showInspectorSheet) {
            ModalBottomSheet(
                onDismissRequest = { showInspectorSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = StudioColors.Surface
            ) {
                Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.82f)) {
                    VisualEditorRightPanel(
                        selectedElement = uiState.selectedElement,
                        suggestions = suggestions,
                        pageSettings = uiState.project.pageSettings,
                        pageCount = uiState.project.pageCount,
                        onUpdateElement = viewModel::updateElement,
                        onAlignSelected = viewModel::alignSelected,
                        onReorderSelected = viewModel::reorderSelected,
                        onReplaceImage = { targetId ->
                            replacingImageElementId = targetId
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onLocateElements = { ids ->
                            viewModel.highlightElements(ids)
                            showInspectorSheet = false
                        },
                        onApplySuggestionFix = viewModel::applySuggestionFix,
                        onApplyFullRedesign = viewModel::applyFullRedesign,
                        onResetOriginalLayout = viewModel::resetToOriginalLayout,
                        onUpdatePageSettings = viewModel::updatePageSettings,
                        onUpdateMargins = viewModel::updateMargins,
                        onAddPage = viewModel::addPage,
                        onDeleteActivePage = viewModel::deleteActivePage
                    )
                }
            }
        }

        // Workspace Storage, Versions & Cloud Sync Modal
        if (uiState.syncModalOpen) {
            WorkspaceSyncModal(
                syncStatus = syncStatus,
                activeDocumentId = uiState.project.id,
                documents = documents,
                versions = versions,
                templates = templates,
                clients = clients,
                onDismiss = { viewModel.setSyncModalOpen(false) },
                onSyncNow = viewModel::syncNow,
                onSignIn = viewModel::signInCloud,
                onSignUp = viewModel::signUpCloud,
                onSignOut = viewModel::signOutCloud,
                onResolveConflict = viewModel::resolveConflict,
                onSwitchDocument = viewModel::switchDocument,
                onCreateNewDocument = viewModel::createNewDocument,
                onDeleteDocument = viewModel::deleteDocument,
                onRestoreVersion = viewModel::restoreVersion,
                onApplyTemplate = viewModel::applyTemplate,
                onSaveTemplate = viewModel::saveCurrentAsTemplate,
                onLoadClient = viewModel::loadClientIntoForm
            )
        }
    }
}
