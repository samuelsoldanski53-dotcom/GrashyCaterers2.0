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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.ConflictStrategy
import com.example.model.DocumentVersionRecord
import com.example.model.LocalClientRecord
import com.example.model.LocalDocumentRecord
import com.example.model.LocalTemplateRecord
import com.example.model.RecordSyncStatus
import com.example.model.SyncStatusSnapshot
import com.example.ui.theme.StudioColors

@Composable
fun WorkspaceSyncModal(
    syncStatus: SyncStatusSnapshot,
    activeDocumentId: String,
    documents: List<LocalDocumentRecord>,
    versions: List<DocumentVersionRecord>,
    templates: List<LocalTemplateRecord>,
    clients: List<LocalClientRecord>,
    onDismiss: () -> Unit,
    onSyncNow: () -> Unit,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit,
    onSignOut: () -> Unit,
    onResolveConflict: (String, ConflictStrategy) -> Unit,
    onSwitchDocument: (LocalDocumentRecord) -> Unit,
    onCreateNewDocument: () -> Unit,
    onDeleteDocument: (String) -> Unit,
    onRestoreVersion: (DocumentVersionRecord) -> Unit,
    onApplyTemplate: (LocalTemplateRecord) -> Unit,
    onSaveTemplate: (String) -> Unit,
    onLoadClient: (LocalClientRecord) -> Unit
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var newTemplateName by remember { mutableStateOf("") }

    val conflicts = remember(documents) {
        documents.filter { it.syncStatus == RecordSyncStatus.CONFLICT }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = StudioColors.Surface,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.86f)
        ) {
            Column {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StudioColors.SurfaceMuted)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Workspace Storage & Sync", style = MaterialTheme.typography.titleMedium)
                        Text(syncStatus.label, style = MaterialTheme.typography.bodySmall, color = StudioColors.TextMuted)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_sync_modal_button")) {
                        Icon(Icons.Default.Close, contentDescription = "Close Sync Workspace")
                    }
                }
                HorizontalDivider(color = StudioColors.Border)

                // Conflict Alert Banner
                if (conflicts.isNotEmpty()) {
                    val conflictDoc = conflicts.first()
                    Surface(
                        color = StudioColors.ErrorBg,
                        border = BorderStroke(1.dp, Color(0xFFFECACA)),
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = StudioColors.ErrorText, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "Sync Conflict on \"${conflictDoc.name}\"",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = StudioColors.ErrorText
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { onResolveConflict(conflictDoc.id, ConflictStrategy.KEEP_LOCAL) },
                                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp)
                                ) { Text("Keep Local", fontSize = 11.sp) }
                                OutlinedButton(
                                    onClick = { onResolveConflict(conflictDoc.id, ConflictStrategy.KEEP_REMOTE) },
                                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp)
                                ) { Text("Use Cloud", fontSize = 11.sp) }
                                Button(
                                    onClick = { onResolveConflict(conflictDoc.id, ConflictStrategy.KEEP_BOTH) },
                                    colors = ButtonDefaults.buttonColors(containerColor = StudioColors.BrandAccent),
                                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp)
                                ) { Text("Save Both", fontSize = 11.sp) }
                            }
                        }
                    }
                }

                // 5 Tabs
                ScrollableTabRow(
                    selectedTabIndex = tabIndex,
                    edgePadding = 8.dp,
                    containerColor = StudioColors.Surface,
                    contentColor = StudioColors.BrandAccent
                ) {
                    listOf(
                        "Sync & Cloud",
                        "Docs (${documents.size})",
                        "Versions (${versions.size})",
                        "Templates (${templates.size})",
                        "Clients (${clients.size})"
                    ).forEachIndexed { idx, title ->
                        Tab(
                            selected = tabIndex == idx,
                            onClick = { tabIndex = idx },
                            text = { Text(title, fontSize = 12.sp) },
                            modifier = Modifier.testTag("sync_tab_$idx")
                        )
                    }
                }
                HorizontalDivider(color = StudioColors.Border)

                when (tabIndex) {
                    0 -> Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = StudioColors.SurfaceMuted,
                            border = BorderStroke(1.dp, StudioColors.Border)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Local Room Database (Offline-First)", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Every quotation, client, template, and version snapshot is saved locally on your device automatically — zero data loss offline.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = StudioColors.TextSecondary
                                )
                                Text(
                                    "Pending upload queue: ${syncStatus.pendingCount} item(s)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = StudioColors.BrandAccent
                                )
                                syncStatus.lastSyncedAt?.let {
                                    Text("Last synced: $it", style = MaterialTheme.typography.bodySmall, color = StudioColors.TextMuted)
                                }
                                syncStatus.lastError?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = StudioColors.ErrorText)
                                }
                            }
                        }

                        if (!syncStatus.supabaseConfigured) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = StudioColors.BrandSoftBg,
                                border = BorderStroke(1.dp, StudioColors.BrandSoftBorder)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Optional Cloud Sync", style = MaterialTheme.typography.titleSmall, color = Color(0xFF9A3412))
                                    Text(
                                        "To enable cross-device Supabase sync, set VITE_SUPABASE_URL and VITE_SUPABASE_ANON_KEY in Secrets.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF9A3412)
                                    )
                                }
                            }
                        } else if (!syncStatus.isAuthenticated) {
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("Email") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("cloud_email_input")
                            )
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Password") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth().testTag("cloud_password_input")
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onSignIn(email, password) },
                                    enabled = !syncStatus.isSyncing && email.isNotBlank() && password.length >= 6,
                                    colors = ButtonDefaults.buttonColors(containerColor = StudioColors.BrandAccent),
                                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("cloud_signin_button")
                                ) { Text("Sign In") }
                                OutlinedButton(
                                    onClick = { onSignUp(email, password) },
                                    enabled = !syncStatus.isSyncing && email.isNotBlank() && password.length >= 6,
                                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("cloud_signup_button")
                                ) { Text("Create Account") }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = StudioColors.SuccessBg,
                                border = BorderStroke(1.dp, StudioColors.SuccessBorder)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Signed in", style = MaterialTheme.typography.labelMedium, color = StudioColors.SuccessText)
                                        Text(syncStatus.userEmail ?: "", style = MaterialTheme.typography.titleSmall, color = StudioColors.TextPrimary)
                                    }
                                    OutlinedButton(onClick = onSignOut, modifier = Modifier.defaultMinSize(minHeight = 44.dp)) {
                                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Sign Out", fontSize = 12.sp)
                                    }
                                }
                            }
                            Button(
                                onClick = onSyncNow,
                                enabled = !syncStatus.isSyncing && syncStatus.isOnline,
                                colors = ButtonDefaults.buttonColors(containerColor = StudioColors.BrandAccent),
                                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("sync_now_button")
                            ) {
                                Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (syncStatus.isSyncing) "Syncing..." else "Sync Workspace Now")
                            }
                        }
                    }

                    1 -> Column(modifier = Modifier.weight(1f)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Button(
                                onClick = onCreateNewDocument,
                                colors = ButtonDefaults.buttonColors(containerColor = StudioColors.BrandAccent),
                                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("create_new_doc_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("New Quotation Document")
                            }
                        }
                        LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(documents, key = { it.id }) { doc ->
                                val isActive = doc.id == activeDocumentId
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isActive) StudioColors.BrandSoftBg else StudioColors.SurfaceMuted,
                                    border = BorderStroke(1.dp, if (isActive) StudioColors.BrandAccent else StudioColors.Border),
                                    modifier = Modifier.fillMaxWidth().clickable { onSwitchDocument(doc) }
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(doc.name, style = MaterialTheme.typography.titleSmall)
                                            Text("v${doc.version} · ${doc.syncStatus.name}", style = MaterialTheme.typography.bodySmall, color = StudioColors.TextMuted)
                                        }
                                        if (documents.size > 1) {
                                            IconButton(onClick = { onDeleteDocument(doc.id) }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete Document", tint = StudioColors.ErrorText)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(versions, key = { it.id }) { ver ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = StudioColors.SurfaceMuted,
                                border = BorderStroke(1.dp, StudioColors.Border)
                            ) {
                                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.History, contentDescription = null, tint = StudioColors.BrandAccent, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("v${ver.version} — ${ver.summary}", style = MaterialTheme.typography.titleSmall)
                                        Text(ver.createdAt.take(19).replace('T', ' '), style = MaterialTheme.typography.bodySmall, color = StudioColors.TextMuted)
                                    }
                                    OutlinedButton(onClick = { onRestoreVersion(ver) }, modifier = Modifier.defaultMinSize(minHeight = 44.dp)) {
                                        Text("Restore", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    3 -> Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newTemplateName,
                                onValueChange = { newTemplateName = it },
                                label = { Text("Save current layout as template", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    onSaveTemplate(newTemplateName)
                                    newTemplateName = ""
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = StudioColors.BrandAccent),
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            ) { Text("Save") }
                        }
                        LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(templates, key = { it.id }) { tpl ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = StudioColors.SurfaceMuted,
                                    border = BorderStroke(1.dp, StudioColors.Border)
                                ) {
                                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Style, contentDescription = null, tint = StudioColors.BrandAccent, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(tpl.name, style = MaterialTheme.typography.titleSmall)
                                            Text("${tpl.elements.size} elements · ${tpl.layoutMode}", style = MaterialTheme.typography.bodySmall, color = StudioColors.TextMuted)
                                        }
                                        Button(
                                            onClick = { onApplyTemplate(tpl) },
                                            colors = ButtonDefaults.buttonColors(containerColor = StudioColors.BrandAccent),
                                            modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                                        ) { Text("Apply", fontSize = 11.sp) }
                                    }
                                }
                            }
                        }
                    }

                    else -> LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (clients.isEmpty()) {
                            item {
                                Text(
                                    "Clients are automatically indexed here whenever you enter a client name.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = StudioColors.TextMuted,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                        items(clients, key = { it.id }) { client ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = StudioColors.SurfaceMuted,
                                border = BorderStroke(1.dp, StudioColors.Border),
                                modifier = Modifier.fillMaxWidth().clickable { onLoadClient(client) }
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = StudioColors.BrandAccent, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(client.name, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            listOfNotNull(
                                                client.venue.takeIf { it.isNotEmpty() },
                                                client.defaultPax.takeIf { it.isNotEmpty() }?.let { "$it PAX" }
                                            ).joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = StudioColors.TextMuted
                                        )
                                    }
                                    OutlinedButton(onClick = { onLoadClient(client) }, modifier = Modifier.defaultMinSize(minHeight = 44.dp)) {
                                        Text("Use", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
