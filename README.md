<div align="center">
  <h1>RecorderX</h1>
  <p>Professional high-performance screen recording for Android</p>
  <img src="metadata/images/icon.png" width="128" height="128" />
  <br><br>
  
  [![Latest Version](https://img.shields.io/badge/Version-v1.1.0-9575CD?style=flat-square)](https://github.com/snap24/RecorderX/releases)
  [![License](https://img.shields.io/badge/License-Apache_2.0-blue?style=flat-square)](LICENSE)
  [![Platform](https://img.shields.io/badge/Platform-Android_10+-brightgreen?style=flat-square)](https://developer.android.com)
</div>

---

RecorderX is a specialized, privacy-focused tool designed for high-fidelity screen capture. It is optimized for devices with high-refresh-rate displays and AMOLED screens, providing near-lossless output with minimal system overhead.

## Versions

- **v1.1.0 (Latest):** 4K/120FPS Support, AMOLED Lavender UI, and Live Thumbnail Notifications.
- **v1.0.0:** Initial stable release with H.264/HEVC support.

## Features

- **High Resolution Capture:** Support for 4K (UHD), 2K (QHD), and standard definitions.
- **Enhanced Framerates:** Native support for 90 FPS and 120 FPS recording modes.
- **Advanced Codecs:** Integrated support for H.264 (AVC), H.265 (HEVC), and AV1.
- **Audio Management:** Simultaneous capture of Microphone and System audio with automated recovery logic.
- **AMOLED Interface:** High-contrast Dark and Lavender theme with precision-aligned controls.
- **Post-Capture Feedback:** Automated thumbnail generation and system notification on session completion.
- **Security:** Operates entirely offline with no internet permissions or external data transmission.

<details>
<summary><b>Interface Gallery</b></summary>
<br>
<div align="center">
  <img src="metadata/en-US/images/phoneScreenshots/1.jpeg" width="200" />
  <img src="metadata/en-US/images/phoneScreenshots/2.jpeg" width="200" />
  <img src="metadata/en-US/images/phoneScreenshots/3.jpeg" width="200" />
  <img src="metadata/en-US/images/phoneScreenshots/4.jpeg" width="200" />
</div>
</details>

## Technical Configuration

- **Video Bitrate:** Configurable up to 40 Mbps (CBR/VBR support).
- **Audio Fidelity:** Adjustable sample rates from 64kbps to 320kbps.
- **Storage Path:** All recordings are stored locally in `/Movies/RecorderX`.
- **Naming Conventions:** Support for custom filename templates using date and timestamp variables.

## Build Requirements

1. **Clone:** `git clone https://github.com/snap24/RecorderX.git`
2. **Environment:** Android Studio Koala+, JDK 17.
3. **Target:** Minimum SDK 29 (Android 10).
4. **Execution:** Use `./gradlew assembleRelease` for optimized production binaries.

## License

Distributed under the Apache License 2.0. See `LICENSE` for further information.

---
<div align="center">
  Maintained by snap24
</div>
