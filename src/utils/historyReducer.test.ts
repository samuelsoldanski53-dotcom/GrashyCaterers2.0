import test from 'node:test';
import assert from 'node:assert/strict';
import { createRedesignedGrashyProject } from '../data/grashyInitialProject';
import { createInitialHistoryState, historyReducer } from './historyReducer';

test('COMMIT records undo step and clears redo stack', () => {
  const initial = createRedesignedGrashyProject();
  let state = createInitialHistoryState(initial);

  state = historyReducer(state, {
    type: 'COMMIT',
    updater: (prev) => ({
      ...prev,
      formData: { ...prev.formData, clientName: 'Acme Corp' },
    }),
  });

  assert.equal(state.present.formData.clientName, 'Acme Corp');
  assert.equal(state.past.length, 1);
  assert.equal(state.past[0].formData.clientName, initial.formData.clientName);
  assert.equal(state.future.length, 0);

  state = historyReducer(state, { type: 'UNDO' });
  assert.equal(state.present.formData.clientName, initial.formData.clientName);
  assert.equal(state.future.length, 1);

  state = historyReducer(state, { type: 'REDO' });
  assert.equal(state.present.formData.clientName, 'Acme Corp');
  assert.equal(state.future.length, 0);
});

test('LIVE_UPDATE burst captures single checkpoint that Undo reverts cleanly across both elements and formData', () => {
  const initial = createRedesignedGrashyProject();
  let state = createInitialHistoryState(initial);

  // Typing burst in Form Generator or inline text edit
  state = historyReducer(state, {
    type: 'LIVE_UPDATE',
    captureCheckpoint: true,
    updater: (prev) => ({
      ...prev,
      formData: { ...prev.formData, clientName: 'H' },
    }),
  });
  state = historyReducer(state, {
    type: 'LIVE_UPDATE',
    captureCheckpoint: true,
    updater: (prev) => ({
      ...prev,
      formData: { ...prev.formData, clientName: 'Horizon' },
    }),
  });

  assert.ok(state.checkpoint !== null);
  assert.equal(state.present.formData.clientName, 'Horizon');
  assert.equal(state.past.length, 0);

  // Clicking Undo right after live typing reverts to exact initial checkpoint
  state = historyReducer(state, { type: 'UNDO' });
  assert.equal(state.present.formData.clientName, initial.formData.clientName);
  assert.equal(state.checkpoint, null);
  assert.equal(state.future.length, 1);

  // Redo restores typed value
  state = historyReducer(state, { type: 'REDO' });
  assert.equal(state.present.formData.clientName, 'Horizon');
});

test('LIVE_UPDATE followed by COMMIT (e.g. Generate Document) preserves pre-typing baseline as undo step', () => {
  const initial = createRedesignedGrashyProject();
  let state = createInitialHistoryState(initial);

  state = historyReducer(state, {
    type: 'LIVE_UPDATE',
    captureCheckpoint: true,
    updater: (prev) => ({
      ...prev,
      formData: { ...prev.formData, clientName: 'Safari Lodge' },
    }),
  });

  state = historyReducer(state, {
    type: 'COMMIT',
    updater: (prev) => prev,
  });

  assert.equal(state.checkpoint, null);
  assert.equal(state.past.length, 1);
  assert.equal(state.past[0].formData.clientName, initial.formData.clientName);

  state = historyReducer(state, { type: 'UNDO' });
  assert.equal(state.present.formData.clientName, initial.formData.clientName);
});

test('FINALIZE_CHECKPOINT commits live drag or inline-edit burst into past', () => {
  const initial = createRedesignedGrashyProject();
  let state = createInitialHistoryState(initial);

  state = historyReducer(state, {
    type: 'LIVE_UPDATE',
    captureCheckpoint: true,
    updater: (prev) => ({
      ...prev,
      elements: prev.elements.map((el, idx) =>
        idx === 0 ? { ...el, x: el.x + 12 } : el
      ),
    }),
  });

  state = historyReducer(state, { type: 'FINALIZE_CHECKPOINT' });
  assert.equal(state.checkpoint, null);
  assert.equal(state.past.length, 1);
  assert.equal(state.present.elements[0].x, initial.elements[0].x + 12);

  state = historyReducer(state, { type: 'UNDO' });
  assert.equal(state.present.elements[0].x, initial.elements[0].x);
});
