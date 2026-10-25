# TAKEOVER: Mantra Remote (Android), state 25.10.2026, v2

**What it is.** An Android app (Kotlin + Compose, `android/`, package `com.mantra.remote`) that controls Marko's Mac from his
Nothing Phone 2a over the internet. Tailscale carries it (Mac 100.79.251.19, phone 100.67.149.40); the Mac side is Apple
Screen Sharing on port 5900. The RFB client is our own (`Rfb.kt`): ARD auth (type 30) logs in as the Mac user and shows
the real desktop (type 2, the VNC password, only sees the lock screen). Release: v2 on GitHub, built on the Mac.

**Files.**
- `android/app/src/main/java/com/mantra/remote/Rfb.kt`: the protocol, the encodings (ZRLE, Zlib, Hextile, CopyRect, Raw,
  DesktopSize), the colour formats (only 32 bpp and 16 bpp 5-6-5 are accepted by Apple), byte counters, VIEW ONLY,
  the 2 s fast-picture burst after any key or click, `key(…, always)` for keys that pass VIEW ONLY.
- `MainActivity.kt`: the Session (held in `Hold`, so a rebuilt window finds the live link), the screens (portrait, full
  screen with CAMERA-language side rails), gestures, keys, FPS and colour menus, the help popups.
- `LinkService.kt`: a foreground service (specialUse) that keeps the process awake while the app is in the background;
  in the background the picture drops to 0.2 FPS, in front back to the chosen FPS; a link that fell is dialled again on return.
- `tools/fake_vnc.py`: a local test server on 127.0.0.1:5901.

**Build (manifest android-app.md §1b): the Mac first.**

    cd android && JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew --offline -q assembleDebug
    export ANDROID_SERIAL=100.67.149.40:5555; adb install -r app/build/outputs/apk/debug/<N>-mantra-remote-v<N>.apk
    gh release create v<N> app/build/outputs/apk/debug/<N>-mantra-remote-v<N>.apk --latest

Raise `appVersion` in `android/gradle.properties` first (every change a new whole number); the APK name comes from it.

**Controls (v2).** MON 1 BenQ / MON 2 MacBook (or swipe left/right), ✕; meter (VNC MB, kbit/s, ms); keys ⌃ ⌥ ⌘ ⇧ (named,
MacBook order; ⌘ is a held toggle for ⌘Tab; ⌃ is also pressed at once so ⌃⌃ reaches the Mac) ESC TAB ⏎ ⌫ arrows;
KEYS, SCROLL, FPS (long press: list), colours (long press: list), LOGIN; ⛶ FULL, VIEW ONLY, TAB ▶ (⌘⇧], next tab in
Chrome and iTerm, works in VIEW ONLY). Gestures: 1 finger mouse (tap click, long press drag / right click), 2 zoom, 3 pan.

**Open (next session).**
1. The ⌘Tab replacement: a NEW Swift app (not the Dock Switcher), running apps only, drawn and acting like Apple's
   switcher, on BOTH screens at once, no delay. Event tap that swallows ⌘Tab (needs Accessibility, Marko grants it).
2. Marko to test v2: ⌃⌃ from the phone opening the Dock Switcher; the names under ⌃ ⌥ ⌘ ⇧; ⌘Tab with the switcher seen.
3. The LANDSCAPE layout is unreachable since its button went (FULL is the sideways view); remove its code or bring it back.
4. Background: tested 45 s only; test 10 minutes and a locked phone.
5. The debug key signs the Mac builds; a CI workflow would sign with another key (android-app.md §1b).
