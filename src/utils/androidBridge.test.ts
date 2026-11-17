import test from 'node:test';
import assert from 'node:assert/strict';
import {
  resolveAndroidBackAction,
  sanitizeExportFilename,
  uint8ArrayToBase64,
} from './androidBridge';

test('sanitizeExportFilename strips unsafe path characters and preserves extension', () => {
  assert.equal(
    sanitizeExportFilename('Sunculture Kenya / Quote:2026*.pdf'),
    'Sunculture-Kenya-Quote-2026-.pdf'
  );
  assert.equal(sanitizeExportFilename('///'), 'grashy-document.bin');
});

test('uint8ArrayToBase64 encodes binary payloads deterministically', () => {
  const bytes = new TextEncoder().encode('Grashy PDF');
  assert.equal(uint8ArrayToBase64(bytes), 'R3Jhc2h5IFBERg==');
});

test('resolveAndroidBackAction prioritizes modals, drawers, selection, preview, then exit', () => {
  assert.equal(
    resolveAndroidBackAction({
      syncModalOpen: true,
      mobileDrawer: 'left',
      editorMode: 'visual',
      mobileFormPane: 'preview',
      selectedId: 'el-1',
    }),
    'close-sync-modal'
  );

  assert.equal(
    resolveAndroidBackAction({
      syncModalOpen: false,
      mobileDrawer: 'right',
      editorMode: 'visual',
      mobileFormPane: 'form',
      selectedId: 'el-1',
    }),
    'close-drawer'
  );

  assert.equal(
    resolveAndroidBackAction({
      syncModalOpen: false,
      mobileDrawer: 'none',
      editorMode: 'visual',
      mobileFormPane: 'form',
      selectedId: 'el-1',
    }),
    'clear-selection'
  );

  assert.equal(
    resolveAndroidBackAction({
      syncModalOpen: false,
      mobileDrawer: 'none',
      editorMode: 'form',
      mobileFormPane: 'preview',
      selectedId: null,
    }),
    'back-to-form'
  );

  assert.equal(
    resolveAndroidBackAction({
      syncModalOpen: false,
      mobileDrawer: 'none',
      editorMode: 'form',
      mobileFormPane: 'form',
      selectedId: null,
    }),
    'exit-app'
  );
});
