# GLACIA — full Telegram Android architecture (experimental)

## Scope and sources

This is **not another Compose imitation of the reference UI**. It is a
separately imported copy of the complete upstream
[Cherrygram source](https://github.com/arsLan4k1390/Cherrygram) at commit
`2cad8055bf9cb633da60d76786ddebdd9e35499d` (2026-10-01),
derived from Telegram's Android application.

**License**: GPL-2.0, whose complete text and preexisting authors'
copyright notices must remain intact, even though the customer-facing
app name, icon, and promotions become GLACIA. Source attribution is not
an advertisement; deleting original notices is not permitted.

### Isolated branches

- `feature/glacia-full-telegram-bootstrap`: import automation/audit script
- `feature/glacia-full-source`: source port created automatically by the
  *GLACIA Full Telegram Source Import* workflow when it completes
- Existing `main` and all GLACIA Compose/TDLib branches stay unchanged

The imported branch contains an entirely different Android project and Git
history. **Never merge it directly into the lightweight GLACIA branch.**
Treat it as a new app. Its own independent application ID is
`dev.glacia.fullclient`; Telegram login data from the prior app is not
silently copied or migrated.

## User-facing identity: first pass

- `CG_AppName` and other localized visible strings: GLACIA
- APK filenames: GLACIA
- Launcher artwork: new minimal ice-crystal vector mark
- Basic auto-follow/review popups for the upstream channel: disabled
- Former app channel/store links: neutral Telegram help page (no new fake
  GLACIA support channels)
- **Still to audit**: dynamic icons, About/support screens, preference names,
  donation/marketplace, hardcoded links, privacy policy and FCM routing.
- Internal Kotlin/Java classes/resources and copyright notices retain
  upstream identifiers until a safe, build-tested refactor is possible.

## Important security and build blockers

The upstream contains direct `KotlinFragmentsManager.nfweioufwehr117`
integrity/guard calls, `DonatesManager.startAutoRefresh`, and
`Extra.TG_BLOCKED_URL` network-loading code. **The original
`AutoBackupUserAgent.woifwfwif4343` and
`KotlinFragmentsManager.iooewwfueuewu121` can call Android's
`clearApplicationUserData()` or logout after user-ID matches against an
upstream-configured list/message.** For the isolated GLACIA source snapshot,
we intentionally short-circuit both entrypoints, donor auto-refresh,
remote blocked-account message retrieval and brand-check restart hook.
This is a mitigation, **not a complete security audit**: other callbacks,
permission flows and network endpoints still need review. No APK is
built or installed and no Telegram account is logged into by this import.

The upstream build documentation requires uncommitted `Extra.kt`,
`secrets_for_ci.env`, app-owned Google Firebase configuration,
signing keystore and native submodules. None are imported automatically
from somebody else's setup. Never put actual API credentials or phone
login/session data in Git.

Hardware requirements: Android Studio 2025.1.4, JDK 17, Android SDK 36,
NDK 27.2.12479018, CMake, disk space for large native submodules.

## Windows PowerShell — clone separate project (only after source import succeeds)

```powershell
cd D:\
git clone --branch feature/glacia-full-source --single-branch https://github.com/void-tech-shiv/Telegram.git GLACIA-Full
cd D:\GLACIA-Full
git submodule update --init --recursive --depth=1
```

For subsequent pull:

```powershell
cd D:\GLACIA-Full
git fetch origin
git switch feature/glacia-full-source
git pull --ff-only origin feature/glacia-full-source
git submodule update --init --recursive --depth=1
```

Open `D:\GLACIA-Full` as a separate project in Android Studio.
**Do not install or log into any resulting APK until the review flags
in `GLACIA_SOURCE_REVIEW.json` are investigated.** Do not delete existing
`D:\telegram\Telegram` or uninstall its running GLACIA APK.

## Acceptance criteria before this is called GLACIA

- Upstream application buildable from complete source with own credentials
- All in-app app title, icons, dialogs, settings labels and notification
  identity show GLACIA, including different locales/themes
- No automatic old-project subscription prompts, donations/ads, rate prompts,
  old-domain network calls or third-party analytics/credential routing
- No original-app hardcoded updates, old Google services config, signing keys
- Complete open-source attribution, copyright and GPL compliance
- Real Android phone tests: chats/media, stickers, stories, contacts, calls,
  multi-account and background notifications with security review
- Proper release signing and privacy policy under GLACIA identity

Source import **is not the same thing as a tested, rebranded release**.
