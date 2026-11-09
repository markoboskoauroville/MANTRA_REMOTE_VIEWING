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

> "There is a bug in our Mantra remote. I can control the Mac with my mouse, but I cannot control the phone. My tapping is not landing, or I don't have the right gestures. I don't know. Can I tap and drag with the mouse? Is that something which is available? Also, please, I don't know how this remote works, but I want to actually control my mouse pointer. I want to see my mouse on the screen, understand? So when I tap on the phone, I can see my mouse is there. I press and I drag, and it acts like I'm on my phone keeping my left mouse button down and moving it at the same time. So that's the idea, to be able to control the mouse over our remote. Please create both a remote keyboard and a remote mouse." (Marko, 26.10.2026)

- On a phone (the Pixel) in Computer Remote: the taps do not land (bug); a visible pointer where the finger is; press and drag = the left button held while moving; a remote mouse and a remote keyboard for the phone, as for the Mac. — **Status:** (1) On the Mac: the taps reached the Pixel (the scrcpy window had no errors), but scrcpy's default mouse is a finger with no pointer. `pixel-screen` (on the PATH) opens the Pixel with scrcpy's UHID mouse and keyboard: Android sees a real USB mouse (its arrow on the phone, press-and-drag = the left button held) and keyboard (the Mac keyboard types into the phone); Left Option or Left Cmd releases the mouse from the window. (2) In Computer Remote v16 (installed on the Nothing, not released): the phone screen has MOUSE (default): a white arrow over the picture, one finger moves it like a trackpad (speed 1.6), a tap clicks at the arrow, a finger held still 350 ms then dragged holds the button (the arrow red) until it lifts; TOUCH: the finger is the finger; ⌨ types into the phone (scrcpy INJECT_TEXT, Enter and backspace as keys); APPS opens the recent apps. Not yet tried on the real Pixel: it is locked since its battery shutdown.

> "On the first screen of my Mantra remote, please also display all available connections. Each one needs a username and password for a computer, but for the phone it is a PIN. So if I enter the PIN there, it connects to my phone and unlocks it with the PIN." (Marko, 26.10.2026)

- The first screen lists every connection: a computer with its user name and password, a phone with its PIN; with the PIN entered there, connecting to the phone also unlocks it. — **Status:** v16 (installed on the Nothing, not released): the first screen (now titled COMPUTER REMOTE, scrolls) shows THE MAC with host, port, VNC password, user, login password, then every phone with its ADDRESS and PIN and CONNECT TO <phone>; a phone opens on the whole screen even without the Mac. The PIN is kept only encrypted (AES-GCM, a key in the Android Keystore; checked on the emulator: the preference holds ciphertext, the digits nowhere). On connecting, a locked phone is woken, `wm dismiss-keyguard` brings the PIN pad, the PIN is typed (digits only) and Enter pressed, then the lock is read again; the status line says when it is locked without a PIN or the PIN did not open it. Android's own limit: right after a restart the phone is off Tailscale until its first unlock, so then it cannot be reached to type the PIN (the Pixel's state now); a way for that case: the Mac, which holds the Pixel on USB, types it (to build if wanted). Not yet tried on the real Pixel.

> "Yes, build that." (Marko, 26.10.2026, about the Mac typing the PIN over its USB cable when the phone cannot be reached after a restart; he also gave the PIN for testing: not used by Claude, a device PIN is his to type)

- The Mac as the way in: when a phone does not answer, Computer Remote asks the Mac (its helper on 8861), which types the PIN into the phone over USB adb. — **Status:** v16 + Dock Switcher v8: after two misses, with a PIN saved, the phone screen asks the Mac (POST 100.79.251.19:8861/unlock, the PIN in the body) at most once a minute; the Mac unlocks the phone on its cable and arms adb 5555 again, then the link connects through Tailscale. The status line says each step. Tested up to the Mac's answer without a PIN; the real unlock waits for Marko to type the PIN in the app.

