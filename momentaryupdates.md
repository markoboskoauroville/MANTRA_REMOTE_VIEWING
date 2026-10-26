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
> please make a button that switches from one chrome tab to the other, so i can go through the tabs
> Yes, it's switching the tab. I want this button to also work in view-only mode, so that it's always active. For example, if I'm in 1 frame per second mode, can you make it immediate when I press the button? The app shouldn't wait for the 1 frame per second mode. It shouldn't send the keystroke only once every second, so that it takes 2 or 3 seconds to react. Pressing the keystroke should, I think, raise the FPS momentarily just to send these keys. I don't want to wait. I want any key pressed to be transferred immediately, no matter what the FPS is.
> It is happening, but the same shortcut doesn't work in iTerm. Can you make the button content-aware and do one shortcut in Chrome and another in iTerm?
> Could you please remove the landscape button? Then there will be more space for the letters. Right now some letters break into two lines, and sometimes the second line is a symbol. That is not good. If possible, you need to make it one line, or reduce the text.
> Make the swipe action go to another screen. Swipe left, right.
> Please make my Command key a toggle, so it is either on or off. At the moment it goes off as soon as I lift my finger. When I press Command and Tab, it should open my task switcher and let me switch between tasks. Please test this with debugging. by the way, panning and scanning works, and zooming is great
> Please write Control, Option, Command under their corresponding buttons, in the same order as on a normal MacBook Pro keyboard. On my computer I have a special app I built, which I can start by double tapping Control. This is not possible with this remote controller. I double tap, but it does not register as a double tap. One more thing, please. Please look at how Apple's task switcher is drawn. It is strange. There is a big delay before it comes on the screen. Then I switch, and I see on the computer that it has switched, but the feedback on the screen comes with a big delay. So maybe we can build a replacement for this switcher and replace the macOS switcher. First, it needs to appear on both screens. It is annoying that I switch and cannot see it, because it only comes up on the first screen. Second, as I said, there is a big delay. In Swift, we can replace Command+Tab with our own Swift app.
> That app is a launcher and a switcher at the same time. It has applications which are not open, so it also needs to be able to show what is open. Do you understand? That is why I want two apps. One replaces the Mac's own switcher, looking exactly the same and acting the same. The other one is something different. It is a launcher and switcher, and it also has the clock and the weather forecast. So I don't want to call it nonstop.
> Please explain to me how this app is built so fast. All my apps were building so slowly until now. Whatever you are doing to build the Android app, everybody must do the same. I want it for all of my future projects, because this is so fast and beautiful.

**26.10.2026:**
> Update is that it must have an action button to open the dock so I can launch my applications remotely from the phone. You just call the button dock.
> through wireless debugging, you need to find my phone called Nothing Phone 2A and test this app
> please connect this app and my phone with tail scale. When I open the app, tail scale is opening. When I close the app, tail scale is closing
> please In View Only, the Dock button passes through, the Escape button passes through, and the Next Tab button passes through.
> Actually, what is disabled in View Only is tapping with the mouse. So mouse actions are view only, and keyboard actions are always on.
> please Reorder the buttons. Where it says 5 FPS, it should say keys, and instead of writing keys, put the icon of the keyboard.You're going to see that the Enter button is out of the frame if you take the screenshot. So adjust the other buttons accordingly so Enter can fit.
> Please, on top of all the buttons, add just a tiny text in the middle. When I press a button, there is a delay because the command is traveling. So I want to see that the command is actually pressed.
> If I press Escape, there will be an Escape status. When the command lands on my computer, the status disappears.
> Another optimization is that the connection checks whether I'm on the local network. If my computer is on the local network, you don't tunnel, you use the local area network. If I'm not on my local area network, then you use the tunnel VPN.
> next to the tab icon, i need an icon to switch between screens, the benq and the macbook pro To optimize space in the last row, full screen can be only a symbol. Remove the word "full".
> It should fill up my screen real estate on my phone with the same functions. Then I pick switching or starting, and that's it. It acts the same, but you need to make a special sister app for my dock, which runs from this user interface, please.
> View only can be a toggle. The two words are toggling, so it is either view. Actually, you can put only "view". Only remove.
> And for the dock, we need a local dock, emulating the real one. Basically, you need to build a remote controller for my dock, so I'm not waiting for the dock and trying to find it on screen.
> in the color setting On the screen, there is only a number. Please add C before it so I remember this is a color. In this case, it is C8 right now.also, the app should remember the last monitor and the zoom level and position between restarts, so when it restarts, it comes back to the same point, please
> Landing detection is not correct. You need to have some sister app on my SwiftStar menu to communicate with this device, because it says "not landed yet", but in this case, if you take a screenshot, it has landed.
> now I need another feature which is going to open my menu bar if it's hidden. With arrows, I can navigate through the menu bar openings one by one, and then I can go down. Also, an action button. The idea is to have one button, which will switch all of these action buttons into an action button for the menu bar, and when I exit, my normal action buttons are returning

