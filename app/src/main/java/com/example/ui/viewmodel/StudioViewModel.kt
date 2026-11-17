package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GrashyDatabase
import com.example.data.SupabaseSyncClient
import com.example.data.WorkspaceRepository
import com.example.domain.DesignAnalyzer
import com.example.domain.FormSync
import com.example.domain.Geometry
import com.example.domain.GrashyInitialProject
import com.example.domain.HistoryManager
import com.example.domain.QuoteParser
import com.example.domain.SyncEngine
import com.example.export.DocumentExporter
import com.example.model.CloudUserSession
import com.example.model.ConflictStrategy
import com.example.model.DesignSuggestion
import com.example.model.DocumentVersionRecord
import com.example.model.EditorMode
import com.example.model.ElementRole
import com.example.model.ElementType
import com.example.model.FontPairing
import com.example.model.LetterheadElement
import com.example.model.LetterheadFormData
import com.example.model.LetterheadProject
import com.example.model.LocalClientRecord
import com.example.model.LocalDocumentRecord
import com.example.model.LocalTemplateRecord
import com.example.model.PageMargins
import com.example.model.PageSettings
import com.example.model.RecordSyncStatus
import com.example.model.ShapeStyleConfig
import com.example.model.SnapGuide
import com.example.model.SyncStatusSnapshot
import com.example.model.TextAlignOption
import com.example.model.TextStyleConfig
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AddElementPreset {
    HEADING, SUBTITLE, BODY, LINE, BOX
}

sealed class ShareEvent {
    data class ExportReady(val result: DocumentExporter.ExportFileResult) : ShareEvent()
}

data class StudioUiState(
    val history: HistoryManager.HistoryState = HistoryManager.HistoryState(
        present = GrashyInitialProject.createRedesignedGrashyProject()
    ),
    val editorMode: EditorMode = EditorMode.FORM,
    val activePage: Int = 0,
    val selectedId: String? = null,
    val highlightedIds: List<String> = emptyList(),
    val activeSnapGuides: List<SnapGuide> = emptyList(),
    val zoom: Float = 0.68f,
    val deletedElements: List<LetterheadElement> = emptyList(),
    val isExporting: Boolean = false,
    val noticeMessage: String? = null,
    val syncModalOpen: Boolean = false,
    val showPreviewOnCompactForm: Boolean = false,
    val isInitialized: Boolean = false
) {
    val project: LetterheadProject get() = history.present
    val selectedElement: LetterheadElement?
        get() = project.elements.find { it.id == selectedId }
}

class StudioViewModel(application: Application) : AndroidViewModel(application) {
    private val db = GrashyDatabase.getInstance(application)
    private val repository = WorkspaceRepository(db)
    private val supabaseClient = SupabaseSyncClient(repository)

    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    private val _isOnline = MutableStateFlow(checkNetworkOnline(application))
    private val _isSyncing = MutableStateFlow(false)
    private val _syncError = MutableStateFlow<String?>(null)
    private val _cloudSession = MutableStateFlow<Pair<CloudUserSession, String>?>(null)

    private val _shareEvents = MutableSharedFlow<ShareEvent>()
    val shareEvents = _shareEvents.asSharedFlow()