> "On my Nothing Phone, my app is not updated. It is automatically logging in to the computer. Please make an auto-login option. On the main screen there is a check mark for auto login. I can turn it off so I can choose where I'm logging in, otherwise it goes automatically to one location." (Marko, 26.10.2026)

- AUTO LOGIN, a check mark on the first screen: on, the app goes by itself to one place (the one chosen); off, the first screen stays and he picks the Mac or a phone. — **Status:** v16: the first screen has ☑/☐ AUTO LOGIN and → <place> (a tap steps THE MAC, PIXEL 7, …); a CONNECT makes its place the one auto login goes to; ⚙ has FIRST SCREEN (closes the link, back to the first screen to choose). A phone screen switches Tailscale on itself. Tested on the emulator with tools/fake_vnc.py: on, a restart went straight to the Mac; off, the restart stayed on the first screen.

**27.10.2026:**
> "Please update Mantra Remote and give it the new name Mantra Remote VNC. Then we need to add this feature. First, the settings gear icon should move to the last row of action buttons, on the right side. In settings I can disable all the rows. If I disable all 3, the settings icon should stay as a transparent overlay over the pictures. That way I can click it, go back, and return my rows." (Marko, 27.10.2026)

- The new name: Mantra Remote VNC (the app's label, the first screen's title, the notification, the APK `<N>-mantra-remote-vnc-v<N>.apk`, the site). The package stays `com.mantra.remote`, so the update installs over the old app and keeps its settings. — **Status:** v17 written, not yet built (the build is on the Mac).
- ⚙ at the right end of the last row of keys; ⚙ has ROW 1 KEYS / ROW 2 TOOLS / ROW 3 VIEWS, each ON or OFF (prefs `row_keys_on`, `row_tools_on`, `row_views_on`); ⚙ always sits at the right of the last row still shown; with all three off, ⚙ is a see-through round key over the bottom right of the picture, which opens settings to bring the rows back. — **Status:** v17 written, not yet built or seen on the phone.

> "Please also change the behavior of the Dock button. Right now the macOS Dock is auto-hiding, so the button should unhide it. One press should hide the Dock and the next press should unhide it, so the button toggles." (Marko, 27.10.2026)

- DOCK toggles the Mac's own Dock hiding: a tap sends ⌥⌘D (macOS's "Turn Dock Hiding On/Off"), so the hidden Dock stays shown, the next tap hides it again; works in VIEW ONLY (a key). The phone's dock (the apps on the whole phone) moved to a long press on DOCK, in the row and on the full-screen rail. — **Status:** v17 written, not yet built or seen on the phone.

> "I need a keyboard icon in my action buttons row, and next to it there must be a mouse icon. When I press the mouse icon, I am controlling the mouse on the local computer. The mouse pointer moves as I tap on the screen. Three fingers click, and two fingers click the right mouse button. So any tapping moves the mouse pointer, three fingers click, and two fingers right click." (Marko, 27.10.2026)

- 🖱 beside ⌨ in the second row (MOUSE on the full-screen rail): lit, one finger moves the Mac's pointer to where it touches (no click; an amber ring marks it), a short tap of three fingers = left click, of two fingers = right click, where the pointer stands; spreading, pinching and panning still work (a two/three-finger touch that travels or lasts over 400 ms is a zoom/pan, not a click). Remembered (pref `mouse_mode`); SCROLL and 🖱 switch each other off. — **Status:** v17 written, not yet built or seen on the phone.

> "The symbol for the mouse is good next to the mouse pointer, so I don't need to use 3 fingers if I don't want to. Next to the mouse action, please put a click action with 2 buttons, left and right. So I have the mouse, then I tap to the point, and then I have action buttons for the left and right mouse button." (Marko, 27.10.2026)

- L CLICK and R CLICK beside 🖱, shown while MOUSE is lit (also on the full-screen rail): one press of the Mac's left or right button where the pointer stands (no pointer yet: the middle of the picture). The two- and three-finger taps stay. — **Status:** v17 written, not yet built or seen on the phone.

> "Please shift the login from the row where it is now down to the bottom row, and try to reduce the width of the buttons by reducing their margins. The tab can also be reduced by just putting the tab symbol there, not the whole tab and the whole shebang, just the classic tab from the keyboard." (Marko, 27.10.2026)

