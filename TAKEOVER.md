# TAKEOVER: Mantra Remote (Android), state 26.10.2026, v3

**What it is.** An Android app (Kotlin + Compose, `android/`, package `com.mantra.remote`) that controls Marko's Mac from his
Nothing Phone 2a over the internet. Tailscale carries it (Mac 100.79.251.19, phone 100.67.149.40); the Mac side is Apple
Screen Sharing on port 5900. The RFB client is our own (`Rfb.kt`): ARD auth (type 30) logs in as the Mac user and shows
the real desktop (type 2, the VNC password, only sees the lock screen). Release: v3 on GitHub, built on the Mac.

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
    export ANDROID_SERIAL=100.67.149.40:5555; adb install -r app/build/outputs/apk/debug/<N>-mantra-remote-v<N>.apk
    gh release create v<N> app/build/outputs/apk/debug/<N>-mantra-remote-v<N>.apk --latest

Raise `appVersion` in `android/gradle.properties` first (every change a new whole number); the APK name comes from it.

**Controls (v3: DOCK added, ⌃ tapped twice with 150 ms between, opens and closes the Dock Switcher; ⌃` cannot be
used, ` is a dead key on his US International layout and never arrives).** MON 1 BenQ / MON 2 MacBook (or swipe left/right), ✕; meter (VNC MB, kbit/s, ms); keys ⌃ ⌥ ⌘ ⇧ (named,
MacBook order; ⌘ is a held toggle for ⌘Tab; ⌃ is also pressed at once so ⌃⌃ reaches the Mac) ESC TAB ⏎ ⌫ arrows;
KEYS, SCROLL, FPS (long press: list), colours (long press: list), LOGIN; ⛶ FULL, VIEW ONLY, TAB ▶ (⌘⇧], next tab in
Chrome and iTerm, works in VIEW ONLY). Gestures: 1 finger mouse (tap click, long press drag / right click), 2 zoom, 3 pan.

**Open (next session).**
0. v3 tested 26.10.2026 on the Nothing over Wi-Fi debugging: opened from Tailscale OFF → live in 3 s with no tap;
   DOCK opens/closes the square (portrait and full screen, 3 rounds); Back and swipe-away switch Tailscale off. Not yet
   tried away from home on 4G (Tailscale path only), and not launching an app from the square by touch.
1. The ⌘Tab replacement: a NEW Swift app (not the Dock Switcher), running apps only, drawn and acting like Apple's
   switcher, on BOTH screens at once, no delay. Event tap that swallows ⌘Tab (needs Accessibility, Marko grants it).
2. Marko to test v2: ⌃⌃ from the phone opening the Dock Switcher; the names under ⌃ ⌥ ⌘ ⇧; ⌘Tab with the switcher seen.
3. The LANDSCAPE layout is unreachable since its button went (FULL is the sideways view); remove its code or bring it back.
4. Background: tested 45 s only; test 10 minutes and a locked phone.
5. The debug key signs the Mac builds; a CI workflow would sign with another key (android-app.md §1b).
