import React, { useState } from 'react';
import { MonitorDown } from 'lucide-react';
import { usePWAInstall } from '../hooks/usePWAInstall';

interface PWAInstallButtonProps {
  compact?: boolean;
}

export const PWAInstallButton: React.FC<PWAInstallButtonProps> = ({ compact = false }) => {
  const { isInstallable, isInstalled, isIOS, install } = usePWAInstall();
  const [showIOSGuide, setShowIOSGuide] = useState(false);

  if (isInstalled) {
    return null;
  }

  if (isInstallable) {
    return (
      <button
        type="button"
        onClick={install}
        title="Install Grashy Studio as a Windows / Desktop App"
        aria-label="Install app"
        className={`flex items-center gap-1.5 rounded-md border border-slate-200 text-xs font-medium text-slate-700 bg-white hover:bg-slate-50 whitespace-nowrap shrink-0 transition-colors ${
          compact ? 'px-2.5 py-1.5' : 'p-1.5 sm:px-2.5 sm:py-1.5'
        }`}
      >
        <MonitorDown className="w-3.5 h-3.5 text-[#C45911]" />
        <span className={compact ? '' : 'hidden xl:inline'}>Install App</span>
      </button>
    );
  }

  if (isIOS) {
    return (
      <>
        <button
          type="button"
          onClick={() => setShowIOSGuide(true)}
          aria-label="Install on iOS"
          className="flex items-center gap-1.5 p-1.5 sm:px-2.5 sm:py-1.5 rounded-md border border-slate-200 text-xs font-medium text-slate-700 bg-white hover:bg-slate-50 whitespace-nowrap shrink-0 transition-colors"
        >
          <MonitorDown className="w-3.5 h-3.5 text-[#C45911]" />
          <span className="hidden xl:inline">Install</span>
        </button>

        {showIOSGuide && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4">
            <div className="w-full max-w-sm rounded-xl bg-white p-5 shadow-xl border border-slate-200">
              <h3 className="text-sm font-semibold text-slate-900">
                Install Grashy Studio on iPhone / iPad
              </h3>
              <p className="mt-2 text-xs leading-relaxed text-slate-600">
                1. Tap the <strong>Share</strong> button in Safari&apos;s toolbar.
                <br />
                2. Scroll down and tap <strong>Add to Home Screen</strong> for full offline access.
              </p>
              <button
                type="button"
                onClick={() => setShowIOSGuide(false)}
                className="mt-4 w-full rounded-md bg-slate-900 py-2 text-xs font-semibold text-white hover:bg-slate-800"
              >
                Close
              </button>
            </div>
          </div>
        )}
      </>
    );
  }

  return null;
};