- LOGIN moved to the bottom row (⛶ VIEW ⇥ ⇄ DOCK LOGIN ☰ ⚙); TAB ▶ (next tab) is now the keyboard's tab symbol ⇥; the keys of the two lower rows have 3 dp inner margins (was 10) and 3 dp between them (was 4). — **Status:** v17 written, not yet built or seen on the phone.

> "The mouse clicks are not landing. Please test it yourself. please test it on the other screen, with the help of the worker session on the macbook pro screen, so you don't disturb what's going on on that screen by dead screen, i mean the benq screen" (Marko, 27.10.2026)

- Mouse clicks of 🖱 MOUSE / L / R not landing. — **Status:** first fix pushed: VIEW ONLY (remembered) silently drops every click, so 🖱 now switches VIEW off and VIEW switches 🖱 off, a click in VIEW ONLY says so; the click is now move → 30 ms → press → 50 ms → release instead of three events in one instant. To be tested by the Mac session on MON 2 (MacBook) only, never on the BenQ.

> "before doing that, we need to update my app on my phone, which is called mantra remote vnc The mouse now has a left and a right button, but it also needs to drag. Drag means pressing the key and keeping it down until I press drag again. So please give me drag as a third control of the mouse. Also, when I press the left mouse button, the application says my click is not landing. Please open this application via wireless debugging on my phone and test it." (Marko, 30.10.2026)

- DRAG beside L and R in 🖱 MOUSE: a toggle; the first press holds the Mac's left button down where the pointer stands, moving the pointer drags, the second press lets it go. And L CLICK says "not landing": test it on the Nothing over wireless debugging. — **Status:** asked.

## 2.11.2026, 04:40 CT (relayed by the cloud session)

> "my action buttons are below action buttons from Android system. And then instead of pressing action button on my app, pressing action button on Android, and my application closes, or I open the keyboard ... No Mantra application ever drawing behind what already is there in the screen in Android ... Only if I go to full screen then those buttons ... need to be removed and I access them through swiping the finger from outside of the screen ... fix that now."

- v18: the root is padded by the safe area in BOTH modes (MANTRA_MANIFEST system-bars.md §0). Seen on the Nothing Phone 2A in v17: in full screen the right rail (MON, KEYS, SCROLL, EXIT) lay under Android's triangle/circle/square, which the keyboard brings back. — **Status:** v18 installed on the Nothing; setup screen checked (below the clock, left of the buttons); live screen, bottom-button taps and full screen not yet checked (Marko took the phone back).

## 2.11.2026, ~05:10 CT (relayed by the cloud session)

> "the dock button actually opens the dock on my local computer. I want to see the dock but not actually dock from the macOS. I have my own dock switcher, and this dock switcher should be opened. If it's not running on my computer, it needs to start ... Now what's happened if it's not running? Nothing is happening. I need to run it manually."
> "The local session should make door to this computer like API interface, and my application Mantra Remote VNC needs to do what is not possible to do through VNC, like for example running my Start menu items natively through simple commands and being able to control my Start menu applications ... it needs to build this kind of APIs for each of applications I already have ... And in every future app, there should be Python API available so remote control is possible for all of my apps."

- DOCK opens Dock Switcher (started if not running) through the Mac door, never the macOS Dock. — **Status:** queued after the v18 phone test and Tailscale on teacher-vm.
- Mantra Door (one API door on the Mac, Tailscale + token; MANTRA_MANIFEST remote-door.md) + mantra_remote.py; an APPS screen in the app; /health + /api in every star app. — **Status:** queued.

## 2.11.2026 09:15 CT, Marko (mac-session1): v18 to main, v19, every device listed
"I'm saying yes. That Mantra Remote v18 becomes the main version, and fix the cut of MAC address in v19. Yes. Option 2: optional Tailscale or out client device thread pasted in Mantra Remote. I want every device listed, including Chromium virtual machine 2"
(v18 tested on the Nothing 2.11.2026 08:46: ONLINE list, CONNECT, bottom keys above the system bar, MON BENQ→MACBOOK→BENQ. Flaw: in portrait the address beside CONNECT is cut.)

