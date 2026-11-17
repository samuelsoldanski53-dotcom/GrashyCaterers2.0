package com.example.domain

import com.example.model.LetterheadElement
import com.example.model.LetterheadProject

object HistoryManager {
    const val MAX_HISTORY_STEPS = 40

    data class HistoryState(
        val past: List<LetterheadProject> = emptyList(),
        val present: LetterheadProject,
        val future: List<LetterheadProject> = emptyList(),
        val checkpoint: LetterheadProject? = null
    ) {
        val canUndo: Boolean get() = past.isNotEmpty() || checkpoint != null
        val canRedo: Boolean get() = future.isNotEmpty()
    }

    private fun pushPast(past: List<LetterheadProject>, entry: LetterheadProject): List<LetterheadProject> {
        val next = past + entry
        return if (next.size > MAX_HISTORY_STEPS) next.takeLast(MAX_HISTORY_STEPS) else next
    }

    fun commit(
        state: HistoryState,
        skipIfUnchanged: Boolean = false,
        updater: (LetterheadProject) -> LetterheadProject
    ): HistoryState {
        val updated = updater(state.present)
        if (skipIfUnchanged && state.checkpoint == null && updated == state.present) {
            return state
        }
        val nextPresent = updated.copy(updatedAt = GrashyInitialProject.nowIso())
        val undoBaseline = state.checkpoint ?: state.present
        return HistoryState(
            past = pushPast(state.past, undoBaseline),
            present = nextPresent,
            future = emptyList(),
            checkpoint = null
        )
    }

    fun liveUpdate(
        state: HistoryState,
        captureCheckpoint: Boolean = false,
        updater: (LetterheadProject) -> LetterheadProject
    ): HistoryState {
        val updated = updater(state.present)
        val nextPresent = updated.copy(updatedAt = GrashyInitialProject.nowIso())
        val nextCheckpoint = if (captureCheckpoint && state.checkpoint == null) state.present else state.checkpoint
        return HistoryState(
            past = state.past,
            present = nextPresent,
            future = if (captureCheckpoint) emptyList() else state.future,
            checkpoint = nextCheckpoint
        )
    }

    fun finalizeCheckpoint(
        state: HistoryState,
        fallbackElements: List<LetterheadElement>? = null
    ): HistoryState {
        if (state.checkpoint != null) {
            return HistoryState(
                past = pushPast(state.past, state.checkpoint),
                present = state.present,
                future = emptyList(),
                checkpoint = null
            )
        }
        if (fallbackElements != null) {
            return HistoryState(
                past = pushPast(state.past, state.present.copy(elements = fallbackElements)),
                present = state.present,
                future = emptyList(),
                checkpoint = null
            )
        }
        return state
    }

    fun undo(state: HistoryState): HistoryState {
        if (state.checkpoint != null) {
            return HistoryState(
                past = state.past,
                present = state.checkpoint,
                future = state.future + state.present,
                checkpoint = null
            )
        }
        if (state.past.isEmpty()) return state
        val previous = state.past.last()
        return HistoryState(
            past = state.past.dropLast(1),
            present = previous,
            future = state.future + state.present,
            checkpoint = null
        )
    }

    fun redo(state: HistoryState): HistoryState {
        if (state.future.isEmpty()) return state
        val next = state.future.last()
        return HistoryState(
            past = pushPast(state.past, state.present),
            present = next,
            future = state.future.dropLast(1),
            checkpoint = null
        )
    }

    fun loadDocument(project: LetterheadProject): HistoryState {
        return HistoryState(present = project)
    }
}
