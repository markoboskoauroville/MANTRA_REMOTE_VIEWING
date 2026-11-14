# TAKEOVER: Mantra Remote Viewing (Android)

**State 14.11.2026, v53 (request of 14.11.2026):** a phone (PhoneScreen, Phones.kt) opens on the whole glass: Android's
bars hidden, upright, outside the root's safe-area padding; TOUCH is the default (no pointer arrow); the keys (◀ MAC,
✕, BACK, HOME, APPS, TOUCH/MOUSE, ⌨, status) are a menu over the picture, opened by a finger that lands within 24 dp of
the left or right edge and goes 60 px inward, or by Android's back swipe. A tap at the edge still reaches the phone.

**State 14.11.2026, v52 (request of 14.11.2026):**
- Row 1: **NEW TAB** (label NEW, sub TAB) right after ESC = ⌘T (Ctrl+T on the VM): a new tab in iTerm, Chrome, Safari,
  Finder. Also on the full-screen rail (NEW TAB above NEXT TAB).
- **APPS now brings the app to the front**: after the view moves, `bringToFront` matches the window's app name to the
  Dock Switcher's /apps and calls `/open?id=`; no match → a click on the window's top edge (middle, 6 px down), not in
  VIEW ONLY. The travel line says `i/n APP · IN FRONT` (or `IN FRONT (CLICKED)`).
- **MOUSE redone**: L and R (and click lock, `clickLock`) are gone. With 🖱 on: a tap clicks at the finger, a two-finger
  tap right-clicks at their middle, a moved finger moves the pointer. **MOVE** (pref `mouse_move_only`) = the old aiming
  mode: taps only move the pointer; three-finger tap clicks, two-finger tap right-clicks at the pointer. D (drag) stays.

**State 14.11.2026, v51:** ⚙ on the VM (chromium-vm) has **SERVICES**: the VM's services from vmctl (repo vm-chromium,
tools/vmctl; `GET http://100.102.86.30:8896/services`, `POST /services/NAME/start|stop`, X-Mantra-Token). One row each:
● green running / ○ white stopped, short name (≤ 8 chars, same as the VM status bar) and title; a tap starts (gui=true: a
Pixel opens its mirror on the VM screen) or stops it, the row says STARTING… / STOPPING… meanwhile; core rows (CKBOND,
QUEEN, …) carry 🔒 and take no tap; read again every 5 s while open. Token: VMCTL TOKEN field in the panel (pasted once,
prefs key vmctl_token, shown as ••••; the Mac copy is ~/Downloads/API/chromium-vm-vmctl.txt). Code: VmServices.kt +
ServicesPanel in MainActivity.kt. Built by GitHub Actions only; not yet tried by hand.

**Renamed 13.11.2026:** markoboskoauroville/MANTRA_REMOTE_VNC_TAILSCALE is now **markoboskoauroville/MANTRA_REMOTE_VIEWING** (Marko). Done by Queen.

**Renamed 13.11.2026:** MANTRA REMOTE VNC is now **MANTRA REMOTE VIEWING** (Marko). Done by Queen.

**Renamed 13.11.2026:** Mantra Remote VNC is now **Mantra Remote Viewing** (Marko). Done by Queen.

