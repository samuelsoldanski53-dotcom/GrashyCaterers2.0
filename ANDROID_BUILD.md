# Grashy Studio — Android App Guide

Grashy Studio is packaged as a native **Capacitor Android** application (`com.grashystudio.app`) with full offline IndexedDB storage, Supabase cloud synchronization, native Android status bar & hardware back button handling, and native Android FileProvider sharing for PDF, DOCX, and JSON exports.

## 1. Sync Web Changes into Android
Whenever you update the React/Vite app or `.env.local` Supabase settings, rebuild and sync the web bundle into `android/app/src/main/assets/public`:

```bash
npm run android:sync
```

## 2. Build an Installable APK Locally (Android Studio / Gradle)
Requirements: **Java 21 (JDK)** and **Android SDK (API 36)** (included with Android Studio).

- Open directly in Android Studio:
  ```bash
  npm run android:open
  ```
  Then choose **Build → Build Bundle(s) / APK(s) → Build APK(s)** (or **Generate Signed Bundle / APK** for Google Play).

- Or build from the command line:
  ```bash
  npm run android:build
  ```
  Output APK: `android/app/build/outputs/apk/debug/app-debug.apk`

## 3. Automated Cloud APK Build (GitHub Actions)
Push this repository to GitHub and run the **Build Android APK** workflow (`.github/workflows/build-android.yml`). Optionally add `VITE_SUPABASE_URL` and `VITE_SUPABASE_ANON_KEY` in GitHub Repository Secrets before running. Download `grashy-studio-debug-apk` from the workflow run artifacts and install it directly on any Android phone.
