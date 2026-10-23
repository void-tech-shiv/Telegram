# GLACIA Full Telegram Client — Experimental Source Port

Based on the GPL-2.0 Cherrygram fork (arsLan4k1390/Cherrygram), itself
based on Telegram Android (DrKLO/Telegram). The LICENSE and original
copyright notices are preserved. This branch is not a verified build.

The app name/icon/package and basic texts are GLACIA, but remaining
promotions, update/donation/telemetry paths and anti-tamper logic still
require an independent source review before installing/logging in.
No APK, telemetry endpoint or production release has been created.

IMPORTANT: This is an independent Telegram Android codebase; the former
GLACIA Compose/TDLib app and its session data are not automatically
migrated. Do not uninstall the working app.

Build prerequisites: Android Studio 2025.1.4; SDK 36;
NDK 27.2.12479018; native submodules, app-owned API credentials and
Firebase/signing configuration. Do not reuse upstream third-party secrets.