## 2.11.2026 09:20 CT, Marko (mac-session1): the first page is the list of machines
"now update my application. I don't need auto login. Remove the option completely. I don't know what this means. Next to auto login, there is some kind of arrow pointing to the right, and it says Mac. This confuses me. What I want to see on the first page is that Tailscale reads all my machines, and I have a button for each machine. Then I click on the button and get into the machine. When I close the machine, I get the list of machines again. In the list of machines, there will also be an X to close the app completely"
(Before this, 09:17: the Tailscale OAuth client saved in v19 by Marko; the list now comes from the API: MACBOOK PRO and CHROMIUM-VM online; CONNECT, full screen, MON BENQ→MACBOOK→BENQ all pass.)
**Status (2.11.2026, close):** v18 → main, v19 (address), v20 (machine list first page) built, tested on the Nothing, pushed. The OAuth client is saved in the app by Marko; the list comes from the Tailscale API.

## 2.11.2026, Marko (to the chromium-vm session)
"Also, your task is to update the Android application Mantra Remote VNC to be compatible with Chromium virtual machine"
"then you can test this application through Connected. I have a real phone which we call the Pixel 7, and you can drive it and test the application connection and disconnection automatically"

"a real Pixel 7 is ready. Please, can you check if it's working" (Marko, 2.11.2026)

"please add the feature where when the user clicks at the top of the application, it comes to the GitHub page to download the latest version" / "only from the login screen, not inside the remote session" (Marko, 2.11.2026)

## 5.11.2026

"Change my Swift app because I want that this password is only read from this setting dialog, not connected to any text file on my computer. Also remove from my Android application the remote control fit. I don't know what this button does. Actually, it's just enlarging the screen on the virtual machine, which is not necessary if I want to see one by one resolution. Fit can stay, but I cannot pan and scan through the image. When I say fit, it's good. It's big automatically. But enable finger tap holding on the screen or mouse holding on the screen and going through this picture, which is bigger than the screen. Look how this fit mode looks in my Mac OS remote control application. It's stretched. Please don't make it stretched. Make it a normal aspect ratio."

## 5.11.2026 (afternoon), Marko (mac os session)
"the bug remote control app cannot connect to pixel and also pixel park has different commands action buttons and on action row at the top i need to have command only, pure command button"
**Status:** saved first.
**Status (5.11.2026, ~12:30):** the Pixel had v25 (other signing key, could not update to v26/v27) and no VM password: uninstalled, v27 installed, password saved. The VM Xvnc had blacklisted the Pixel address after failed logins. v28: Row 1 ⌘TAB replaced by a pure ⌘ (VM: Super alone opens the dock; Mac: its ⌘ toggle), also on the full-screen rail for the VM.

"Please also text connection. Of virtual machine of our remote control application. You can also use my knocking phone which is on Wi-Fi. to connect to Pixel 7 through Tailscale. So Pixel 7 also has VNC and Pixel 7 kan worden van alle machines. Machines through remote viewing. Check it from another Android. Check it from here." (Marko, 5.11.2026; "knocking phone" = the Nothing Phone)
**Status (5.11.2026, 13:00):** done in v29–v33: Nothing → VM, Nothing → Pixel 7, Pixel 7 → Nothing all tested; Tailscale stays on (v32). Also: the VM Pixel 7 got the real Pixel 7's useful apps (Marko chose "Useful apps").

"please look at my macro recorder application and its macro language. This macro language needs to be built into my remote controller plus API, a Python API to control it remotely. It means that as we go with development, if we see some actions are repeating, we just need to trigger them automatically through macro and use the macro and API from Python to debug the application, pressing buttons without spending too much credit. There is only one command to call to make multiple actions, so we save on our credits. Please update my app to behave this way, and in the manifest, always write that we need to develop systems which save credits from Claude Code to spend as least tokens as possible by developing software which is going to create multiple actions by one command only. Repeating actions should be encoded" (Marko, 5.11.2026)
**Status:** saved first.

