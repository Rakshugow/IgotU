# 🛡️ IgotU — Hardware-Level GPS & Anti-Spoof Sentinel for Android

![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20%28API%2026--35%29-00F2FE?style=for-the-badge&logo=android)
![Language](https://img.shields.io/badge/Language-Java%20%2F%20Android%20SDK-10B981?style=for-the-badge&logo=openjdk)
![Security](https://img.shields.io/badge/Security-Anti--Mock%20%7C%20Root%20%7C%20LSPosed-EF4444?style=for-the-badge&logo=shield)
![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)

**IgotU** is a security-grade Android application and background sentinel designed to detect, log, and neutralize fake GPS spoofers, mock location providers, and stealth bypass frameworks (Root / Magisk / LSPosed) in real time.

---

## 🌟 Why IgotU?

Standard mobile applications and location-based services (delivery fleets, ride-hailing apps, attendance systems, and navigation tools) are often deceived by Play Store Fake GPS apps or stealth root hooks. 

I developed **IgotU** to solve this problem by pairing **hardware-level sensor verification** with a **24/7 background sentinel**, **anti-bypass detection**, and an **incident audit logger**.

---

## ⚡ Key Features

### 1. 🔍 Native Android 15 `isMock()` Hardware Detection
- Directly inspects Android's native location engine (`location.isMock()` on Android 12–15 and `location.isFromMockProvider()` on legacy versions).
- Flags injected mock coordinates in under 100 milliseconds.

### 2. 🛡️ Advanced Anti-Bypass Shield (Stealth Root & Hook Detection)
- **Root & Magisk Detection:** Inspects system binaries (`/system/bin/su`, `/sbin/su`, `/data/local/su`), kernel build tags (`test-keys`), and root manager packages.
- **Hook Framework Interception:** Detects active Xposed, LSPosed, and Frida dynamic instrumentation hooks attempting to patch Android's `LocationManager` class.

### 3. ⏱️ 24/7 Background Sentinel
- Operates as an ultra-low-power Foreground Service guarding the system 24/7.
- **Intelligent Dual-Channel Notifications:**
  - **Clean / Normal State:** Completely silent and invisible (`IMPORTANCE_MIN`), zero notification clutter.
  - **Spoofing Active State:** Immediately triggers a high-priority Heads-Up Notification (`IMPORTANCE_HIGH`) with sound and vibration:
    > ⚠️ *ALERT: Fake GPS Active! Location is currently being spoofed across Google Maps & apps!*
  - Automatically cancels the alert the second fake GPS is stopped.

### 4. 🛑 Automated Process Neutralizer
- Identifies running fake GPS processes requesting `android.permission.ACCESS_MOCK_LOCATION`.
- Calls `ActivityManager.killBackgroundProcesses()` to terminate mock background engines.
- Tears down synthetic test providers from `LocationManager`.
- Quick-action notification button: **[Disable Mock]** allows one-tap deactivation straight from the notification drawer.

### 5. 📋 Incident Audit Logging & Forensic CSV Export
- Persistent local SQLite database (`igotu_incidents.db`) tracking every spoofing event:
  - Timestamp & Date
  - Injected Fake Coordinates vs. Last Known Physical Coordinates
  - Accuracy and Threat Classification
  - Detected Spoofer App Name
- **One-Tap Export to CSV:** Generates `IgotU_Security_Audit_Report.csv` via Android `FileProvider` for sharing to WhatsApp, Email, or Drive.

---

## 🏗️ Project Architecture

```
IgotU-mobile/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml          # Core permissions & service definitions
│   │   ├── java/com/igotu/app/
│   │   │   ├── MainActivity.java        # Cyber HUD UI, real-time telemetry & controls
│   │   │   ├── SentinelService.java     # 24/7 Background location listener & alerts
│   │   │   ├── SecurityEngine.java      # Root binaries & LSPosed/Frida hook detector
│   │   │   ├── IncidentDbHelper.java    # SQLite database & CSV forensic exporter
│   │   │   └── AppNeutralizer.java      # Process termination & test provider cleaner
│   │   └── res/
│   │       ├── layout/activity_main.xml # Modern Cyber Obsidian dark theme
│   │       ├── drawable/                # Custom vector shields, HUD rings & badges
│   │       └── values/                  # Strings, colors, and styles
│   └── build.gradle                     # Android build configuration (compileSdk 35)
├── settings.gradle
├── gradle.properties
└── README.md
```

---

## 🛠️ Tech Stack

- **Target SDK:** Android 15 (API 35)
- **Minimum SDK:** Android 8.0 (API 26)
- **Architecture:** Android Native (Java)
- **UI Framework:** Material Components 3 (Dark Cyber Obsidian Theme)
- **Database:** Native SQLite
- **Build System:** Gradle 8.7 / Android Gradle Plugin 8.4.0

---

## 🚀 Building & Installation

### Prerequisites
- Android SDK with API 35 (`android-35`) and build-tools (`34.0.0` or newer).
- JDK 17 or JDK 21.

### 1. Clone the Repository
```bash
git clone https://github.com/your-username/IgotU.git
cd IgotU
```

### 2. Build the Debug APK
```bash
./gradlew assembleDebug
```
The compiled APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

### 3. Install to Connected Device via ADB
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 4. Launch the Application
```bash
adb shell am start -n com.igotu.app/.MainActivity
```

---

## 🧪 Testing the Detector

1. Open **IgotU** and grant Location & Notification permissions.
2. The HUD will show:  
   🟢 **AUTHENTIC PHYSICAL LOCATION** (Live status: `● SECURED`).
3. Turn on any Fake GPS app or set a mock location in **Developer Options**.
4. The screen will instantly switch to:  
   🔴 **MOCK LOCATION DETECTED!** (Live status: `● THREAT`).
5. Lock your phone or open Google Maps: the **24/7 Sentinel** will alert you in the status bar with the one-tap **[Disable Mock]** action!
6. Tap **"Export CSV"** inside IgotU to generate a detailed forensic log.

---

## 📄 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
