import { Capacitor } from '@capacitor/core';
import { App as CapApp } from '@capacitor/app';
import { Directory, Filesystem } from '@capacitor/filesystem';
import { Share } from '@capacitor/share';
import { StatusBar, Style } from '@capacitor/status-bar';
import type { EditorMode } from '../types/letterhead';

export interface AndroidBackState {
  syncModalOpen: boolean;
  mobileDrawer: 'none' | 'left' | 'right';
  editorMode: EditorMode;
  mobileFormPane: 'form' | 'preview';
  selectedId: string | null;
}

export type AndroidBackAction =
  | 'close-sync-modal'
  | 'close-drawer'
  | 'clear-selection'
  | 'back-to-form'
  | 'exit-app';

export function resolveAndroidBackAction(state: AndroidBackState): AndroidBackAction {
  if (state.syncModalOpen) return 'close-sync-modal';
  if (state.mobileDrawer !== 'none') return 'close-drawer';
  if (state.editorMode === 'visual' && state.selectedId !== null) return 'clear-selection';
  if (state.editorMode === 'form' && state.mobileFormPane === 'preview') return 'back-to-form';
  return 'exit-app';
}

export function sanitizeExportFilename(filename: string): string {
  const cleaned = filename
    .replace(/[/\\?%*:|"<>]/g, '-')
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '');
  return cleaned || 'grashy-document.bin';
}

export function uint8ArrayToBase64(bytes: Uint8Array): string {
  if (typeof Buffer !== 'undefined') {
    return Buffer.from(bytes).toString('base64');
  }
  let binary = '';
  const chunkSize = 0x8000;
  for (let i = 0; i < bytes.length; i += chunkSize) {
    const chunk = bytes.subarray(i, i + chunkSize);
    binary += String.fromCharCode(...chunk);
  }
  return btoa(binary);
}

export function isNativePlatform(): boolean {
  try {
    return Capacitor.isNativePlatform();
  } catch {
    return false;
  }
}

export function isNativeAndroidPlatform(): boolean {
  try {
    return Capacitor.isNativePlatform() && Capacitor.getPlatform() === 'android';
  } catch {
    return false;
  }
}

export function triggerWebDownload(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export async function saveOrShareBlobOnDevice(
  blob: Blob,
  filename: string,
  dialogTitle = 'Save or Share Document'
): Promise<'native-shared' | 'web-downloaded'> {
  const safeName = sanitizeExportFilename(filename);

  if (!isNativePlatform()) {
    triggerWebDownload(blob, safeName);
    return 'web-downloaded';
  }

  const buffer = await blob.arrayBuffer();
  const base64 = uint8ArrayToBase64(new Uint8Array(buffer));
  const cachePath = `exports/${safeName}`;

  await Filesystem.writeFile({
    path: cachePath,
    data: base64,
    directory: Directory.Cache,
    recursive: true,
  });

  // Also attempt saving an accessible copy to Documents/Grashy Studio without failing if scoped storage restricts
  try {
    await Filesystem.writeFile({
      path: `Grashy Studio/${safeName}`,
      data: base64,
      directory: Directory.Documents,
      recursive: true,
    });
  } catch {
    // Cache + Share URI still succeeds across Android 10-15 scoped storage
  }

  const uriResult = await Filesystem.getUri({
    path: cachePath,
    directory: Directory.Cache,
  });

  await Share.share({
    title: safeName,
    text: `Grashy Studio document: ${safeName}`,
    url: uriResult.uri,
    dialogTitle,
  });

  return 'native-shared';
}

export async function configureAndroidShell(): Promise<void> {
  if (!isNativeAndroidPlatform()) return;
  try {
    await StatusBar.setOverlaysWebView({ overlay: false });
    await StatusBar.setBackgroundColor({ color: '#C45911' });
    await StatusBar.setStyle({ style: Style.Dark });
  } catch {
    // Non-fatal on older WebViews or emulators
  }
}

export function registerAndroidBackHandler(
  getState: () => AndroidBackState,
  onAction: (action: Exclude<AndroidBackAction, 'exit-app'>) => void
): () => void {
  if (!isNativeAndroidPlatform()) {
    return () => {};
  }

  let removed = false;
  let handle: { remove: () => Promise<void> } | null = null;

  void CapApp.addListener('backButton', () => {
    const action = resolveAndroidBackAction(getState());
    if (action === 'exit-app') {
      void CapApp.exitApp();
      return;
    }
    onAction(action);
  }).then((listener) => {
    if (removed) {
      void listener.remove();
    } else {
      handle = listener;
    }
  });

  return () => {
    removed = true;
    if (handle) {
      void handle.remove();
    }
  };
}
