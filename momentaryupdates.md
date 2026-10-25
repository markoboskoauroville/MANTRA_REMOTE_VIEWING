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

**25.10.2026 19:27, first connection over 4G works (Mac lock screen seen on the phone):**
> please make this app work in portrait mode. It's very important. Put the switch for portrait and landscape there, because my screen on my computer is, of course, landscape make this app work in landscape obvious switch in landscape easy to go into the full screen and exit full screen this app with some overlay over it, some kind of rectangle which is half transparent fullscreen action button. Exit. Entering actually, maybe when you enter full screen, you just orient the screen to landscape. Always landscape
> please make an action button "Login." In the settings, I'm going to write the password. When I press Login, it's going to type the password. Every time I need a password in this login screen or any screen that pops up on the screen, I just press Login. Please make these action buttons without a frame, half transparent, text only, without shadow, like subtitles. Imagine the subtitles so they don't obscure the master of the screen. Think how to make these action buttons really not obscure the screen. Maybe at the bottom? I don't know. Take a screenshot and make it somehow smaller and less distracting
> please look at my camera app and make the same user interface with the action buttons on the side of the screen. Check my camera app
> make this app auto-login to the macOS. It should not be waiting for me. Just when you open, you log in automatically. Auto-login, please

**25.10.2026 19:35:** auto-login works. The app signs in with Apple's own method (RFB security type 30, Diffie-Hellman +
AES-128, the Mac user and password from the settings), so macOS opens the desktop directly: no lock screen, nothing typed.
Tested over 4G through Tailscale on the Nothing Phone 2a: the BenQ desktop appeared at once.
> please make gestures in my application for remote control: - Two fingers: spreading, pinching, zooming in and out. - Three fingers: moving, panning through the screen. - One finger: tapping, clicking with the mouse
> remember frames per second of the screen between the restarts of the app, and one FPS is default
> make a long press on FPS. Open the menu, and the user can directly switch. He doesn't need to go all around the scale. Long press FPS and add 0.2 FPS as well
> long-press on any button will open the pop-up explaining what it does
> FPS pop-up doesn't do that. It just shows FPS
> side FPS also adds a button for the number of colors. It can be black and white, and I don't know what the minimum colors are. We also save on bandwidth by saving on the color screen. Maybe only black and white color. I don't know. Something in between should be
> in the status, I want to see, in the current session, how much data went only on the transmission of the images from this application. Basically, I want to see how many megabytes were spent in my session, talking about only transmitting VNC data back and forth

**25.10.2026 19:55, colours measured on the real desktop (one full BenQ picture):** FULL 978 kB, 65K 524, 4096 427,
512 226, 64 177, 8 159, B/W about 160. Apple's server takes only 32 bit and 16 bit 5-6-5 pixels (it closes the link on
any 8 bit format); fewer steps inside 32 bits work. A VNC-password session sees only the lock screen, so measurements must
be made in the app (logged in as Marko), not with the VNC password.
> panning and scanning image still not work. Please build this three-finger gesture,
> I'm going now on Wi-Fi debugging. Please fix my application. We need to have a button which blocks all the clicks, only watching. It will be watching only, so I can zoom in and scroll, but I cannot click. I can zoom and scroll in the picture, not on the real Mac. So it will be a view-only button
> Please swap the buttons on the full screen. What is on the left should go on the right side, and what is on the right should go on the left side.
> Please update my remote control so it can work in the background. Right now, when it goes to the background and I bring it back up, I need to reconnect. When it's in the background, just put it to 0.2 frames automatically. When it's in the foreground, it should go back to whatever the user chooses.
