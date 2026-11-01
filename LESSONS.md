# LESSONS: Mantra Remote

- **Apple Screen Sharing accepts only 32 bpp and 16 bpp 5-6-5.** Any 8-bit format or another 16-bit layout closes the link
  (EOFException). Fewer colour steps inside 32 bpp work and save the data.
- **Type 2 (VNC password) sees the lock screen; type 30 (ARD, Diffie-Hellman + AES-128-ECB of user/password) sees the
  real desktop.** Measure sizes in the app, logged in, never with the VNC password.
- **The framebuffer is in pixels** (5584x1964: BenQ 2560x1440 at 0,0; MacBook 3024x1964 at 2560,0).
- **Compose gesture loops must read geometry through functions**, or they keep the values of their first composition.
- **Read the frame tick in composition**, or a new bitmap after a redial stays black.
- **A background app's process is frozen** and the Mac drops the link; a foreground service (specialUse) keeps it.
  Activity onStop/onStart set the background FPS directly; LaunchedEffects do not run while the window is stopped.
- **The keys were never slow; the picture was.** At 1 FPS the answer showed a frame later. A short burst of fast
  pictures after every input fixes the feel at no real cost.
- **One shortcut for many apps beats guessing the front app:** ⌘⇧] is the next tab in Chrome, iTerm, Safari, Finder.
- **A one-shot modifier never reaches the Mac alone**, so ⌃⌃ could not work; ⌃ now also sends a real press.
- **⌘Tab through VNC works** (Hammerspoon eventtap saw cmd-down, tab, cmd-up; the front app changed), but Apple draws
  its switcher only on the main screen.
- **Nothing OS takes three fingers** for its screenshot (secure setting nothing_three_finger_screenshot); Marko switched
  it off 25.10.2026.
- **Don't drive the phone while Marko holds it**: an adb tap lands in whatever app he has open.
- **(26.10.2026) ⌃` never arrives through Screen Sharing on US International-PC**: ` is a dead key there. Shortcuts sent
  over VNC must avoid dead-key characters; the double ⌃ (two clean taps, a pause between) works.
- **Switching Tailscale on breaks every open socket**: switch it on, wait for the VPN network, then dial.
- **Tailscale for Android takes CONNECT_VPN / DISCONNECT_VPN broadcasts** to `.IPNReceiver`; the calling app needs
  `<queries>` for `com.tailscale.ipn`. `adb shell am broadcast -n com.tailscale.ipn/.IPNReceiver -a …` tests it.
- **Wi-Fi debugging on the Nothing**: `adb mdns services` lists it (`adb-0005534BC000140-…`, 192.168.1.102), `adb
  connect` that address; it stays on the Wi-Fi when Tailscale goes off. A Back too many lands in his open app (it
  happened: Fiverr's label sheet opened, closed again with Back, nothing chosen).
- **The Dock Switcher's square once stayed open** through ⌃⌃ and ESC from the phone, while his clock app (Timer
  Stopwatch Clock, a floating full-screen window) was in front; the `community.auroville.dockswitcher.close`
  notification closed it, and after that DOCK opened and closed it every time. Cause not found.
- **Testing keys sent to the Mac while iTerm is in front**: ESC would interrupt the Claude session and ⌘⇧] switch its
  tab. A Quartz event tap with kCGEventTapOptionDefault that logs and returns None for those keycodes (53, 30) proves
  arrival without effect (allowed for python3 on 26.10.2026).
- **His phone's keyboard is a dictation keyboard that records as soon as it opens** (timer and red dot). A test of ⌨
  records him; close it with ⌨ again at once, never touch its own buttons. The rows move up while it is open: find
  a key's place again from a screenshot before the next tap.
- **Never tap the phone by remembered coordinates while the picture may still be loading**: the rows moved, my taps hit
  ⌨ and the colours, his dictation keyboard heard him and the app typed his words ("two more requests, please") into
  iTerm on the Mac. Screenshot right before every tap; stop tapping while he holds the phone.
- **The first full picture of MON 2 (3024x1964) takes about 8 s at home**; until then the picture is black.
- **The 1x1 picture probe is no proof of landing**: Apple's server answered it late or not at all while the key had
  landed (Marko saw "NOT LANDED YET" with the key done). The Mac's own count of key events (Dock Switcher /keys) is.
- **Android blocks plain http by default** (targetSdk 35); runCatching hid the error. Log every failure of a network call.

- **(1.11.2026) Releasing a Twilio number throws away the paid rest of its month** (no refund, no way back). Keep a
  number until just before its next charge, and ask Marko before releasing. See `docs/TWILIO.md`.