    val documents: StateFlow<List<LocalDocumentRecord>> = repository.documentsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val clients: StateFlow<List<LocalClientRecord>> = repository.clientsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val templates: StateFlow<List<LocalTemplateRecord>> = repository.templatesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeDocVersions: StateFlow<List<DocumentVersionRecord>> = _uiState
        .map { it.project.id }
        .flatMapLatest { docId ->
            if (docId.isBlank()) flowOf(emptyList()) else repository.observeVersionsForDocument(docId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(FlowPreview::class)
    val suggestions: StateFlow<List<DesignSuggestion>> = _uiState
        .map { it.project }
        .debounce(350)
        .map { DesignAnalyzer.analyzeLetterheadDesign(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeDocumentRecord: StateFlow<LocalDocumentRecord?> = combine(
        documents,
        _uiState.map { it.project.id }
    ) { docs, activeId ->
        docs.find { it.id == activeId } ?: docs.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val syncStatus: StateFlow<SyncStatusSnapshot> = combine(
        _isOnline,
        _isSyncing,
        _cloudSession,
        repository.pendingCountFlow,
        combine(documents, repository.lastSyncedAtFlow, _syncError) { docs, syncedAt, err ->
            Triple(docs.count { it.syncStatus == RecordSyncStatus.CONFLICT }, syncedAt, err)
        }
    ) { online, syncing, sessionPair, pendingCount, docMeta ->
        SyncEngine.deriveSyncStatusSnapshot(
            isOnline = online,
            isSyncing = syncing,
            supabaseConfigured = supabaseClient.isConfigured(),
            isAuthenticated = sessionPair != null,
            userEmail = sessionPair?.first?.email,
            pendingCount = pendingCount,
            conflictCount = docMeta.first,
            lastSyncedAt = docMeta.second,
            lastError = docMeta.third
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        SyncEngine.deriveSyncStatusSnapshot(
            isOnline = true,
            isSyncing = false,
            supabaseConfigured = supabaseClient.isConfigured(),
            isAuthenticated = false,
            userEmail = null,
            pendingCount = 0,
            conflictCount = 0,
            lastSyncedAt = null,
            lastError = null
        )
    )

    private var autoSaveJob: Job? = null
    private var noticeDismissJob: Job? = null
    private var connectivityCallback: ConnectivityManager.NetworkCallback? = null

    init {
        registerNetworkMonitor(application)
        viewModelScope.launch {
            val savedSession = repository.getSavedCloudSession()
            if (savedSession != null) {
                _cloudSession.value = CloudUserSession(savedSession.first, savedSession.second) to savedSession.third
            }
            val initial = GrashyInitialProject.createRedesignedGrashyProject()
            val activeDoc = repository.ensureLocalDatabaseSeeded(initial)
            val (permPhysical, permPostal) = repository.getPermanentAddress()
            val hydratedProject = if (permPhysical != null || permPostal != null) {
                val updatedForm = activeDoc.project.formData.copy(
                    physicalAddress = permPhysical ?: activeDoc.project.formData.physicalAddress,
                    postalAddress = permPostal ?: activeDoc.project.formData.postalAddress
                )
                activeDoc.project.copy(
                    formData = updatedForm,
                    elements = FormSync.syncFormToElements(activeDoc.project.elements, updatedForm)
                )
            } else {
                activeDoc.project
            }
            _uiState.update {
                it.copy(
                    history = HistoryManager.loadDocument(hydratedProject),
                    isInitialized = true
                )
            }
        }
    }

    private fun checkNetworkOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun registerNetworkMonitor(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isOnline.value = true
            }
            override fun onLost(network: Network) {
                _isOnline.value = checkNetworkOnline(context)
            }
        }
        connectivityCallback = callback
        runCatching {
            cm.registerNetworkCallback(NetworkRequest.Builder().build(), callback)
        }
    }

    override fun onCleared() {
        super.onCleared()
        connectivityCallback?.let { cb ->
            val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            runCatching { cm?.unregisterNetworkCallback(cb) }
        }
    }

    private fun scheduleAutoSave() {
        if (!_uiState.value.isInitialized) return
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(800)
            repository.saveLocalDocumentWorkingCopy(_uiState.value.project, recordVersion = false)
        }
    }

    fun savePermanentAddress(physicalAddress: String, postalAddress: String) {
        viewModelScope.launch {
            repository.savePermanentAddress(physicalAddress, postalAddress)
            showNotice("Permanent sender address saved.")
        }
    }

    fun showNotice(message: String) {
        _uiState.update { it.copy(noticeMessage = message) }
        noticeDismissJob?.cancel()
        noticeDismissJob = viewModelScope.launch {
            delay(4200)
            _uiState.update { it.copy(noticeMessage = null) }
        }
    }

    fun clearNotice() {
        _uiState.update { it.copy(noticeMessage = null) }
    }

    fun setEditorMode(mode: EditorMode) {
        _uiState.update {
            it.copy(
                editorMode = mode,
                selectedId = if (mode == EditorMode.FORM) null else it.selectedId,
                showPreviewOnCompactForm = false
            )
        }
    }

    fun setCompactFormPreview(showPreview: Boolean) {
        _uiState.update { it.copy(showPreviewOnCompactForm = showPreview) }
    }

    fun setSyncModalOpen(open: Boolean) {
        _uiState.update { it.copy(syncModalOpen = open) }
    }

    fun setActivePage(pageIndex: Int) {
        _uiState.update { it.copy(activePage = pageIndex.coerceIn(0, (it.project.pageCount - 1).coerceAtLeast(0)), selectedId = null) }
    }

    fun setZoom(zoom: Float) {
        _uiState.update { it.copy(zoom = zoom.coerceIn(0.35f, 1.8f)) }
    }

    fun selectElement(id: String?) {
        _uiState.update { it.copy(selectedId = id) }
    }

    fun highlightElements(ids: List<String>) {
        _uiState.update {
            it.copy(
                highlightedIds = ids,
                selectedId = ids.firstOrNull() ?: it.selectedId,
                activePage = 0,
                editorMode = EditorMode.VISUAL
            )
        }
        viewModelScope.launch {
            delay(2500)
            _uiState.update { it.copy(highlightedIds = emptyList()) }
        }
    }

    fun undo() {
        _uiState.update { it.copy(history = HistoryManager.undo(it.history)) }
        scheduleAutoSave()
    }

    fun redo() {
        _uiState.update { it.copy(history = HistoryManager.redo(it.history)) }
        scheduleAutoSave()
    }

    fun updateFormField(transform: (LetterheadFormData) -> LetterheadFormData, recalcFrom: String? = null) {
        _uiState.update { state ->
            val nextHistory = HistoryManager.liveUpdate(state.history, captureCheckpoint = true) { prev ->
                var nextForm = transform(prev.formData)
                if (recalcFrom == "subtotalCost") {
                    val computedVat = FormSync.computeKenyaVatFromSubtotal(nextForm.subtotalCost)
                    if (computedVat != null) nextForm = nextForm.copy(vatAmount = computedVat)
                }
                if (recalcFrom == "subtotalCost" || recalcFrom == "vatAmount") {
                    val computed = FormSync.computeTotalFromBreakdown(nextForm.subtotalCost, nextForm.vatAmount, nextForm.totalAmount)
                    if (computed != null) nextForm = nextForm.copy(totalAmount = computed)
                }
                if (recalcFrom in listOf("subtotalCost", "vatAmount", "totalAmount", "depositAmount")) {
                    val computedBal = FormSync.computeBalanceFromAmounts(nextForm.totalAmount, nextForm.depositAmount)
                    if (computedBal != null) nextForm = nextForm.copy(balance = computedBal)
                }
                prev.copy(
                    formData = nextForm,
                    elements = FormSync.syncFormToElements(prev.elements, nextForm)
                )
            }
            state.copy(history = nextHistory)
        }
        scheduleAutoSave()
    }

    fun updateProjectName(name: String) {
        _uiState.update { state ->
            state.copy(
                history = HistoryManager.liveUpdate(state.history, captureCheckpoint = true) { prev ->
                    prev.copy(name = name)
                }
            )
        }
        scheduleAutoSave()
    }

    fun cleanMenuBullets() {
        _uiState.update { state ->
            val nextHistory = HistoryManager.commit(state.history) { prev ->
                val cleaned = prev.formData.eventDetails
                    .replace(Regex("💫\\s*"), "•  ")
                    .replace("Tea,Chocolate", "Tea & Hot Chocolate")
                    .replace("Mandazi/Bread", "Mandazi / Fresh Bread")
                val nextForm = prev.formData.copy(eventDetails = cleaned)
                prev.copy(
                    formData = nextForm,
                    elements = FormSync.syncFormToElements(prev.elements, nextForm)
                )
            }
            state.copy(history = nextHistory)
        }
        scheduleAutoSave()
        showNotice("Formatted menu bullets and punctuation.")
    }

    fun applyRawQuote(rawText: String) {
        if (rawText.isBlank()) return
        val parsed = QuoteParser.parseQuotationText(rawText)
        _uiState.update { state ->
            val nextHistory = HistoryManager.commit(state.history) { prev ->
                val cur = prev.formData
                val nextSubtotal = parsed.subtotal.ifEmpty { cur.subtotalCost }
                val nextVat = parsed.vat.ifEmpty { cur.vatAmount }
                val rawTotal = parsed.totalCost.ifEmpty { cur.totalAmount }
                val formattedTotal = if (rawTotal.isNotEmpty() && !rawTotal.contains("kes", ignoreCase = true)) {
                    "KES $rawTotal"
                } else {
                    rawTotal
                }
                val computedBalance = FormSync.computeBalanceFromAmounts(formattedTotal, cur.depositAmount) ?: cur.balance
                val nextForm = cur.copy(
                    date = parsed.date.ifEmpty { cur.date },
                    pax = parsed.pax.ifEmpty { cur.pax },
                    clientName = parsed.client.ifEmpty { cur.clientName },
                    venue = parsed.venue.ifEmpty { cur.venue },
                    eventDetails = if (parsed.menuItems.isNotEmpty()) QuoteParser.formatMenuOnlyText(parsed) else cur.eventDetails,
                    transportCost = parsed.transport.ifEmpty { cur.transportCost },
                    subtotalCost = nextSubtotal,
                    vatAmount = nextVat,
                    totalAmount = formattedTotal,
                    balance = computedBalance
                )
                prev.copy(
                    name = if (parsed.client.isNotEmpty()) "${parsed.client} — Quotation" else prev.name,
                    formData = nextForm,
                    elements = FormSync.syncFormToElements(prev.elements, nextForm)
                )
            }
            state.copy(history = nextHistory)
        }
        scheduleAutoSave()
        showNotice("Populated quotation fields from pasted note.")
    }

    fun generateDocument() {
        val current = _uiState.value.project
        _uiState.update { state ->
            val nextHistory = HistoryManager.commit(state.history) { prev ->
                val docTitle = if (prev.formData.clientName.isNotBlank()) {
                    "${prev.formData.clientName.trim()} — Quotation"
                } else {
                    prev.name
                }
                prev.copy(
                    name = docTitle,
                    elements = FormSync.syncFormToElements(prev.elements, prev.formData)
                )
            }
            state.copy(history = nextHistory)
        }
        viewModelScope.launch {
            val updated = _uiState.value.project
            val saved = repository.saveLocalDocumentWorkingCopy(
                project = updated,
                recordVersion = true,
                versionSummary = if (updated.formData.clientName.isNotBlank()) {
                    "Generated document for ${updated.formData.clientName}"
                } else {
                    "Generated document"
                }
            )
            _uiState.update { it.copy(history = HistoryManager.loadDocument(saved.project)) }
            showNotice("Document synced & saved locally (v${saved.version}).")
        }
    }

    fun startNewClient() {
        _uiState.update { state ->
            val nextHistory = HistoryManager.commit(state.history) { prev ->
                val resetForm = FormSync.createResetClientFormData(prev.formData)
                prev.copy(
                    name = "New Client Quotation",
                    formData = resetForm,
                    elements = FormSync.syncFormToElements(prev.elements, resetForm)
                )
            }
            state.copy(history = nextHistory)
        }
        scheduleAutoSave()
        showNotice("Cleared client & event fields — company branding preserved.")
    }

    fun updateElement(
        id: String,
        commitHistory: Boolean = false,
        transform: (LetterheadElement) -> LetterheadElement
    ) {
        _uiState.update { state ->
            val updater: (LetterheadProject) -> LetterheadProject = { prev ->
                var updatedRole: ElementRole? = null
                var updatedContent: String? = null
                var pageZeroTextEdited = false

                val nextElements = prev.elements.map { el ->
                    if (el.id != id) el
                    else {
                        val nextEl = transform(el)
                        if (nextEl.pageIndex == 0 && nextEl.type == ElementType.TEXT && nextEl.content != el.content) {
                            pageZeroTextEdited = true
                            updatedRole = nextEl.role
                            updatedContent = nextEl.content ?: ""
                        }
                        nextEl
                    }
                }

                var nextForm = prev.formData
                if (pageZeroTextEdited && updatedRole != null && updatedContent != null) {
                    nextForm = FormSync.syncElementEditToForm(prev.formData, updatedRole!!, updatedContent!!)
                }
                prev.copy(formData = nextForm, elements = nextElements)
            }

            val nextHistory = if (commitHistory) {
                HistoryManager.commit(state.history, skipIfUnchanged = true, updater = updater)
            } else {
                HistoryManager.liveUpdate(state.history, captureCheckpoint = true, updater = updater)
            }
            state.copy(history = nextHistory)
        }
        if (commitHistory) scheduleAutoSave()
    }

    fun dragElementWithSnap(id: String, targetX: Float, targetY: Float) {
        val state = _uiState.value
        val el = state.project.elements.find { it.id == id } ?: return
        if (el.locked) return

        val clampedX = Geometry.clamp(targetX, 0f, state.project.pageSettings.widthMm - el.width)
        val clampedY = Geometry.clamp(targetY, 0f, state.project.pageSettings.heightMm - el.height)
        val snap = Geometry.calculateSnap(
            movingId = id,
            movingX = clampedX,
            movingY = clampedY,
            movingWidth = el.width,
            movingHeight = el.height,
            pageIndex = el.pageIndex,
            allElements = state.project.elements,
            pageSettings = state.project.pageSettings
        )
        _uiState.update { s ->
            s.copy(
                activeSnapGuides = snap.guides,
                history = HistoryManager.liveUpdate(s.history, captureCheckpoint = true) { prev ->
                    prev.copy(
                        elements = prev.elements.map { item ->
                            if (item.id == id) item.copy(x = snap.x, y = snap.y) else item
                        }
                    )
                }
            )
        }
    }

    fun endElementGesture() {
        _uiState.update { state ->
            state.copy(
                activeSnapGuides = emptyList(),
                history = HistoryManager.finalizeCheckpoint(state.history)
            )
        }
        scheduleAutoSave()
    }

    fun addPresetElement(preset: AddElementPreset) {
        val state = _uiState.value
        val maxZ = state.project.elements
            .filter { it.pageIndex == state.activePage }
            .maxOfOrNull { it.zIndex } ?: 80
        val newId = "el-${System.currentTimeMillis()}"
        val marginL = state.project.pageSettings.margins.left

        val newEl = when (preset) {
            AddElementPreset.HEADING -> LetterheadElement(
                id = newId, pageIndex = state.activePage, type = ElementType.TEXT, role = ElementRole.CUSTOM,
                name = "Heading", x = marginL, y = 40f, width = 120f, height = 12f, zIndex = maxZ + 10,
                content = "Section Title",
                textStyle = TextStyleConfig(fontFamily = "Plus Jakarta Sans", fontSize = 16f, fontWeight = 700, lineHeight = 1.25f, color = "#C45911")
            )
            AddElementPreset.SUBTITLE -> LetterheadElement(
                id = newId, pageIndex = state.activePage, type = ElementType.TEXT, role = ElementRole.CUSTOM,
                name = "Subtitle", x = marginL, y = 55f, width = 110f, height = 8f, zIndex = maxZ + 10,
                content = "Event Overview & Menu",
                textStyle = TextStyleConfig(fontFamily = "DM Sans", fontSize = 11.5f, fontWeight = 600, lineHeight = 1.35f, color = "#475569")
            )
            AddElementPreset.BODY -> LetterheadElement(
                id = newId, pageIndex = state.activePage, type = ElementType.TEXT, role = ElementRole.CUSTOM,
                name = "Text Box", x = marginL, y = 70f, width = 140f, height = 28f, zIndex = maxZ + 10,
                content = "Enter menu notes, delivery instructions, or terms...",
                textStyle = TextStyleConfig(fontFamily = "DM Sans", fontSize = 10f, fontWeight = 400, lineHeight = 1.45f, paragraphSpacing = 2f, color = "#1E293B", paddingTop = 1f, paddingRight = 1.5f, paddingBottom = 1f, paddingLeft = 1.5f)
            )
            AddElementPreset.LINE -> LetterheadElement(
                id = newId, pageIndex = state.activePage, type = ElementType.LINE, role = ElementRole.CUSTOM,
                name = "Divider Line", x = marginL, y = 105f, width = 180f, height = 2f, zIndex = maxZ + 10,
                shapeStyle = ShapeStyleConfig(fill = "transparent", stroke = "#C45911", strokeWidth = 1f, lineStyle = "solid")
            )
            AddElementPreset.BOX -> LetterheadElement(
                id = newId, pageIndex = state.activePage, type = ElementType.SHAPE, role = ElementRole.CUSTOM,
                name = "Accent Panel", x = marginL, y = 115f, width = 180f, height = 26f, zIndex = 8,
                shapeStyle = ShapeStyleConfig(fill = "#FFF7ED", stroke = "#FDBA74", strokeWidth = 0.75f, borderRadius = 2f, lineStyle = "solid")
            )
        }

        _uiState.update { s ->
            s.copy(
                history = HistoryManager.commit(s.history) { prev -> prev.copy(elements = prev.elements + newEl) },
                selectedId = newId,
                editorMode = EditorMode.VISUAL
            )
        }
        scheduleAutoSave()
    }

    fun addImportedImage(uriStr: String, fileName: String) {
        val state = _uiState.value
        val maxZ = state.project.elements.filter { it.pageIndex == state.activePage }.maxOfOrNull { it.zIndex } ?: 80
        val newId = "img-${System.currentTimeMillis()}"
        val bmp = DocumentExporter.loadBitmapForImageSrc(getApplication(), uriStr)
        val ratio = if (bmp != null && bmp.height > 0) bmp.width.toFloat() / bmp.height else 1.5f
        val widthMm = 50f
        val heightMm = Geometry.roundMm(widthMm / ratio).coerceAtLeast(12f)
        val el = LetterheadElement(
            id = newId,
            pageIndex = state.activePage,
            type = ElementType.IMAGE,
            role = ElementRole.CUSTOM,
            name = fileName.ifBlank { "Image Asset" },
            x = state.project.pageSettings.margins.left,
            y = 20f,
            width = widthMm,
            height = heightMm,
            zIndex = maxZ + 10,
            imageSrc = uriStr,
            objectFit = "contain"
        )
        _uiState.update { s ->
            s.copy(
                history = HistoryManager.commit(s.history) { prev -> prev.copy(elements = prev.elements + el) },
                selectedId = newId,
                editorMode = EditorMode.VISUAL
            )
        }
        scheduleAutoSave()
        showNotice("Added image to canvas.")
    }

    fun replaceElementImage(id: String, uriStr: String) {
        updateElement(id, commitHistory = true) { it.copy(imageSrc = uriStr) }
        showNotice("Replaced image asset.")
    }

    fun alignSelected(alignment: Geometry.AlignmentTarget) {
        val id = _uiState.value.selectedId ?: return
        _uiState.update { state ->
            state.copy(
                history = HistoryManager.commit(state.history) { prev ->
                    prev.copy(
                        elements = prev.elements.map { el ->
                            if (el.id == id) Geometry.alignElementToPage(el, alignment, prev.pageSettings) else el
                        }
                    )
                }
            )
        }
        scheduleAutoSave()
    }

    fun reorderSelected(action: Geometry.ZOrderAction) {
        val id = _uiState.value.selectedId ?: return
        reorderElement(id, action)
    }

    fun reorderElement(id: String, action: Geometry.ZOrderAction) {
        _uiState.update { state ->
            state.copy(
                history = HistoryManager.commit(state.history) { prev ->
                    prev.copy(elements = Geometry.reorderZIndex(prev.elements, id, action))
                }
            )
        }
        scheduleAutoSave()
    }

    fun duplicateElement(id: String) {
        val orig = _uiState.value.project.elements.find { it.id == id } ?: return
        val copyId = "${orig.id}-copy-${System.currentTimeMillis()}"
        val dup = orig.copy(
            id = copyId,
            name = "${orig.name} (Copy)",
            x = Geometry.roundMm(orig.x + 5f),
            y = Geometry.roundMm(orig.y + 5f),
            zIndex = orig.zIndex + 5,
            role = ElementRole.CUSTOM
        )
        _uiState.update { state ->
            state.copy(
                history = HistoryManager.commit(state.history) { prev -> prev.copy(elements = prev.elements + dup) },
                selectedId = copyId
            )
        }
        scheduleAutoSave()
    }

    fun deleteElement(id: String) {
        val target = _uiState.value.project.elements.find { it.id == id } ?: return
        _uiState.update { state ->
            state.copy(
                deletedElements = (state.deletedElements + target).takeLast(12),
                selectedId = if (state.selectedId == id) null else state.selectedId,
                history = HistoryManager.commit(state.history) { prev ->
                    prev.copy(elements = prev.elements.filterNot { it.id == id })
                }
            )
        }
        scheduleAutoSave()
        showNotice("Removed \"${target.name}\".")
    }

    fun restoreDeletedElement(id: String) {
        val item = _uiState.value.deletedElements.find { it.id == id } ?: return
        _uiState.update { state ->
            state.copy(
                deletedElements = state.deletedElements.filterNot { it.id == id },
                selectedId = item.id,
                history = HistoryManager.commit(state.history) { prev ->
                    prev.copy(elements = prev.elements + item)
                }
            )
        }
        scheduleAutoSave()
    }

    fun applyFontPairing(pairing: FontPairing) {
        _uiState.update { state ->
            state.copy(
                history = HistoryManager.commit(state.history) { prev ->
                    prev.copy(
                        elements = prev.elements.map { el ->
                            val ts = el.textStyle ?: return@map el
                            val isDisplay = el.role == ElementRole.COMPANY_TITLE ||
                                el.role == ElementRole.CLOSING ||
                                ts.fontSize >= 14f
                            el.copy(
                                textStyle = ts.copy(
                                    fontFamily = if (isDisplay) pairing.displayFont else pairing.bodyFont
                                )
                            )
                        }
                    )
                }
            )
        }
        scheduleAutoSave()
        showNotice("Applied \"${pairing.name}\" font pairing.")
    }

    fun applySuggestionFix(suggestion: DesignSuggestion) {
        val fixFn = suggestion.applyFix ?: return
        _uiState.update { state ->
            state.copy(
                history = HistoryManager.commit(state.history) { prev -> fixFn(prev) }
            )
        }
        scheduleAutoSave()
        showNotice("Applied fix: ${suggestion.fixLabel}")
    }

    fun applyFullRedesign() {
        _uiState.update { state ->
            state.copy(
                history = HistoryManager.commit(state.history) { prev ->
                    GrashyInitialProject.applyCompleteRedesign(prev)
                }
            )
        }
        scheduleAutoSave()
        showNotice("Applied complete Grashy Caterers layout polish.")
    }

    fun resetToOriginalLayout() {
        val original = GrashyInitialProject.createOriginalGrashyProject()
        _uiState.update { state ->
            state.copy(
                activePage = 0,
                selectedId = null,
                history = HistoryManager.commit(state.history) { prev ->
                    original.copy(id = prev.id, version = prev.version)
                }
            )
        }
        scheduleAutoSave()
        showNotice("Restored original PDF baseline layout.")
    }

    fun updatePageSettings(transform: (PageSettings) -> PageSettings) {
        _uiState.update { state ->
            state.copy(
                history = HistoryManager.commit(state.history) { prev ->
                    prev.copy(pageSettings = transform(prev.pageSettings))
                }
            )
        }
        scheduleAutoSave()
    }

    fun updateMargins(transform: (PageMargins) -> PageMargins) {
        updatePageSettings { it.copy(margins = transform(it.margins)) }
    }

    fun addPage() {
        val newPageIdx = _uiState.value.project.pageCount
        _uiState.update { state ->
            state.copy(
                activePage = newPageIdx,
                selectedId = null,
                history = HistoryManager.commit(state.history) { prev ->
                    prev.copy(pageCount = prev.pageCount + 1)
                }
            )
        }
        scheduleAutoSave()
    }

    fun deleteActivePage() {
        val state = _uiState.value
        if (state.project.pageCount <= 1) return
        val target = state.activePage
        _uiState.update { s ->
            s.copy(
                activePage = (target - 1).coerceAtLeast(0),
                selectedId = null,
                history = HistoryManager.commit(s.history) { prev ->
                    prev.copy(
                        pageCount = prev.pageCount - 1,
                        elements = prev.elements
                            .filterNot { it.pageIndex == target }
                            .map { el -> if (el.pageIndex > target) el.copy(pageIndex = el.pageIndex - 1) else el }
                    )
                }
            )
        }
        scheduleAutoSave()
    }

    // Workspace Storage & Cloud Sync Actions
    fun saveManualProjectSnapshot() {
        viewModelScope.launch {
            val saved = repository.saveLocalDocumentWorkingCopy(
                project = _uiState.value.project,
                recordVersion = true,
                versionSummary = "Manual workspace snapshot"
            )
            _uiState.update { it.copy(history = HistoryManager.loadDocument(saved.project)) }
            showNotice("Saved workspace snapshot (v${saved.version}).")
        }
    }

    fun switchDocument(doc: LocalDocumentRecord) {
        viewModelScope.launch {
            repository.setActiveDocumentId(doc.id)
            _uiState.update {
                it.copy(
                    history = HistoryManager.loadDocument(doc.project),
                    activePage = 0,
                    selectedId = null,
                    syncModalOpen = false
                )
            }
            showNotice("Opened \"${doc.name}\".")
        }
    }

    fun createNewDocument() {
        viewModelScope.launch {
            val created = repository.createNewLocalDocument()
            _uiState.update {
                it.copy(
                    history = HistoryManager.loadDocument(created.project),
                    activePage = 0,
                    selectedId = null,
                    syncModalOpen = false
                )
            }
            showNotice("Created new document \"${created.name}\".")
        }
    }

    fun deleteDocument(docId: String) {
        viewModelScope.launch {
            if (documents.value.size <= 1) {
                showNotice("At least one document must remain in workspace.")
                return@launch
            }
            repository.deleteLocalDocumentById(docId)
            val remaining = documents.value.filterNot { it.id == docId }
            if (_uiState.value.project.id == docId && remaining.isNotEmpty()) {
                val next = remaining.first()
                _uiState.update {
                    it.copy(
                        history = HistoryManager.loadDocument(next.project),
                        activePage = 0,
                        selectedId = null
                    )
                }
            }
            showNotice("Document removed from workspace.")
        }
    }

    fun restoreVersion(version: DocumentVersionRecord) {
        _uiState.update { state ->
            state.copy(
                history = HistoryManager.commit(state.history) { version.snapshot },
                syncModalOpen = false
            )
        }
        scheduleAutoSave()
        showNotice("Restored version v${version.version}.")
    }

    fun applyTemplate(template: LocalTemplateRecord) {
        _uiState.update { state ->
            val nextHistory = HistoryManager.commit(state.history) { prev ->
                val formSynced = FormSync.syncFormToElements(template.elements, prev.formData)
                prev.copy(
                    layoutMode = template.layoutMode,
                    pageSettings = template.pageSettings,
                    elements = formSynced
                )
            }
            state.copy(history = nextHistory, syncModalOpen = false)
        }
        scheduleAutoSave()
        showNotice("Applied template \"${template.name}\".")
    }

    fun saveCurrentAsTemplate(templateName: String) {
        viewModelScope.launch {
            val rec = repository.saveTemplateFromProject(_uiState.value.project, templateName)
            showNotice("Saved template \"${rec.name}\".")
        }
    }

    fun loadClientIntoForm(client: LocalClientRecord) {
        _uiState.update { state ->
            val nextHistory = HistoryManager.commit(state.history) { prev ->
                val nextForm = prev.formData.copy(
                    clientName = client.name,
                    venue = client.venue.ifEmpty { prev.formData.venue },
                    pax = client.defaultPax.ifEmpty { prev.formData.pax }
                )
                prev.copy(
                    formData = nextForm,
                    elements = FormSync.syncFormToElements(prev.elements, nextForm)
                )
            }
            state.copy(history = nextHistory, syncModalOpen = false, editorMode = EditorMode.FORM)
        }
        scheduleAutoSave()
        showNotice("Loaded client \"${client.name}\".")
    }

    fun resolveConflict(docId: String, strategy: ConflictStrategy) {
        viewModelScope.launch {
            val resolved = repository.resolveConflict(docId, strategy)
            if (resolved != null && resolved.id == _uiState.value.project.id) {
                _uiState.update { it.copy(history = HistoryManager.loadDocument(resolved.project)) }
            }
            showNotice("Conflict resolved.")
        }
    }

    fun signInCloud(email: String, password: String) {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            runCatching { supabaseClient.signIn(email.trim(), password) }
                .onSuccess { auth ->
                    if (auth.session != null && auth.accessToken != null) {
                        _cloudSession.value = auth.session to auth.accessToken
                        showNotice("Signed in as ${auth.session.email}.")
                        runSyncNowInternal(auth.session.userId, auth.accessToken)
                    }
                }
                .onFailure { err ->
                    _syncError.value = err.message ?: "Sign in failed."
                }
            _isSyncing.value = false
        }
    }

    fun signUpCloud(email: String, password: String) {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            runCatching { supabaseClient.signUp(email.trim(), password) }
                .onSuccess { auth ->
                    if (auth.needsEmailConfirmation) {
                        showNotice("Check your email to confirm account creation.")
                    } else if (auth.session != null && auth.accessToken != null) {
                        _cloudSession.value = auth.session to auth.accessToken
                        showNotice("Cloud account created and signed in.")
                        runSyncNowInternal(auth.session.userId, auth.accessToken)
                    }
                }
                .onFailure { err ->
                    _syncError.value = err.message ?: "Sign up failed."
                }
            _isSyncing.value = false
        }
    }

    fun signOutCloud() {
        viewModelScope.launch {
            supabaseClient.signOut()
            _cloudSession.value = null
            showNotice("Signed out of cloud workspace.")
        }
    }

    fun syncNow() {
        val sess = _cloudSession.value ?: return
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            runSyncNowInternal(sess.first.userId, sess.second)
            _isSyncing.value = false
        }
    }