**State 13.11.2026, v47:** LOGIN no longer types into whatever has the focus. It asks the Mac's Star API
(`POST http://<mac tailscale>:8863/login-dialog {"password"}`, X-Mantra-Token = files/door.token) and Macro Recorder v23
(`login-dialog:` inbox, app/Dialog.swift) finds every password dialog through Accessibility, in front or not (keychain
prompts of SecurityAgent, admin prompts, sheets; never a web page's field), fills it, presses ALWAYS ALLOW, else ALLOW /
OK / UNLOCK, and answers all of them in one tap. The answer shows 6 s on the line above the keys ("LOGIN: ALWAYS ALLOW
PRESSED · 2 DIALOGS", red when one stayed open). No dialog on the Mac (the lock screen) or no answer from the Star API:
typed + Return as before. Needs on the Mac: Macro Recorder v23 installed, STAR_API pulled and `star serve` restarted.

**State 9.11.2026, v42 (replaces v41's lock):** L/R long-pressed = sticky CLICK MODE (`clickLock`, TAP=L/TAP=R, tap the lit key or long-press again to end): one-finger movement moves the ring relatively, a tap (<250 ms, <10 px) = one click at the ring, no jump; D stays the held drag. MONITORS derived as in v41.

**State 9.11.2026, v41 (lock part superseded by v42):** L and R long-pressed LOCK the button down (like D, `held`; label LOCKED): the finger moves the pointer relatively from the ring, no jump, a tap does nothing, a long press on the key releases; click mode (`clickLock`, v39) is gone. MONITORS is derived from the picture size by `layoutMonitors` (Retina at y = fbH-1964: 5584x1964 tops level, 5584x2361 the old 397 offset).

**State 8.11.2026, v40:** in ⚙, ⬇ UPDATE APP (with "NOW v<installed>") and ← BACK TO LOGIN (OTHER MACHINE) stand in one row at the top, under the title with the circled ✕.

**State 8.11.2026, v39:** SCROLL moves the Mac's pointer to the finger first and turns the wheel at the finger (a tick every 20 px), so the window under the finger scrolls. L and R long-pressed are now click MODES (`clickLock`, one at a time, lit "TAP=L"/"TAP=R"): the button goes down at once where the finger lands, follows a moved finger, lets go on lifting (held at least 60 ms); a second finger releases it (zoom/pan). They no longer hold a button down; D is still the drag lock (`held`). Installed on the Pixel 7 and the Nothing (8.11.2026, 12:50); not yet tried by hand.

**State 8.11.2026, v38:** the settings dialog (⚙) redesigned (Marko: "circle with X ... closing the dialogue", "back to login", "button to update the app ... GitHub page"): a circled ✕ at the top right closes it; at the bottom only two buttons, ⬇ UPDATE APP (opens the GitHub latest release page: download, install by hand) and ← BACK TO LOGIN (link closed, first page to pick another machine). QUIT APP, CLOSE MAC and BACK are gone. Built by GitHub Actions only.

**State 8.11.2026, v36 (installed on both phones, tested on the Nothing Phone 2a; Marko: test only on the Nothing):**
DOCK asks the Dock Switcher v15 (`/dock?show=1|0`) to switch the Dock's hiding off, then moves the picture to the bar at
the zoom he has (switching to the BenQ if the bar is there), and the pointer onto the bar (not with VIEW on: view only
sends no pointer). The second press hides the Dock again (only if it was hiding) and gives back the monitor, zoom, picture
and pointer. MONITORS: the MacBook now at 2560,397 (picture 5584x2361). The Pixel 7's app has no Mac password saved.

**State 5.11.2026, v33 (GitHub release, installed on the Pixel 7 and the Nothing Phone 2a):**
- Row 1 (and the full-screen rail on the VM): ⌘TAB replaced by a pure ⌘; on the VM it taps Super alone = the VM dock (v28).
- Machines list: the Pixel 7 and the Nothing Phone know each other from the start (v29); tested Nothing → VM, Nothing →
  Pixel 7, Pixel 7 → Nothing (5.11.2026). Tailscale is no longer switched off when the app closes (v32).
- The macro door (v30, protocol of MANTRA_MANIFEST remote-door.md since v33): port 8841, `/health`, `/api`,
  `/api/state`, `POST /api/run` (Macro Recorder language + Press "WORD" + Back), `/api/shot?w=360`; X-Mantra-Token
  from Tailscale (= ~/.config/mantra-door.token), none via adb forward. Code: MacroDoor.kt. Client: tools/mremote.py
  (`mremote` on the PATH), saved macros in macros/.
- Keep debugging (v30, DebugKeeper.kt): KeeperService watches USB and wireless debugging and switches them back on
  (WRITE_SECURE_SETTINGS, granted by `mremote keep <phone>`, which also hands over the token); restores adb on 5555
  through its own wireless debugging after a reboot; without the permission, a notification opens Developer options.
  Android 17 (Pixel) hides adb_enabled from apps (reads 0 while on): the keeper just writes 1 again, harmless.
- Not yet seen: a real reboot of either phone with the keeper (5555 restore path untested).

---

(history below: state 27.10.2026, v17, named Mantra Remote until v16)

**What it is.** An Android app (Kotlin + Compose, `android/`, package `com.mantra.remote`) that controls Marko's Mac from his
Nothing Phone 2a over the internet. Tailscale carries it (Mac 100.79.251.19, phone 100.67.149.40); the Mac side is Apple
Screen Sharing on port 5900. The RFB client is our own (`Rfb.kt`): ARD auth (type 30) logs in as the Mac user and shows
the real desktop (type 2, the VNC password, only sees the lock screen). Release: v13 on GitHub, built on the Mac.

**Files.**
- `android/app/src/main/java/com/mantra/remote/Rfb.kt`: the protocol, the encodings (ZRLE, Zlib, Hextile, CopyRect, Raw,
  DesktopSize), the colour formats (only 32 bpp and 16 bpp 5-6-5 are accepted by Apple), byte counters, VIEW ONLY,
  the 2 s fast-picture burst after any key or click, `key(…, always)` for keys that pass VIEW ONLY.
- `MainActivity.kt`: the Session (held in `Hold`, so a rebuilt window finds the live link), the screens (portrait, full
  screen with CAMERA-language side rails), gestures, keys, FPS and colour menus, the help popups.
- `LinkService.kt`: a foreground service (specialUse) that keeps the process awake while the app is in the background;
  in the background the picture drops to 0.2 FPS, in front back to the chosen FPS; a link that fell is dialled again on return.
- `Tailscale.kt` (v3): the phone's Tailscale switched by broadcast to `com.tailscale.ipn/.IPNReceiver` (CONNECT_VPN,
  DISCONNECT_VPN; `<queries>` in the manifest); `onAndWait` waits for the VPN before dialling, or the link drops when it comes.
- `MacFinder.kt` (v3): every way to the Mac at once, the first that greets "RFB " wins: Bonjour `_rfb._tcp` on the Wi-Fi,
  the home address that worked last (`lan_host`), 100.79.251.19, `macbook-pro`, HOST if written.
- `Session.start()` (v3): Tailscale on → find → dial; run by itself in onCreate when a VNC password is saved, on return
  from the background, and up to 5 times after a fall in front. `hangUp()` (✕, Back out, swipe away via
  `LinkService.onTaskRemoved`) closes the link, stops the service and switches Tailscale off.
- `tools/fake_vnc.py`: a local test server on 127.0.0.1:5901.

**Build (manifest android-app.md §1b): the Mac first.**

    cd android && JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew --offline -q assembleDebug
    export ANDROID_SERIAL=100.67.149.40:5555; adb install -r app/build/outputs/apk/debug/<N>-mantra-remote-vnc-v<N>.apk
    gh release create v<N> app/build/outputs/apk/debug/<N>-mantra-remote-vnc-v<N>.apk --latest

Raise `appVersion` in `android/gradle.properties` first (every change a new whole number); the APK name comes from it.

**Controls (v3: DOCK added, ⌃ tapped twice with 150 ms between, opens and closes the Dock Switcher; ⌃` cannot be
used, ` is a dead key on his US International layout and never arrives).** MON 1 BenQ / MON 2 MacBook (or swipe left/right), ✕; meter (VNC MB, kbit/s, ms); keys ⌃ ⌥ ⌘ ⇧ (named,
MacBook order; ⌘ is a held toggle for ⌘Tab; ⌃ is also pressed at once so ⌃⌃ reaches the Mac) ESC TAB ⏎ ⌫ arrows;
KEYS, SCROLL, FPS (long press: list), colours (long press: list), LOGIN; ⛶ FULL, VIEW ONLY, TAB ▶ (⌘⇧], next tab in
Chrome and iTerm, works in VIEW ONLY). Gestures: 1 finger mouse (tap click, long press drag / right click), 2 zoom, 3 pan.

**Open (next session).**
00. v17 (27.10.2026): renamed Mantra Remote VNC (label, first screen, notification, APK name, site; package unchanged).
   ⚙ moved to the right end of the last row shown; ⚙ switches ROW 1 KEYS / ROW 2 TOOLS / ROW 3 VIEWS on and off; with all
   three off, ⚙ floats see-through over the bottom right of the picture. DOCK: a tap = ⌥⌘D (the Mac's Dock hiding off/on),
   a long press = the phone's dock. Written in the cloud, not built: build on the Mac.
0. v13 (26.10.2026): the status line (Marko's word, not "meter") reads VNC MB · kbit/s · ms · C<colours> · <n> FPS,
   fixed slots; the ↓ ↑ split only in ⚙; ⚙ switch "STATUS LINE: ON / OFF" (pref `meter_on`). Seen on the phone.
   v12 (26.10.2026): in ⚙ every number starts at the left edge, padded on its right (`%-7s`), units in one column;
   ✕ QUIT APP left, BACK right. Seen on the phone.
   v11 (26.10.2026): titles of ⚙, FPS, colours and DOCK centred; ms = a running average (0.9/0.1 per picture),
   shown once a second as three digits with zeros (065 ms); ⚙'s numbers keep their padding; ✕ QUIT APP.
   v10 (26.10.2026): MON 1 / MON 2 / ✕ gone from the top (⇄ switches); the meter alone at the top, fixed-width
   numbers (no jitter), switchable (pref `meter_on`); second row SCROLL | ⌨ | LOGIN | ⚙; ⚙ = the meter with its meaning,
   FPS and colours as rows of choices, METER ON THE SCREEN, BACK, ✕ QUIT (hangUp + finish: quitting is ⚙ then ✕).
   The full-screen rails still carry MON, FPS, COL. Seen on the phone.
   v9 (26.10.2026): ☰ (last row) = MENU mode: the three key rows become ESC ↑ ☰OPEN / ← ↓ → / ⏎CHOOSE ✕EXIT;
   ☰ asks Dock Switcher v7 `/menu` (AXPress on the front app's menu, menu bar shown even hidden), EXIT `/menuclose`
   (AXCancel), never ESC. Proven on the phone: iTerm2 menu opened, → Shell, ↓ New Window, EXIT closed, keys back.
   Portrait only; the full-screen rails have no MENU yet.
   v8 (26.10.2026): DOCK opens the phone's dock (`DockScreen`): the Dock Switcher's apps from `MacSister` (:8861 on
   the Mac, Dock Switcher v6 `Remote.swift`), cells as square as the screen allows, dot = open, lit = in front; a tap
   = `/open` (switch or start), ⌃⌃ MAC keeps the old way. The landing line now waits on the Mac's own key count
   (`/keys?after=N` long-poll, `Session.watchKeys`), 30–42 ms at home; the picture probe only when the sister is
   silent. The sister is asked at 100.79.251.19 first (macOS Local Network privacy holds back the Wi-Fi), cleartext
   allowed in the manifest. Colours read C8, C64 …; monitor, zoom and pan remembered (monitor proven; zoom/pan not yet).
   v7 (26.10.2026): last row ⛶ | VIEW | TAB ▶ | ⇄ (BENQ/MACBOOK under it) | DOCK, widths by weight. Seen on the phone.
   v6 (26.10.2026): (a) the line above the keys shows what is travelling ("ESC  →  MAC"), gone when it landed, red
   "NOT LANDED YET" after 3 s; landing is proven by `Rfb.probe()`: a whole 1x1 request at (0,0) after the keys, the Mac
   answers in order (log "landed: … in N ms", 76 ms at home). In full screen the line sits at the top of the picture.
   (b) MacFinder tries the home Wi-Fi first (Bonjour + lan_host), the tunnel only after 3 s of silence there: at home it
   linked to 192.168.1.100 in 3 s. Not yet seen: the line itself on the phone, away-from-home fallback with v6.
   v5 (26.10.2026): second row FPS, SCROLL, ⌨ (the keyboard, where FPS was), colours, LOGIN; ⏎ in a 36 dp frame
   (padH 2) so the first row ends inside the 413 dp phone, the others unchanged (Marko's word). Seen on the Nothing.
   v4 (26.10.2026): VIEW ONLY blocks only the mouse; ESC, TAB ▶, DOCK proven to pass, a tap on the picture sent no
   click (event tap on the Mac). v3 tested 26.10.2026 on the Nothing over Wi-Fi debugging: opened from Tailscale OFF → live in 3 s with no tap;
   DOCK opens/closes the square (portrait and full screen, 3 rounds); Back and swipe-away switch Tailscale off. Not yet
   tried away from home on 4G (Tailscale path only), and not launching an app from the square by touch.
1. The ⌘Tab replacement: a NEW Swift app (not the Dock Switcher), running apps only, drawn and acting like Apple's
   switcher, on BOTH screens at once, no delay. Event tap that swallows ⌘Tab (needs Accessibility, Marko grants it).
2. Marko to test v2: ⌃⌃ from the phone opening the Dock Switcher; the names under ⌃ ⌥ ⌘ ⇧; ⌘Tab with the switcher seen.
3. The LANDSCAPE layout is unreachable since its button went (FULL is the sideways view); remove its code or bring it back.
4. Background: tested 45 s only; test 10 minutes and a locked phone.
5. The debug key signs the Mac builds; a CI workflow would sign with another key (android-app.md §1b).

## 2.11.2026 (v18 → v20, main)
- v18: ONLINE list at the top (Tailnet.kt: the Tailscale API with an OAuth client devices:read, else the known places,
  each probed on its port), system bars kept clear (safeDrawingPadding), one MON toggle key in full screen.
- v19: the address under the name (no cut in portrait); chromium-vm (100.102.86.30) known without OAuth.
- v20: the first page is MY MACHINES, one button each (ENTER ▶, SOON for Linux), ✕ closes the app; AUTO LOGIN and
  "→ THE MAC" removed; Back in the Mac view closes it and brings the list; Tailscale stays on until the app closes;
  the passwords, PINs and the Tailscale pass under SETTINGS. Tested on the Nothing Phone 2a, 2.11.2026.
- Next: entering a Linux machine (chromium-vm), and the Star API (STAR_API, port 8863, X-Mantra-Token) from the app.

## 2.11.2026 (v21, main): entering chromium-vm
- `Tailnet.LINUX_VMS` (CHROMIUM-VM 100.102.86.30:5901); the list probes a Linux VM on its VNC port, ENTER ▶ instead of SOON.
- `Session.linux` set → `startLinux()`: Tailscale on, dial straight, VncAuth (type 2) with the password from
  `Secrets.pin(prefs, "vnc:100.102.86.30")` (SETTINGS → CHROMIUM-VM (LINUX), encrypted by the Keystore). No password
  saved → the status asks for it and SETTINGS opens. No MacSister key watch for a Linux machine (landing = picture probe).
- `Rfb(…, extendedSize = true)` asks ExtendedDesktopSize (-308) for Linux only (Apple's encodings untouched); parses it,
  `setDesktopSize()` (msg 251), `closeAfterWrites()`. FIT / FITTED (row 3 and the full-screen rail): the VM takes the
  picture's size in dp (410x598 on the Pixel 7 portrait); the second tap, Back or ✕ gives the size it had back.
- Hidden on Linux: ⌘ (mods are ctrl / alt / shift), ⇄, DOCK, LOGIN, ☰, rail LOGIN / MON / DOCK; the monitor swipe; Mac
  zoom/pan prefs are not touched. ⇥ = Ctrl+Tab (not tried: Marko's Chrome tabs were left alone).
- Tested 2.11.2026 on the Pixel_7_API_35 emulator (headless, portrait), NOT the real Pixel 7: it was on USB but offered
  no ADB interface (adb saw no device; Tailscale pixel-7 offline 6 days). Seen: ENTER → Chrome/Gemini picture in 1 s
  (1512x944), a tap on empty space moved the VM's pointer exactly there, FIT → 410x598 and back to 1512x944, Back → LINK
  CLOSED (socket TIME_WAIT, service stopped), ENTER again → picture. Keys and typing not tried (his Gemini job ran there).
- Next: the same test on the real Pixel 7 (USB debugging on) and the Nothing; a key test on an empty Chrome tab.

## v22 (2.11.2026)

Tap the title on the first page (the list of machines) → GitHub releases/latest of MANTRA_REMOTE_VNC_TAILSCALE; not inside a machine. Tested on the real Pixel 7 over ADB: the tap opens Firefox; ENTER CHROMIUM-VM live (1 FPS, 125 ms), the top inside a session opens nothing, Back → LINK CLOSED and the list. The repo is private, so the page shows 404 unless the phone is signed in to GitHub. Main is the only branch; v22 is the Latest release.

"make repository public" (Marko, 2.11.2026): done; the history was searched for keys first (none). The title tap on the Pixel 7 now opens v22 with its APK in the GitHub app.
- 2.11.2026 ~16:40 CT (chromium-vm Mac session): v21 installed on the real Pixel 7 (41241FDH2S10SJ, by adb), the
  notification permission allowed, the CHROMIUM-VM VNC password saved under SETTINGS (typed from the key file into
  the verified field, never shown). The ENTER / picture / Back test on the real phone was not done: Marko said
  "wait until I say continue". Tailscale on the phone was last seen 6 days before: check it is on before ENTER.

## v23 (3.11.2026): ENTER always checks Tailscale and looks the address up fresh
Marko, 3.11.2026, on the Pixel 7: "It cannot connect to the Mac session. Looks like it's not picking up the right
address or it's not connected with Tailscale ... when I click to connect, checking Tailscale address and connecting to
the address. It's always refreshing when I want to connect."
- Causes found in the code: without the OAuth key the Mac was only ever tried at the address written in the source
  (100.79.251.19, measured 26.10.2026) and chromium-vm at 100.102.86.30; `Tailscale.isUp` counted any VPN as "up" even
  with Tailscale signed out (no 100.x address on the phone); the CONNECT broadcast was sent once and lost if Tailscale's
  process slept; a machine shown OFFLINE (list read before Tailscale stood) could not be entered, a tap only re-read
  the list.
- `Tailscale.ensure()`: up = VPN AND the phone's own 100.x address; the broadcast repeated every 4 s, at most 15 s;
  `whyDown()` says why in words; the first page shows it with OPEN TAILSCALE.
- `Tailscale.resolve(name)`: the VPN network's resolver (short name + its search domain), else a small DNS client
  straight to 100.100.100.100 with the tailnet's domain learnt from the PTR of the phone's own 100.x address.
- `Tailnet.macAddresses()` / `vmAddress()`: fresh lookup (DNS, then the API if keyed), remembered as `mac_ts_ip` /
  `vm_ip:<NAME>`, then the old written address as the last fallback. `Session.start()` and `startLinux()` do it on
  every ENTER and every redial; the list does it on every read. `linuxVm()` matches by name too.
- ENTER on a Mac/VM shown OFFLINE ("OFFLINE · TRY ▶") now tries it anyway (with the fresh lookup).
- Logcat (tag MantraRemote): `resolve macbook-pro → [...]`, `tailscale up=… own=… mac candidates=[...]`,
  `tailscale not ready after …`.
- Built in the cloud (compiles, v23 APK, signed with the cloud's debug key, NOT the Mac's): not yet tried on a phone.
  Build on the Mac for an in-place update over v22.

## v24 (3.11.2026): L/R lock, CONNECT TO OTHER SESSION, built by GitHub Actions
- L and R (row and full-screen rail): a short press clicks; a long press locks that button down (lit, "LOCKED"),
  moving the pointer then drags with it; another long press lets it go (Marko, 3.11.2026). `held` (0 / 1 / 4) replaces
  `dragHeld`; D is the left lock. Fixed on the way: in 🖱 MOUSE mode the moves sent button 0 during a lock (let it go).
- ⚙ → ⇄ CONNECT TO OTHER SESSION: every machine of the tailnet, read fresh; a tap closes this link and enters that one
  (`Session.enter(place)`, shared with the first page; `isCurrent` marks the one on the screen "(NOW)").
- `.github/workflows/android.yml`: every push to main that touches `android/` builds the APK and publishes release
  v<appVersion> as Latest. Signing needs the secret ANDROID_DEBUG_KEYSTORE_B64 (base64 of the Mac's
  ~/.android/debug.keystore; on the Mac: `base64 -i ~/.android/debug.keystore | gh secret set ANDROID_DEBUG_KEYSTORE_B64
  -R markoboskoauroville/MANTRA_REMOTE_VNC_TAILSCALE`), so CI builds install over Mac builds. Building on the Mac is no
  longer the way; work goes to main (MANTRA_MANIFEST rule, 3.11.2026).

## v25 (3.11.2026): a refused login is never dialled again
Pixel 7 test of v24 (Termux session "i am pixel phone"): the list filled at once (MACBOOK-PRO 100.79.251.19,
CHROMIUM-VM 100.102.86.30; MagicDNS resolve worked, the Mac answered at home on 192.168.1.102), but with the passwords
wiped by the uninstall one ENTER gave six "LOGIN REFUSED" in 38 s: `onClosed` redialled every fall. Now a refused
login (and REFUSED / NO VNC PASSWORD / NOT A VNC SERVER) stops: no redial, the service stops, SETTINGS opens with
"CHECK THE … PASSWORD". ENTER on the Mac with no password saved dials nothing and opens SETTINGS.
Still to test on the phone (needs Marko's passwords typed in SETTINGS): picture, L/R lock, CONNECT TO OTHER SESSION.

## v26 (5.11.2026)
Top row: ESC first, then ctrl and alt (shift removed; the keyboard has its own), then ⌘TAB (Mac: ⌘+Tab; Linux machine: Super+Tab, the VM's switcher). FIT on a Linux machine no longer resizes the machine: the picture fills the phone with its aspect kept, one finger drags across it, a tap clicks, a long press drags with the button. Pan is clamped so no empty edge shows.


## From the former README (9.11.2026)

# MANTRA REMOTE

**v21 (2.11.2026): CHROMIUM-VM is entered too.** Its button says ENTER ▶ and opens the Oracle VM (100.102.86.30:5901,
TigerVNC, VNC password kept encrypted on the phone under SETTINGS → CHROMIUM-VM). Same picture, gestures and keys; the
Mac-only keys (⌘, MON / ⇄, DOCK, LOGIN, ☰) hide there; ⇥ is Ctrl+Tab; FIT asks the VM for the phone's size (a second tap
or leaving gives the old size back). Back / ✕ closes the link.

**The Android app, v13 (26.10.2026):** `android/`, the phone controls the Mac with Apple Screen Sharing, logged in as
Marko, two monitors, gestures, a held link in the background. Nothing to type: opening the app switches the phone's
Tailscale on, finds the Mac (home Wi-Fi by Bonjour, or Tailscale, the first that answers) and logs in; closing it
switches Tailscale off. DOCK opens the Mac's Dock on the phone, full screen: a tap switches to an app or starts it (with the Dock Switcher v6 on the Mac). VIEW ONLY blocks the mouse only; keys always pass. Home Wi-Fi first, the tunnel only away; a tiny line above the keys shows each key until it lands on the Mac. ☰ turns the keys into the Mac's menu bar keys (opened by Accessibility). A status line at the top (data, speed, time, colours, FPS); ⚙ holds its meaning, FPS, colours and ✕ QUIT APP. Release: GitHub v13. How it is built, what is
open: [TAKEOVER.md](TAKEOVER.md); what was learned: [LESSONS.md](LESSONS.md).

>>> **START HERE IN ANY NEW SESSION: [FEATURE_REQUEST.md](FEATURE_REQUEST.md)**
>>> That file is the project brain — feature menu, design language, open decisions.
>>> This README is only the Tailscale/VNC plumbing.


**Control my Mac from my Nothing Phone 2a, from anywhere, over 2 Mbit/s.**

Private. No open ports. No public IP needed. No DDNS needed.

---

## THE ONE THING YOU MUST UNDERSTAND FIRST

You asked for **Dynamic DNS** (IP changes → name always points to you).

**Do not use DDNS. It will not work on Telemach.**

Croatian mobile and most modern cable connections put you behind **CGNAT**
(Carrier Grade NAT). That means:

- The public IP you see is **shared with hundreds of other customers**.
- It is **not yours**. You cannot forward a port to it.
- A DDNS name pointing at it is a name pointing at Telemach's router, not your Mac.

DDNS only works if you have a real public IPv4 and can open a port. You almost
certainly do not, and even if you did, an open VNC/RustDesk port on the public
internet is exactly the thing that gets you owned.

**The correct answer is an overlay network.** Both devices dial *out* to a
coordination server, punch through NAT, and then talk **directly, encrypted,
peer to peer**. Your Mac gets a permanent address that never changes even when
Telemach changes your IP five times a day.

That tool is **Tailscale** (WireGuard under the hood).

---

## ARCHITECTURE

```
  Nothing Phone 2a                              Mac at home
  ┌──────────────┐                          ┌──────────────┐
  │ Tailscale    │                          │ Tailscale    │
  │ 100.x.x.x    │                          │ 100.y.y.y    │
  │              │  <=== WireGuard P2P ===>  │ mantra-mac   │
  │ RustDesk /   │      encrypted, direct    │ Screen       │
  │ bVNC client  │      NAT-punched          │ Sharing +    │
  └──────────────┘                          │ RustDesk     │
         │                                  └──────────────┘
         │                                          │
         └──── both dial OUT to Tailscale ──────────┘
               coordination server (control only,
               your pixels never touch it)
```

**Layer 1 — Transport: Tailscale.** Solves dynamic IP + CGNAT + encryption.
**Layer 2 — Pixels: RustDesk over the tailnet** (or macOS Screen Sharing/VNC).

Because Tailscale already gives a stable private IP, RustDesk connects by
**direct IP** — you do not need RustDesk's public servers *or* your own relay
VPS at all. Nothing is published anywhere. This is the quiet setup.

Cost: **0 €.** Tailscale Personal is free for 6 users with unlimited personal
devices (as of the April 2026 pricing change).

---

## PLAN: TWO STAGES

### Stage 1 — Working today, 20 minutes, zero code
Tailscale + macOS built-in Screen Sharing + **bVNC** on Android.
8-bit colour mode. Good enough to click Record in Zoom from a beach in Kukljica.

### Stage 2 — The custom APK, "Mantra Remote"
Fork `rustdesk/rustdesk`, bake your server/key into the build with compile-time
env vars, build the APK in GitHub Actions, install it on the phone.
It opens and it already knows your Mac. Own name, own icon, own colours.

Do Stage 1 first. It is the fallback that always works when Stage 2 breaks.

---

## STAGE 1 — SETUP

### 1. Mac side

```bash
git clone https://github.com/markoboskoauroville/MANTRA_REMOTE_VNC_TAILSCALE.git
cd MANTRA_REMOTE
chmod +x mac/*.sh
./mac/mantra-mac-setup.sh
```

The script installs Tailscale, enables Screen Sharing, stops the Mac from
sleeping, and prints your permanent address.

Then, **critically**, in the Tailscale admin console
(https://login.tailscale.com/admin/machines):

- find `mantra-mac` → `...` menu → **Disable key expiry**

If you skip this, the Mac silently drops off the tailnet in ~6 months while you
are in another country. This is the number one way this setup fails.

### 2. Phone side

1. Install **Tailscale** from Play Store, log in with the same account.
2. Install **bVNC Free** (or bVNC Pro, ~10 €, worth it for gestures).
3. New connection:
   - Type: **VNC**
   - Address: `mantra-mac` (or the `100.x.x.x` shown by the setup script)
   - Port: `5900`
   - Username: your Mac login name
   - Password: your Mac login password
4. Under advanced settings set **Colour: 8-bit** and **Compression: Tight,
   quality 4**. See `docs/BANDWIDTH.md`.

### 3. Test

Turn **Wi-Fi off** on the phone. Use mobile data only. Connect.
If it works on 4G it works from anywhere.

---

## STAGE 2 — CUSTOM APK

See [`rustdesk/BUILD.md`](rustdesk/BUILD.md).

Short version:

- Fork is **not** enough — GitHub forks of public repos are always public and
  can never be made private. To keep it secret you do a **mirror clone** into a
  fresh private repo. Command is in that file.
- Set `RENDEZVOUS_SERVER` and `RS_PUB_KEY` at build time so the APK ships
  pre-configured.
- Rename, re-icon, build with the provided Actions workflow, download APK from
  the run artifacts, sideload.

---

## FILES IN THIS REPO

| Path | What |
|---|---|
| `mac/mantra-mac-setup.sh` | One-shot Mac setup |
| `mac/mantra-status.sh` | Health check — run before you leave the house |
| `mac/com.mantra.remote.awake.plist` | LaunchDaemon, never sleep |
| `docs/BANDWIDTH.md` | Making 2 Mbit/s actually usable |
| `docs/OBS_ZOOM.md` | The recording workflow |
| `docs/SECURITY.md` | Locking it down |
| `docs/TROUBLESHOOTING.md` | When it breaks and you are in Zadar |
| `rustdesk/BUILD.md` | Building the custom Mantra Remote APK |
| `rustdesk/self-host-server.md` | Optional: own relay on a VPS |

---

## BEFORE YOU LEAVE THE HOUSE

Run this. Every time.

```bash
./mac/mantra-status.sh
```

Green across the board = you can leave.

## 14.11.2026: the app updates itself (v48 → v50)
AppUpdater.kt: at start GitHub's Latest release (public repo, no token); vN newer than versionCode → APK downloaded to
cache (Wi-Fi first; on mobile data asked first with the size) → "Mantra Remote Viewing vN is ready · Do you want to
update?" UPDATE / LATER → PackageInstaller session (REQUEST_INSTALL_PACKAGES; Android asks once "Allow from this source",
then its own "Do you want to update this app?"). First start of the new version: "Updated to vN". MY_PACKAGE_REPLACED posts
a notification "vN is installed · Tap to open". Tested v48 → v49 on the Pixel 7 emulator. Play Protect can show its own
dialog on a sideloaded update: the answer is to install/scan, not to block it. v50: onResume continues an install that waited for the permission.
