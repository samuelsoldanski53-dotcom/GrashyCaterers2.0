import { LetterheadElement, LetterheadProject } from '../types/letterhead';

export const MAX_HISTORY_STEPS = 40;

export interface HistoryState {
  past: LetterheadProject[];
  present: LetterheadProject;
  future: LetterheadProject[];
  checkpoint: LetterheadProject | null;
}

export type HistoryAction =
  | {
      type: 'COMMIT';
      updater: (prev: LetterheadProject) => LetterheadProject;
      skipIfUnchanged?: boolean;
    }
  | {
      type: 'LIVE_UPDATE';
      updater: (prev: LetterheadProject) => LetterheadProject;
      captureCheckpoint?: boolean;
    }
  | {
      type: 'FINALIZE_CHECKPOINT';
      fallbackElements?: LetterheadElement[];
    }
  | { type: 'UNDO' }
  | { type: 'REDO' }
  | { type: 'LOAD_DOCUMENT'; project: LetterheadProject };

export function createInitialHistoryState(
  initialProject: LetterheadProject
): HistoryState {
  return {
    past: [],
    present: initialProject,
    future: [],
    checkpoint: null,
  };
}

function pushPast(past: LetterheadProject[], entry: LetterheadProject): LetterheadProject[] {
  const next = [...past, entry];
  return next.length > MAX_HISTORY_STEPS
    ? next.slice(next.length - MAX_HISTORY_STEPS)
    : next;
}

export function historyReducer(
  state: HistoryState,
  action: HistoryAction
): HistoryState {
  switch (action.type) {
    case 'COMMIT': {
      const updated = action.updater(state.present);
      const nextPresent: LetterheadProject = {
        ...updated,
        updatedAt: new Date().toISOString(),
      };
      const undoBaseline = state.checkpoint ?? state.present;

      if (
        action.skipIfUnchanged &&
        state.checkpoint === null &&
        updated === state.present
      ) {
        return state;
      }

      return {
        past: pushPast(state.past, undoBaseline),
        present: nextPresent,
        future: [],
        checkpoint: null,
      };
    }

    case 'LIVE_UPDATE': {
      const updated = action.updater(state.present);
      const nextPresent: LetterheadProject = {
        ...updated,
        updatedAt: new Date().toISOString(),
      };
      const nextCheckpoint =
        action.captureCheckpoint && state.checkpoint === null
          ? state.present
          : state.checkpoint;

      return {
        past: state.past,
        present: nextPresent,
        future: action.captureCheckpoint ? [] : state.future,
        checkpoint: nextCheckpoint,
      };
    }

    case 'FINALIZE_CHECKPOINT': {
      if (state.checkpoint !== null) {
        return {
          past: pushPast(state.past, state.checkpoint),
          present: state.present,
          future: [],
          checkpoint: null,
        };
      }
      if (action.fallbackElements) {
        return {
          past: pushPast(state.past, {
            ...state.present,
            elements: action.fallbackElements,
          }),
          present: state.present,
          future: [],
          checkpoint: null,
        };
      }
      return state;
    }

    case 'UNDO': {
      if (state.checkpoint !== null) {
        return {
          past: state.past,
          present: state.checkpoint,
          future: [...state.future, state.present],
          checkpoint: null,
        };
      }
      if (state.past.length === 0) return state;
      const previous = state.past[state.past.length - 1];
      return {
        past: state.past.slice(0, -1),
        present: previous,
        future: [...state.future, state.present],
        checkpoint: null,
      };
    }

    case 'REDO': {
      if (state.future.length === 0) return state;
      const next = state.future[state.future.length - 1];
      return {
        past: pushPast(state.past, state.present),
        present: next,
        future: state.future.slice(0, -1),
        checkpoint: null,
      };
    }

    case 'LOAD_DOCUMENT': {
      return createInitialHistoryState(action.project);
    }

    default:
      return state;
  }
}