    private suspend fun runSyncNowInternal(userId: String, token: String) {
        runCatching { supabaseClient.synchronize(userId, token) }
            .onSuccess { res ->
                val activeId = _uiState.value.project.id
                repository.getDocumentById(activeId)?.let { latest ->
                    if (latest.updatedAt > _uiState.value.project.updatedAt) {
                        _uiState.update { it.copy(history = HistoryManager.loadDocument(latest.project)) }
                    }
                }
                showNotice("Synced: ${res.uploadedCount} uploaded, ${res.pulledCount} pulled.")
            }
            .onFailure { err ->
                _syncError.value = err.message ?: "Sync failed."
            }
    }

    // Export & Import
    fun exportPdf() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            runCatching { DocumentExporter.exportToPdf(getApplication(), _uiState.value.project) }
                .onSuccess { res ->
                    _shareEvents.emit(ShareEvent.ExportReady(res))
                    showNotice("PDF exported: ${res.fileName}")
                }
                .onFailure { err ->
                    showNotice("PDF export failed: ${err.message}")
                }
            _uiState.update { it.copy(isExporting = false) }
        }
    }

    fun exportDocx() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            runCatching { DocumentExporter.exportToDocx(getApplication(), _uiState.value.project) }
                .onSuccess { res ->
                    _shareEvents.emit(ShareEvent.ExportReady(res))
                    showNotice("DOCX exported: ${res.fileName}")
                }
                .onFailure { err ->
                    showNotice("DOCX export failed: ${err.message}")
                }
            _uiState.update { it.copy(isExporting = false) }
        }
    }

    fun exportProjectJson() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            runCatching { DocumentExporter.exportProjectJson(getApplication(), _uiState.value.project) }
                .onSuccess { res ->
                    _shareEvents.emit(ShareEvent.ExportReady(res))
                    showNotice("Backup saved: ${res.fileName}")
                }
                .onFailure { err ->
                    showNotice("Backup export failed: ${err.message}")
                }
            _uiState.update { it.copy(isExporting = false) }
        }
    }

    fun importFromUri(uri: android.net.Uri) {
        viewModelScope.launch {
            val state = _uiState.value
            val maxZ = state.project.elements.filter { it.pageIndex == state.activePage }.maxOfOrNull { it.zIndex } ?: 80
            runCatching {
                DocumentExporter.importFromUri(getApplication(), uri, state.activePage, maxZ + 10)
            }.onSuccess { result ->
                when (result) {
                    is DocumentExporter.ImportResult.LoadedProject -> {
                        val saved = repository.saveLocalDocumentWorkingCopy(result.project, recordVersion = true, versionSummary = "Imported JSON project")
                        _uiState.update {
                            it.copy(
                                history = HistoryManager.loadDocument(saved.project),
                                activePage = 0,
                                selectedId = null
                            )
                        }
                        showNotice(result.message)
                    }
                    is DocumentExporter.ImportResult.AddedElements -> {
                        val firstId = result.elements.firstOrNull()?.id
                        _uiState.update { s ->
                            s.copy(
                                history = HistoryManager.commit(s.history) { prev ->
                                    prev.copy(elements = prev.elements + result.elements)
                                },
                                selectedId = firstId,
                                editorMode = EditorMode.VISUAL
                            )
                        }
                        scheduleAutoSave()
                        showNotice(result.message)
                    }
                }
            }.onFailure { err ->
                showNotice(err.message ?: "Import failed.")
            }
        }
    }
}
