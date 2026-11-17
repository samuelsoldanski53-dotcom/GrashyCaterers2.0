package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.GrashyDatabase
import com.example.data.WorkspaceRepository
import com.example.domain.DesignAnalyzer
import com.example.domain.FormSync
import com.example.domain.GrashyInitialProject
import com.example.domain.HistoryManager
import com.example.domain.QuoteParser
import com.example.export.DocumentExporter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GrashyStudioQaTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunchesAndSwitchesBetweenFormAndVisualModes() {
        composeTestRule.waitForIdle()
        // Verify top bar mode toggles are rendered
        composeTestRule.onNodeWithTag("mode_form_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mode_visual_button").assertIsDisplayed()

        // Switch to Visual Editor mode
        composeTestRule.onNodeWithTag("mode_visual_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("a4_paper_surface").assertIsDisplayed()

        // Switch back to Form Generator mode
        composeTestRule.onNodeWithTag("mode_form_button").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun workspaceSyncModalOpensAndCloses() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sync_status_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        // Close modal via close button
        composeTestRule.onNodeWithTag("close_sync_modal_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun quoteParserHandlesValidAndEdgeCaseInputs() {
        val sample = """
            Client: Jane Doe
            Date: 15th December 2026
            Venue: Karen Blixen Gardens
            Pax: 120 guests
            Menu:
            - Pilau Rice
            - Beef Stew
            - Chapati
            Total: KES 180,000
            Deposit: KES 90,000
        """.trimIndent()

        val parsed = QuoteParser.parseQuotationText(sample)
        assertTrue(parsed.menuItems.size >= 3)

        val emptyParsed = QuoteParser.parseQuotationText("")
        assertTrue(emptyParsed.menuItems.isEmpty())
    }

    @Test
    fun formSyncComputesBalanceAndResetsClient() {
        val balance = FormSync.computeBalanceFromAmounts("KES 180,000", "KES 90,000")
        assertNotNull(balance)
        assertTrue(balance!!.contains("90,000"))

        val project = GrashyInitialProject.createRedesignedGrashyProject()
        val reset = FormSync.createResetClientFormData(project.formData)
        assertEquals("", reset.clientName)
        assertEquals("", reset.venue)
    }

    @Test
    fun historyManagerUndoRedoWorks() {
        val p1 = GrashyInitialProject.createRedesignedGrashyProject()
        val p2 = p1.copy(name = "Updated Quotation")
        var state = HistoryManager.loadDocument(p1)
        assertFalse(state.canUndo)

        state = HistoryManager.commit(state) { p2 }
        assertTrue(state.canUndo)
        assertEquals("Updated Quotation", state.present.name)

        state = HistoryManager.undo(state)
        assertEquals(p1.name, state.present.name)
        assertTrue(state.canRedo)

        state = HistoryManager.redo(state)
        assertEquals("Updated Quotation", state.present.name)
    }

    @Test
    fun designAnalyzerAuditsOriginalAndRedesignedProjects() {
        val original = GrashyInitialProject.createOriginalGrashyProject()
        val suggestions = DesignAnalyzer.analyzeLetterheadDesign(original)
        val summary = DesignAnalyzer.computeLayoutHealthSummary(suggestions)
        assertNotNull(summary)
        assertTrue(suggestions.isNotEmpty())
    }

    @Test
    fun roomDatabaseSeedsPersistsAndDeletesDocuments() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, GrashyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = WorkspaceRepository(db)
            val initial = GrashyInitialProject.createRedesignedGrashyProject()
            val seeded = repo.ensureLocalDatabaseSeeded(initial)
            assertEquals(initial.name, seeded.name)

            val created = repo.createNewLocalDocument(name = "QA Test Quotation")
            assertEquals("QA Test Quotation", created.name)
            assertNotNull(repo.getDocumentById(created.id))

            repo.deleteLocalDocumentById(created.id)
            val afterDelete = repo.getDocumentById(created.id)
            assertNotNull(afterDelete)
            assertTrue(afterDelete!!.isDeleted)
        } finally {
            db.close()
        }
    }

    @Test
    fun documentExporterGeneratesValidDocxAndJson() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val project = GrashyInitialProject.createRedesignedGrashyProject()

        val docx = DocumentExporter.exportToDocx(context, project)
        assertTrue(docx.fileName.endsWith(".docx"))
        assertEquals(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            docx.mimeType
        )

        val jsonExport = DocumentExporter.exportProjectJson(context, project)
        assertTrue(jsonExport.fileName.endsWith(".grashy.json"))
        assertEquals("application/json", jsonExport.mimeType)
    }
}