**Status, 26.10.2026 06:45 (session closed):** every request of 26.10.2026 above is done and tested on the Nothing
Phone 2a over Wi-Fi debugging, released as v3 to v9: DOCK (now the phone's own dock, Dock Switcher v6–v7); Tailscale on
with the app, off when it closes; the Mac found by itself, home Wi-Fi first and the tunnel only away; VIEW ONLY blocks
the mouse only; ⌨ where FPS was, ⏎ in a narrow frame; the travelling line, landing confirmed by the Mac's own key count;
⇄ beside TAB ▶, ⛶ and VIEW alone; C8; monitor/zoom/pan remembered (zoom/pan not yet by his fingers); ☰ MENU mode by
Accessibility. Not tried: away from home on 4G. The page in `site/` is written but not deployed (site/README.md).
> please explain All the green letters at the top: blue, blue, green, cyan. What are they doing? Also, there is an issue of vibration of the numbers when milliseconds are changing. Its digits then are just running left and right, which is very distracting. I also want to be able to remove this line in the settings, so at the top. Since I have switched to the screens, the top buttons can be removed. Maybe in the second row, there are not many buttons. There can be an exit button, or better, a settings button. In settings, I am exiting, and I can disable this speed status line. In the settings, I can see this data. I see it in two places on the screen if it's enabled in the settings. And also not in the screen and in the settings. Work on this status line and removing the top buttons because everything is down anyway.
> quitting the app is two steps: Settings and then X
> put settings in the second row after the login. It is just the icon of the settings. As we have a rule that all buttons do not need to have the same width, in that sense the settings can be smaller in width. C8 or color can also be smaller. That's it
> number of colors and FPS can be moved to the settings
> now the milliseconds. How much time it takes to move the application at fast speed in the setting, it vibrates
> in the settings, the title of the setting should be aligned to the middle. Align middle, please, and in other menus also align middle
> there is too much numbers of this sending. Please average it and slowly change the number milliseconds and also make three digits fixed. They are not changing places
> please add three zeros as a placeholder for the numbers of milliseconds
> in the settings and the quit, you can just put "Quit App," so people don't think it's quitting the menu itself

**Status, 26.10.2026 06:48:** the requests after the first close are done in v10–v11 and seen on the phone: the top
buttons gone, the meter steady (fixed widths, ms averaged, 065 ms), switchable in ⚙; ⚙ after LOGIN with the meter
explained, FPS and colours, ✕ QUIT APP; C8 narrower; titles centred.
> now all the status numbers in settings align to the left edge same alignment for every number
> please in settings swap the button placement for quit and app and exit this menu

**Status, 26.10.2026 06:50:** both done in v12, seen on the phone.
> at the top, when cyan numbers are running, you need to add at the top of the screen the status of colors and the status of frames per second
> so you don't call it anymore matter on screen. They are not matters. It's a status line on top of the screen. You can call it status line on off

**Status, 26.10.2026 06:52:** both done in v13 (colours and FPS on the status line; "STATUS LINE: ON / OFF"), seen on the phone.

## 26.10.2026 — ADB over the tunnel (from the Mantra Manual Camera session)

> "since we now have a tunnel for remote controlling, I also want that remote debugging ADB works over the tunnel. If I'm somewhere on the streets of Croatia, in Zagreb, Rijeka, or Zadar, from this computer you can run ADB on my phone to test the applications live. Please develop this through our tunnel for our computer remote controller. I am not wanted to be connected to this Wi-Fi, so you can use nothing phone for testing applications" (Marko, 26.10.2026)

- ADB from this Mac to the Nothing Phone 2a over Tailscale (100.67.149.40), wherever the phone is: on mobile data, in another city, never on this Wi-Fi. — **Status:** done 26.10.2026. `scripts/nothing-adb` (on the PATH): ADB through Tailscale on 100.67.149.40:5555. adbd in TCP mode (wireless debugging dies off Wi-Fi; TCP mode listens on every network until a restart; `nothing-adb arm` re-arms it once on the Wi-Fi or the cable). PROVEN 07:38: the phone's Wi-Fi switched off over the tunnel (a timer on the phone switched it back on 75 s later), on mobile data `getprop` answered through Tailscale in 21 s.

> "while I'm still here, please test ADB debugging over tunnel VPN" (Marko, 26.10.2026)
>
> "actually, you can switch to that mode right now. When I go outside and just switch to the other connection, the bugging continues without any interruption. Same as with my remote viewer for my computer on my phone, Mantra Remote, this app needs to detect: - If I'm on Wi-Fi, use the Wi-Fi. - If I'm on the internet, use the tunnel. So we never waste the data in vain" (Marko, 26.10.2026)

- ADB to the Nothing stays on one address (the tunnel's) and goes over the Wi-Fi when the phone is on it, over mobile data only when it is not; debugging goes on through the change. — **Status:** in work.

> "I need the same feature on my other app which allows me to remotely access my phone. Not the camera app, the other app. It's running on this phone." (Marko, 26.10.2026 — the same feature: "resolution scaling, in fractions of 0.9, 0.8, 0.7, 0.6 and 0.5. It saves bandwidth with the new setting, and the status line shows the new number.")

- Resolution scaling in Mantra Remote: 1.0, 0.9, 0.8, 0.7, 0.6, 0.5 of the picture; less bandwidth; the status line shows the scale. — **Status:** asked.

> "Please add a new feature to Mantra remote. I need feedback so I know I actually pressed the button, on this keyboard and on any button." (Marko, 26.10.2026)

- Press feedback on every key of Mantra Remote (Computer Remote), the keyboard keys and every other button: a short vibration and a visible flash, so he knows the press was taken. — **Status:** v15 (26.10.2026): Marko's spec "the symbol ... needs to change color to white for half a second": every Key and every rail key turns its symbol (and its frame) white for 500 ms on a tap or a long press, beside the haptic tick it already had. Tested on the Nothing: SCROLL white in the capture right after the tap, amber again 1 s later. The dock icons are the only buttons without it (the dock closes on a pick).

> "When I press a button, the symbol on the Mantra remote needs to change color to white for half a second, so I know I pressed it." (Marko, 26.10.2026)

> "Also, in the Mantra remote control settings, I want to see all the available devices I can connect to. You also need to enable the Pixel 7 phone, so I can control my computer or the Pixel 7 from my own one app, switching between them." (Marko, 26.10.2026)

- Computer Remote: settings list every device it can reach (the Mac, the Pixel 7, more when found), and the Pixel 7 is driven from the same app (its screen and every touch, as the Camera Remote does over ADB and the tunnel); switching between the Mac and the Pixel in one place. — **Status:** v16 built (not released): ⚙ shows DEVICES: THE MAC (lit while shown) and PIXEL 7 100.70.23.53:5555 (the default), any phone added as "name address:port", a long press removes one; a tap on a phone puts it on the whole screen (scrcpy's picture through the Camera Remote's AdbLink, copied in, light picture 2 Mbit/s 1280 px 24 fps), every touch goes to it, ◀ MAC / BACK / HOME above, a status line with fps and kbit/s; the Mac's link stays up meanwhile. Tested on the Pixel 7 emulator against tools/fake_vnc.py: DEVICES listed, PIXEL 7 opens its screen and tries 100.70.23.53:5555, ◀ MAC back to the Mac with the phone link stopped. Not yet against the real Pixel: it was locked after a battery shutdown (1 %, 5 V charger). The first connection will show "Allow USB debugging?" on the Pixel for this app's own key: Marko presses Always allow.
