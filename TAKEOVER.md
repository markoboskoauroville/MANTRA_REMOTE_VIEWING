# TAKEOVER: Mantra Remote VNC (Android), state 27.10.2026, v17 (named Mantra Remote until v16)

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
