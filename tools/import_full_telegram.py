#!/usr/bin/env python3
"""Create an experimental GLACIA full-source branch. Never produces an APK.
Original copyright, GPL-2.0 and code symbols are retained.
"""
import json, re, sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()
assert (root/"LICENSE").is_file(), "Missing GPL-2.0 LICENSE"
assert (root/"TMessagesProj"/"build.gradle").is_file(), "Unexpected project"
changed = []

def save(p, value):
    if p.read_text(encoding="utf8") != value:
        p.write_text(value, encoding="utf8")
        changed.append(str(p.relative_to(root)))

props = root/"gradle.properties"
s = props.read_text(encoding="utf8")
assert "APP_PACKAGE=uz.unnarsx.cherrygram" in s, "Upstream layout changed, review it"
save(props, s.replace("APP_PACKAGE=uz.unnarsx.cherrygram",
                     "APP_PACKAGE=dev.glacia.fullclient"))

# Only translated *visible text*; do not rewrite XML identifiers or legal comments.
for tree in [root/"TMessagesProj/src/main/res-cherrygram",
             root/"TMessagesProj/src/main/res"]:
    if not tree.exists(): continue
    for p in tree.glob("values*/*.xml"):
        src = p.read_text(encoding="utf8")
        new = re.sub(r"(?<=>)[^<]+(?=<)",
                     lambda m: re.sub("Cherrygram", "GLACIA", m.group(),
                                      flags=re.IGNORECASE), src)
        new = re.sub(r"(?i)https://t\.me/cherrygram[a-z]*",
                     "https://telegram.org/faq", new)
        save(p, new)

# Distinct GLACIA app mark instead of the old cherry icon.
icon = root/"TMessagesProj/src/main/res-cherrygram/drawable/glacia_mark.xml"
icon.write_text('''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
 android:width="108dp" android:height="108dp"
 android:viewportWidth="108" android:viewportHeight="108">
 <path android:fillColor="#10213D" android:pathData="M0,0h108v108h-108z"/>
 <path android:strokeColor="#9DE7FF" android:strokeWidth="5"
  android:strokeLineCap="round"
  android:pathData="M54,18v72 M18,54h72 M28,28l52,52 M80,28l-52,52"/>
</vector>
''', encoding="utf8")
changed.append(str(icon.relative_to(root)))
for tree in [root/"TMessagesProj/config", root/"TMessagesProj/src/main"]:
    for manifest in tree.rglob("AndroidManifest*.xml"):
        src = manifest.read_text(encoding="utf8")
        save(manifest, re.sub(r'@mipmap/icon_launcher_cherry[^"\s]*',
                              '@drawable/glacia_mark', src))

# Disabled: forced subscription suggestion and unsolicited original-store rating.
extras = root/"TMessagesProj/src/main/java/uz/unnarsx/cherrygram/misc/CherrygramExtras.kt"
if extras.exists():
    text = extras.read_text(encoding="utf8")
    text = text.replace(
        "fun checkChannelFollow(activity: Activity, currentAccount: Int) = AndroidUtilities.runOnUIThread {",
        "fun checkChannelFollow(activity: Activity, currentAccount: Int) = AndroidUtilities.runOnUIThread {\n        return@runOnUIThread // GLACIA: no automatic subscription solicitations")
    text = text.replace("fun shouldCheckFollow(fragment: BaseFragment): Boolean {",
                        "fun shouldCheckFollow(fragment: BaseFragment): Boolean {\n        return false // GLACIA")
    text = text.replace("fun requestReviewFlow(fragment: BaseFragment?) {",
                        "fun requestReviewFlow(fragment: BaseFragment?) {\n        return // No original app store review prompt")
    save(extras, text)

# Replace original remote promo/support destinations. Never invent a GLACIA support group.
constants = root/"TMessagesProj/src/main/java/uz/unnarsx/cherrygram/misc/Constants.kt"
if constants.exists():
    text=constants.read_text(encoding="utf8")
    for destination in [
        "https://t.me/cherrygram", "https://t.me/CherrygramAPKs",
        "https://t.me/CherrygramBetaAPKs", "https://t.me/CherrygramSupport",
        "https://arslan4k1390.github.io/cherrygram/privacy",
        "https://arslan4k1390.github.io/cherrygram/donation-terms",
        "https://crowdin.com/project/cherrygram",
        "https://play.google.com/store/apps/details?id=uz.unnarsx.cherrygram"
    ]: text=text.replace(destination,"https://telegram.org/faq")
    text=text.replace("https://github.com/arsLan4k1390/Cherrygram",
                      "https://github.com/void-tech-shiv/Telegram")
    save(constants,text)

