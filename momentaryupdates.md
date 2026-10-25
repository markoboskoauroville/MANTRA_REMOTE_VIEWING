# Momentary updates: Marko's requests, word for word, saved before any code

**25.10.2026 ~18:05 (session "ableton live remote control", hub):**
> Now this session will be for something else. I'm coming home in 5 to 10 minutes, so you need to prepare everything. I'm going to attach my phone for debugging purposes.
> For the first time, we need to create an application for remote controlling the computer. There is a repository somewhere, so please find it and clone it. Check the code, and when I plug in the phone, we will have a Wi-Fi connection and an internet connection.
> We are going to work only through the internet connection for testing purposes. I want to see if we can tunnel this phone to my computer from anywhere in the world with our application.
> The application needs to support 2 monitors, with a simple switch between monitor 1 and monitor 2. It should work as a remote control for my computer, with a user interface adapted to the phone and finger use, so I can operate my computer from any distance.

**25.10.2026 18:30, built while Marko was on his way home:** Android app v1 in `android/` (Kotlin, Compose, own RFB
client: VNC password, ZRLE/Zlib/Hextile/CopyRect/Raw, two monitors as two rectangles of the framebuffer, frames asked
only for the chosen monitor). Tested on the Pixel 7 emulator against `tools/fake_vnc.py` (password, MON 1/MON 2,
tap lands at the right Mac coordinates, typing with Shift and Backspace). Waiting for Marko: Tailscale on the Mac
(pkg in ~/Downloads/Tailscale), Screen Sharing with a VNC password, Tailscale app on the phone.

> plugged in. Start testing. Adjust the testing in my Mac, and let's go
