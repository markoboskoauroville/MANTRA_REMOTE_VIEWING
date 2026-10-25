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