# APK artifact filenames, without touching package symbols/source copyrights.
for folder in ["TMessagesProj_App", "TMessagesProj_AppHuawei",
               "TMessagesProj_AppStandalone", "TMessagesProj_AppHockeyApp"]:
    p=root/folder/"build.gradle"
    if p.exists():
        save(p,p.read_text(encoding="utf8").replace("Cherrygram-","GLACIA-"))

# Do not re-publish old project's Firebase config or signing keys.
removed=[]
for pat in ["google-services.json", "*.jks", "*.keystore"]:
    for p in root.rglob(pat):
        removed.append(str(p.relative_to(root)))
        p.unlink()

# Kill-switch and remote data-wipe paths are not safe to inherit unreviewed.
# Retain original source for auditing, but make callable entrypoints no-ops.
security_patches = [
    ("TMessagesProj/src/main/java/org/telegram/messenger/AutoBackupUserAgent.kt",
     [("suspend fun checkLoggedAccountsInstances() {",
       "suspend fun checkLoggedAccountsInstances() {\n        return // GLACIA SECURITY: disable upstream donor/account enforcement"),
      ("suspend fun woifwfwif4343(messageObject: MessageObject?) {",
       "suspend fun woifwfwif4343(messageObject: MessageObject?) {\n        return // GLACIA SECURITY: disable remote-triggered app data deletion")]),
    ("TMessagesProj/src/main/java/org/telegram/messenger/KotlinFragmentsManager.kt",
     [("suspend fun iooewwfueuewu121(messageObject: MessageObject?) {",
       "suspend fun iooewwfueuewu121(messageObject: MessageObject?) {\n        return // GLACIA SECURITY: disable remote-controlled data wipe"),
      ("fun vreg42r2r2r1r3q1rq3(input: String): Boolean {",
       "fun vreg42r2r2r1r3q1rq3(input: String): Boolean {\n        return false // GLACIA: remove brand-protection restart trigger")]),
    ("TMessagesProj/src/main/java/uz/unnarsx/cherrygram/core/configs/CherrygramCoreConfig.kt",
     [("DonatesManager.startAutoRefresh(ApplicationLoader.applicationContext, force = false, fromIntegrityChecker = false)",
       "// GLACIA: disable remote donor refresh pending independent review"),
      ("        loadMessages()",
       "        // GLACIA: remote account-check message loader disabled")]),
]
for relative, patches in security_patches:
    p = root / relative
    assert p.is_file(), f"Security-critical source missing: {relative}"
    code = p.read_text(encoding="utf8")
    for old, new in patches:
        assert old in code, f"Security-critical upstream code changed, refusing import: {relative}"
        code = code.replace(old, new, 1)
    save(p, code)

# The original LICENSE, code copyright and upstream acknowledgements stay available.
(root/"README.md").write_text("""# GLACIA Full Telegram Client — Experimental Source Port

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
""",encoding="utf8")
changed.append("README.md")
flags=[]
srcroot=root/"TMessagesProj/src/main/java/uz/unnarsx/cherrygram"
for p in srcroot.rglob("*"):
    if p.suffix not in {".kt",".java"}: continue
    text=p.read_text(encoding="utf8",errors="replace")
    for pattern in [r"KotlinFragmentsManager\.nfweioufwehr117",
                    r"DonatesManager\.startAutoRefresh",
                    r"Extra\.TG_BLOCKED_URL",
                    r"t\.me/[Cc]herrygram",r"[Cc]herrygramSupport"]:
        for m in re.finditer(pattern,text):
            flags.append({"path":str(p.relative_to(root)),
                          "line":text.count("\n",0,m.start())+1,"pattern":pattern})
(root/"GLACIA_SOURCE_REVIEW.json").write_text(
    json.dumps({"app_id":"dev.glacia.fullclient",
                "licensing":"GPL-2.0 notices preserved",
                "source_audit_required":True,
                "remote_account_data_wipe_entrypoints_disabled":True,
                "changes":len(changed),"removed_config":removed,
                "findings":flags[:300],"finding_count":len(flags)},indent=2)+"\n",
    encoding="utf8")
print(f"GLACIA experimental port prepared; {len(changed)} modified files; "
      f"{len(flags)} manual-review flags. NEVER INSTALL BEFORE SECURITY AUDIT.")