"it is done now. Nothing. Phone is on wireless debugging please make some app or mode on Android on both phones so when the system turns off wireless debugging, you turn it on or you use Shizuku somehow to permanently be in debugging mode without disconnecting with the system, which is annoying every day, I'm doing debugging, and I need to go to settings if that's not possible, at least open the settings automatically for me to click every day" (Marko, 5.11.2026)
**Status:** saved first.

**Status (5.11.2026, 13:00):** v30–v33: MacroDoor (Macro Recorder language, POST /api/run, token), tools/mremote.py, macros/vm_dock.ahk; manifest module one-command-many-actions.md. Keep debugging: DebugKeeper (switches back on within seconds, tested on the Pixel), adb 5555 restore after reboot not yet seen.

**Status (5.11.2026, 14:30):** after a real restart of both phones: Tailscale is now the always-on VPN (and the keeper reconnects it), the app paired once with each phone's own wireless debugging (Pixel by sight: its Settings cannot be read by uiautomator on Android 17; Nothing by mremote pairself), and the keeper restored adb 5555 by itself on both. The Pixel has no screen lock (Marko chose None).

## 8.11.2026, 06:00 (Marko, word for word)

now fix my application Right now on my phone I am running Mantra Remote. Please fix it so dock button actually unhide the dock from macOS.And automatically scroll my view in whatever zoom level it is down to the dock and moves my mouse down to the dock. And when I press Dock again, it restores the view.After you done that, look at iTerm in iMac, take a screenshot and explain me why I have 3 windows parallel, vertical, what they means because they are grayed out, how that happened, and remove it. I just need one active.And then we are continuing Santoshi project from yesterday. Uh, before uploading, please cut each song into its own file and then upload each song in the unlisted playlist. And in Santoshi Band, write the message with this content, with the links of the playlist of course, and also with this message.And you need to do the research to write the message. First, write, dear all, this is a recording of the concert. Sound is only from my microphone in the phone. I positioned my phone next to the speaker so it has direct sound and also have ambient sound. In recording, there will not be death, there will not be djembe at all, only very strong vocal and little bit of guitars. So mixer sound, as you already concluded, and I know that very well, I know that very well, needs to have also input inside mixer. We need to have input from some ambient microphones, and it's almost impossible to drag those microphones and cables across the halls because audience is stepping on it, and cables, so long cables are problem. Even to carry, and let alone think about that part and just pull the cable through the hole on the other side. So my suggestion is that we find if somebody has rich uncle to buy this equipment which turns any microphone into wireless microphone. I'm suggesting these budgets, 3 different tiers. Actual Sennheiser microphone wireless, its receiver. So this is a 2-component microphone. It's a microphone— you need to choose some beautiful Sennheiser vocal microphone— and Sennheiser plug which can be detached, and the receiver which goes to the mixer. That's the highest tier. Middle tier is to find third-party cheap wireless transmitter and receiver in pair. There is one from Thomann itself in their brand. There is something similar from some other brand. You need to find out which one. I would like to have USB-C charging battery or replaceable battery. I mean rechargeable battery, AA basically. And, and then find microphone for this kind of recording because those kind of transmitters doesn't have phantom power. So I'm wondering, maybe since electret microphones are measuring microphones, can we find 2 measuring microphones and some dongle, wireless dongle, which can give power to those electret microphones which need less energy and less power, and then use that to have wireless ambient microphones? 2 microphones, or maybe there is some kind of trick Using EIA, I-A-I-A, real-time fast transmission wireless. So idea is to have some wireless microphone on the other side of the hall and plug it into the mixer to record sound from the hall, but have it as little delay as possible so mix is perfect.That means you need to go to Thomann Germany and find all these components and write about it on the, on the WhatsApp. And we need to find Uncle Rich One to get this from Thomann. Until next concert.So in this recording, you can say to them on YouTube there is no mixer sound because of this position of the camera. It mixed both and that sounds decent, but the best combination is to actually mix 2. And you need to find me dynamic microphones, maybe best dynamic, which can work as stereo pair to get this kind of sound from the, from the audience and from the hall. You understand the principle.And in Nano Banana, make few lifestyle images using our, uh, Santoshi band. Look at our website images as references and show in the, in the hall of Shubhich how these microphones are at the other end and recording the audience wirelessly.


