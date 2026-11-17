# Grashy Studio — Native Android Letterhead Editor

Grashy Studio is a native Android application built with **Kotlin**, **Jetpack Compose**, **Material Design 3**, and **Room**, rewritten from the original React + Capacitor project while preserving all quotation generation, A4 visual editing, design auditing, offline-first persistence, and document export workflows.

## Key Features
- **Form Generator & Quick Quote Auto-Fill**: Structured event, menu, payment (auto-calculating total and balance due), and sender fields with a WhatsApp quotation note parser (`QuoteParser`) and one-tap bullet formatting.
- **Interactive A4 Visual Editor**: Proportional `210mm × 297mm` canvas with millimeter rulers, printable margin guides, optional 5mm grid, smart snap guides (`Geometry`), drag-and-drop positioning, corner resize handles, inline text editing, Z-order layer management, and multi-page support.
- **Design Assistant & Typography Pairings**: Layout health analyzer (`DesignAnalyzer`) detecting margin clipping, punctuation/bullet issues, payment block stacking, overlap, and footer wave bleed, with one-tap fixes, full redesign polish, and revert to the original baseline PDF layout.
- **Native Document Export & Import**: Print-ready multi-page **PDF** export (`android.graphics.pdf.PdfDocument`), structured **Word (.docx)** OOXML generation, `.grashy.json` project backups, zero-permission Android Photo Picker (`PickVisualMedia`) image insertion, and SAF import for PDF (via `PdfRenderer`), DOCX, images, and JSON.
- **Offline-First Room Storage & Optional Supabase Sync**: Local Room persistence for documents, clients, templates, version snapshots, and pending sync queue, with optional Supabase authentication and conflict resolution (`Keep Local`, `Use Cloud`, `Save Both`).

## Project Structure
- `app/src/main/java/com/example/model/`: `LetterheadModels.kt`, `SyncModels.kt`
- `app/src/main/java/com/example/domain/`: `Geometry.kt`, `FormSync.kt`, `QuoteParser.kt`, `GrashyInitialProject.kt`, `DesignAnalyzer.kt`, `HistoryReducer.kt`, `SyncEngine.kt`
- `app/src/main/java/com/example/data/`: Room database (`Entities.kt`, `Daos.kt`, `GrashyDatabase.kt`), `WorkspaceRepository.kt`, `SupabaseSyncClient.kt`
- `app/src/main/java/com/example/export/`: `DocumentExporter.kt`
- `app/src/main/java/com/example/ui/`: `Theme.kt`, `StudioViewModel.kt`, `TopStudioBar.kt`, `FormGeneratorScreen.kt`, `A4CanvasView.kt`, `VisualEditorLeftPanel.kt`, `VisualEditorRightPanel.kt`, `WorkspaceSyncModal.kt`

## Configuration
To enable optional cross-device Supabase cloud sync, set `VITE_SUPABASE_URL` and `VITE_SUPABASE_ANON_KEY` in `.env` or the AI Studio Secrets panel (see `.env.example` and `supabase/schema.sql`).
