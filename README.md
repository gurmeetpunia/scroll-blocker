# 🛡️ ScrollBlocker

An Android Accessibility Service app that **instantly closes YouTube Shorts, Instagram Reels, TikTok, and browser-based short video URLs** the moment you navigate to them — no root required.

---

## How It Works

| Detection | Method |
|---|---|
| YouTube Shorts | Detects "Shorts" nav tab selected via Accessibility API |
| Instagram Reels | Detects "Reels" nav tab selected via Accessibility API |
| TikTok | Closes instantly whenever TikTok is opened |
| Browser Shorts/Reels | Reads address bar URL for `youtube.com/shorts`, `instagram.com/reels`, `tiktok.com` |

When detected → `performGlobalAction(GLOBAL_ACTION_HOME)` → you're back at the home screen.

---

## Requirements

- **Java JDK 17+**
- **Android SDK** with:
  - `platforms;android-34`
  - `build-tools;34.0.0`
- Android phone running **Android 8.0+ (API 26+)**
- `ANDROID_HOME` environment variable set

---

## Build & Install

### Step 1 — Install Android SDK (if you don't have it)

```bash
# Download command-line tools from:
# https://developer.android.com/studio#command-tools

# Extract and set up:
mkdir -p ~/android-sdk/cmdline-tools/latest
unzip commandlinetools-*.zip -d ~/android-sdk/cmdline-tools/latest

# Install required SDK components:
~/android-sdk/cmdline-tools/latest/bin/sdkmanager \
  "platform-tools" \
  "platforms;android-34" \
  "build-tools;34.0.0"

# Set environment variable (add to ~/.bashrc or ~/.zshrc):
export ANDROID_HOME=~/android-sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools
```

### Step 2 — Build the APK

```bash
chmod +x setup.sh
./setup.sh
```

This will:
1. Check your Java and Android SDK setup
2. Download the Gradle wrapper if needed
3. Build the APK at `app/build/outputs/apk/debug/app-debug.apk`

### Step 3 — Install on Your Phone

**Option A — via ADB (USB):**
```bash
# Enable Developer Options on your phone:
# Settings → About Phone → tap "Build Number" 7 times
# Settings → Developer Options → enable USB Debugging

adb install app/build/outputs/apk/debug/app-debug.apk
```

**Option B — Manual transfer:**
Copy the APK to your phone via USB/WhatsApp/Drive, then tap to install.
(You'll need "Install unknown apps" enabled for your file manager.)

---

## First-Time Setup on Phone

1. Open **ScrollBlocker** app
2. Tap **"Open Accessibility Settings"**
3. Find **ScrollBlocker** in the list → tap it → **Enable**
4. The status dot will turn **green** ✅
5. Open YouTube → tap Shorts → you'll be instantly returned home

---

## App Features

- 🔴 **Red dot** = service not active (go enable accessibility permission)
- 🟢 **Green dot** = service active and monitoring
- **Toggle switch** = temporarily pause blocking without disabling the service
- Monitors: YouTube, Instagram, TikTok, Chrome, Firefox, Edge, Opera, Brave

---

## Notes

- The app needs **Accessibility Service permission** to work — this is expected and intentional
- No internet permission is requested — fully offline
- The toggle in the app lets you pause blocking (e.g., for a break) without going back to Accessibility Settings

---

## Project Structure

```
ScrollBlocker/
├── app/src/main/
│   ├── java/com/scrollblocker/app/
│   │   ├── MainActivity.kt               ← App UI + status
│   │   └── BlockerAccessibilityService.kt ← Core detection logic
│   ├── res/
│   │   ├── values/strings.xml
│   │   ├── values/colors.xml
│   │   ├── values/themes.xml
│   │   └── xml/accessibility_service_config.xml
│   └── AndroidManifest.xml
├── setup.sh                              ← Build script
└── README.md
```