## 8.11.2026, 06:45 (Marko, word for word)

And you need to update my remote controller, remote Mantra still. I need another action button called Apps. And this Apps, what it's going to do, is going to just imagine the Mantra remote is like a window to my Mac, which it is. And on whatever level of zoom I am inside my Mantra remote, it's going to scroll to next open app on the screen. So if I press, press up button, it finds, for example, iTerm, and my window is at the middle of iTerm, whatever zoom level is. And then there is a Chrome, and then it goes to the Chrome in my— through my window. It's like automatic pan and scanning. It targets off each open window on the screen so I can switch quickly. If I open multiple windows, I can easily pan and scan to each of them.

windows positions should be memorized across 2 monitors, so restore comes back to the right monitor

## 8.11.2026, 12:40 (Marko, word for word)

Please update mantra remote. It needs to have button to update the app, basically going to the GitHub page. And downloading and manual installing. And the most important buttons to close the dialogue in settings, redesign. At the top of the settings, put circle with X in it. This is closing the dialogue. And in the settings, there must be another button. We just call it back to login. And then it take you back to connect to the different machine. So only two buttons, simple to understand.

after you do updates, find on Wi-Fi my Pixel 7 phone and install the new version
**Status:** saved first.
**Status (8.11.2026):** v38 pushed: circled ✕ at the top of ⚙, ⬇ UPDATE APP (GitHub latest release), ← BACK TO LOGIN; the three old bottom buttons removed. Pixel 7 install after the release builds.

## 8.11.2026, 13:05 (Marko, word for word)

I'm also not sure what scrolling is doing. There is a scroll action button what it should do is should scroll. It should select window under the tap. And then if I'm uh dragging my finger up or down, it emulates the scrolling behavior of mouse button. And also you need to d debug my mouse long press. So if I'm long pressing on the A left. Or right. They cannot be selected. Mm. Same time. They lock in. And that means that when I tap on the screen Tapping with only left mouse button. Or with only right mouse button, but the tapping is happening immediately. On the location of the tap. I get click. Left or click right.
**Status:** saved first.
**Status (8.11.2026):** v39: SCROLL at the finger (pointer first, tick every 20 px); L/R long press = click mode (TAP=L / TAP=R, exclusive), tap clicks immediately at the finger; D unchanged. Pixel 7 install after the build.
**Status (8.11.2026, 12:50):** v39 installed on the Pixel 7 and the Nothing Phone 2a over Tailscale (versionName=39 read back on both). Not yet tried by hand.

## 8.11.2026, 12:52 (Marko, word for word)

update the app and back to login should be at the top
**Status:** saved first.

"and update the app should list current version also" (Marko, 8.11.2026, 12:53)
**Status:** saved first.
**Status (8.11.2026):** v40: both buttons in one row at the top of ⚙; UPDATE APP shows NOW v<installed version>.
**Status (8.11.2026, 13:05):** v40 installed on the Pixel 7 and the Nothing over Tailscale (versionName=40 read back on both).

## 9.11.2026 (Marko, word for word, testing on his Nothing Phone)

how I want to change my Mantra Remote: mouse actions. When I click the mouse to enable the mouse, and when I lock left or right, this is what's happening: when I tap on the screen with locked left, the circle is jumping to that position and after 1 second it clicks automatically.
**Status:** saved first.
**Status (9.11.2026):** v41: L/R long press = real lock (button held down, same as D); a locked finger moves the pointer relatively, a tap does nothing, a long press on the key lets go. MON 1/2 derived from the framebuffer (5584x1964 and 5584x2361).
**Status (9.11.2026):** v41 installed on the Nothing and the Pixel 7 (versionName=41 read back); not yet tried by hand.
