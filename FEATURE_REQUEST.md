# MANTRA REMOTE — FEATURE MENU

> **This document is the external memory of the project.**
> Chat context is small. This file is not. Every decision, every feature, every
> "why we did it this way" lives here. At the start of any new session, point
> the assistant at this file and it knows everything.
>
> **Nothing here is built yet.** This is the menu. Marko orders by number.

---

## HOW TO USE THIS FILE

Each feature has an ID like `F07`. To build one, say: **"Build F07."**
When it works, say: **"F07 done."** — the status table gets updated and we
move on. Order is a suggestion, not a rule. Skip freely.

**Status legend**

| Mark | Meaning |
|---|---|
| ` ` | not started |
| `WIP` | in progress |
| `TEST` | built, Marko is testing |
| `DONE` | tested and accepted |
| `PARK` | deliberately postponed |
| `KILL` | decided against, reason recorded |

---

## PART 1 — DESIGN LANGUAGE

### The feeling

**1980 computer room.** A refrigerator-sized IBM mainframe humming behind glass
in Zagreb, and a small amber terminal in your hand in the jungle. You are not
"using an app". You are **at a terminal, addressing a machine**.

Modern where it counts (smooth, fast, readable), period-correct in its skin.

### Palette — "AMBER PHOSPHOR"

| Token | Hex | Use |
|---|---|---|
| `bg` | `#0A0A0A` | screen body, near-black not pure black |
| `bg_raised` | `#141210` | panels, dropdown surfaces |
| `amber` | `#FFB000` | primary text — the true P3 phosphor amber |
| `amber_dim` | `#8A5F00` | secondary text, inactive labels |
| `amber_glow` | `#FFD37A` | active/focused element |
| `green_ok` | `#33FF66` | status OK, connected |
| `red_alert` | `#FF3B30` | fail, over budget |
| `cyan_data` | `#4FD8E8` | numbers, counters, byte values |
| `rule` | `#2A2320` | box-drawing lines and borders |

Rule: **amber is the voice of the machine, cyan is the voice of the numbers.**
Never more than these. No gradients. No blur. No drop shadows.

### Type

- Everything monospace. `JetBrains Mono` or `IBM Plex Mono` (Plex is
  period-honest — IBM's own).
- Sizes on a strict scale: 11 / 13 / 16 / 22 / 34. Nothing between.
- Generous line-height (1.6). Marko reads better with air.
- **Minimum body size 16px, preferred 22px.** Eye strain is a stated hard
  constraint on this project, not a preference. When in doubt, bigger.
- **Never** thin weights. Regular and Bold only.

### Layout

- Borders drawn with box characters, not CSS boxes:
  `┌ ─ ┐ │ └ ┘ ├ ┤ ┬ ┴ ┼ ═ ║ ╔ ╗`
- Section headers as `── SECTION ──────────────────` rules.
- Labels UPPERCASE, values in normal case.
- Every panel has a header bar with a label, like a real TUI.
- Right-aligned numeric columns. Always. Numbers must line up.
- Optional (F26): faint scanline overlay + very slight text glow. Toggleable,
  default **off** — it costs battery and hurts legibility.

### Voice

The machine speaks in short declarative lines.

```
> PING mantra-mac ......... 42 ms   OK
> LINK ............... DIRECT / P2P
> SESSION ....... 4.2 MB ↓  0.3 MB ↑
```

Not "Successfully connected!" — just `LINK ESTABLISHED`.

### Logo concept — "THE CONVERSATION"

Two shapes in amber on black, in a single square mark:

- **Left / large:** a mainframe cabinet — a tall rectangle, two horizontal
  slots for tape reels, drawn in 2px lines. This is the Mac in Zagreb.
- **Right / small:** a CRT terminal — a smaller rectangle with a rounded
  screen and a stubby base. This is the phone.
- **Between them:** three dots or a dashed line at eye height, suggesting the
  link. The dots animate on the splash screen, left to right, then right to
  left. Two-way.

Wordmark: `MANTRA REMOTE` in Plex Mono Bold, letter-spaced wide, all caps,
with a solid amber block cursor `▊` blinking after the final E.

App icon: just the two shapes and the dashed line, amber on `#0A0A0A`.
It must read at 48px — so the mainframe is essentially a domino shape.

Variant idea for later: the mark drawn purely in box-drawing characters, so
the logo is literally text.

---

## PART 2 — DECISIONS TO MAKE BEFORE BUILDING

These change everything downstream. Answer them first.

### D1 — VNC or RustDesk as the pixel engine? **← THE BIG ONE**

Marko's frame-rate idea (1 FPS, 0.5, 0.2, 0.1) is excellent, and it decides
this question:

| | VNC (RFB) | RustDesk |
|---|---|---|
| How frames move | **Client asks, server answers** (pull) | Server streams video (push) |
| Sub-1 FPS possible? | **Yes, trivially** — just ask less often | No. Codecs assume continuous video |
| "One frame on demand" | Native concept | Fighting the design |
| Quality on moving content | Poor | Much better (VP9/H264) |
| Effort to build custom client | Medium — libvncclient does the hard part | High — Rust + Flutter + NDK |

**Recommendation: build the custom client on VNC.**

The whole point of this app is *extreme frugality*, not smooth video. VNC's
pull model is architecturally exactly what Marko described. Asking for a frame
every 10 seconds is one line of code in VNC and impossible in RustDesk.

Keep RustDesk installed as the **fallback for when you need real speed** on a
good connection. Two tools, two jobs. Don't force one to be both.

- [x] **DECIDED 2026-08-15: D1 = VNC (RFB).** Confirmed by Marko.

#### Consequences of D1 — read before building Phase C

**The good news: the pull model is in the RFB spec itself.**
The client sends `FramebufferUpdateRequest`, the server answers with one
`FramebufferUpdate`. Nothing arrives that was not asked for. So:

- **F13 (frame-rate dropdown)** = a timer controlling how often we send that
  request. 0.1 FPS is a 10-second timer. No hacks, no fighting the protocol.
- **F14 (NO VIDEO)** = stop sending requests entirely. Socket stays open,
  input events still flow. Costs bytes per keystroke and nothing else.
- **F15 (SNAP)** = send exactly one request. This is the protocol's native
  verb, not a trick we invented.
- **F18 (region of interest)** = `FramebufferUpdateRequest` already carries
  x / y / width / height. Ask only for the rectangle around the OBS record
  button. **This is a spec feature we get for free.**
- **F16 (8-bit colour)** = `SetPixelFormat` with BGR233. Client-side request,
  honoured by every server.

Every headline feature in Phase C is a first-class RFB concept. This is the
right protocol for this app.

**The catch: Apple's built-in server does not speak Tight/JPEG.**
macOS Screen Sharing supports Raw, CopyRect, RRE, Hextile, Zlib, ZRLE and
Apple's own ARD encoding — but not Tight, which is the encoding that carries
lossy JPEG. ZRLE is zlib-based and lossless.

Why this matters less than it sounds: lossless RLE compression is excellent on
flat blocks of colour and poor on photographs. The whole bandwidth discipline
in `docs/BANDWIDTH.md` — solid-colour wallpaper, reduce transparency, reduce
motion, 8-bit colour — turns the screen into exactly the flat blocks that ZRLE
compresses beautifully.

So **ZRLE + BGR233 on a flat desktop is close to optimal anyway.** JPEG only
wins on photographic content, which we are already engineering away.

This does mean **F17 (JPEG quality slider) is not buildable against Apple's
server.** Parked until D4 is answered.

### D2 — Where does the "brain" live?

The client needs the Mac to answer questions ("how much disk? is OBS
recording?"). Two options:

- **(a) Pure SSH.** No agent. Client runs shell commands over Tailscale SSH.
  Zero server code, zero maintenance, works day one.
- **(b) A small agent.** A Python/Go daemon on the Mac exposing a tiny JSON
  API on the tailnet. Cleaner, faster, more work.

**Recommendation: start with (a).** SSH is already there, already encrypted,
already reachable. Move to (b) only when SSH round-trips become the bottleneck
— which for this app is probably never.

- [x] **DECIDED 2026-08-15: D2 = SSH only, no agent.** Nothing to install on
  the Mac, nothing to keep alive, nothing that can crash while Marko is in
  another country. Every question is a shell command over Tailscale SSH.

### D3 — How is the client built?

- **(a)** Native Android (Kotlin) — best control, most work
- **(b)** Flutter — one codebase, decent
- **(c)** Termux + a local web UI in the browser — **fastest to something real**

**Recommendation: (c) for prototyping, (a) for the final.** Marko already
lives in Termux and ships web UIs fast (AGY UI, MA Reader Web). A Flask app on
localhost with the amber terminal skin could be testable this week. Port to a
real APK once the feature set has stopped moving.

- [x] **DECIDED 2026-08-15: D3 = Native Android (Kotlin).**

  *Note: this overrides the earlier recommendation of a Termux web prototype,
  and it is the better call for two reasons that only became clear after D1.*

  1. Marko already ships Android apps continuously (TTT, TA-TA Talk, Key
     Tester). The build pipeline the prototype was meant to avoid already
     exists and is warm.
  2. **We have to write our own RFB client anyway** (see STACK below). Once
     that is true, the prototype saves almost nothing — the hard part is the
     protocol, not the platform. Better to write it once, in the final home.

### D4 — Which VNC server on the Mac? *(new, opened by D1)*

- **(a) Apple built-in Screen Sharing.** Zero install, already enabled by
  `mantra-mac-setup.sh`, survives macOS updates, works at the login window.
  No JPEG. **Recommended to start.**
- **(b) RealVNC Server.** Has Tight/JPEG and adaptive quality. Proprietary,
  limited free tier, another moving part to maintain from the jungle.
- **(c) Old open-source Mac servers (Vine / OSXvnc).** Abandoned years ago,
  will not run cleanly on modern Apple Silicon. Do not.

**Recommendation: (a).** Build the whole app against the built-in server. If
real testing shows ZRLE is the bottleneck — and it may well not be — revisit
using actual byte measurements from F12 rather than guesses.

- [x] **DECIDED 2026-08-15: D4 = Apple built-in Screen Sharing.** Zero install,
  already enabled by `mac/mantra-mac-setup.sh`, survives macOS updates.
  Revisit only with real byte measurements from F12.

### D5 — Big-screen access: which route? *(new, opened by Phase G)*

- **(a) Fork scrcpy to work without ADB.** **Not possible.** See Phase G.
- **(b) Fix scrcpy properly** — diagnose why it fails on the Nova TV machine.
  Cheap to test, and if it works it costs nothing to maintain.
- **(c) Funnel + noVNC (F39–F41).** Browser only. Works anywhere with
  internet — hotels, cafes, airports. **Not at Nova TV: no internet there.**
- **(d) USB tethering + MJPEG in the browser (F45–F48).** The Nova TV answer.
  No internet, no install, no admin, no ADB. **Recommended for work.**

**Recommendation: run the F45 tethering test first (60 seconds), then build
(d) for work and (c) for travel.** They solve different problems: (b) shows the
phone on one specific PC; (c) shows the *Mac* on every PC, forever.

- [ ] **DECIDE: D5 =** ______

### DIAGNOSTIC: why does scrcpy fail at Nova TV?

Run in this order. The answer changes the plan.

1. On the phone: Settings → About → tap Build number 7× → back → System →
   Developer options → **USB debugging ON**.
   *(This is the single most common cause. scrcpy cannot work without it.)*
2. On the PC: download the **scrcpy win64 zip** and just unzip it to the
   Desktop. **There is nothing to install** — `adb.exe` is inside the zip.
   If the belief was that ADB must be installed, that was the misunderstanding.
3. Open `cmd` in that folder and run `adb devices`. Read the result:

| Result | Meaning | Fix |
|---|---|---|
| device listed | Working | Just run `scrcpy.exe` |
| `unauthorized` | Phone has not trusted the PC | Unlock phone, accept the dialog |
| empty list | **USB driver missing** — needs admin | Blocked. Go to (c) |
| `adb.exe` blocked / won't run | AppLocker or Defender policy | Blocked. Go to (c) |
| phone not in Device Manager at all | USB device-class policy allows MTP only | Blocked. Go to (c) |

4. If it works, useful flags for tired eyes and rotation:
   `scrcpy.exe --capture-orientation=90 --stay-awake --window-title="MANTRA"`
   (scrcpy 3.0+ replaced `--lock-video-orientation` with
   `--capture-orientation`, and added `--angle` for arbitrary rotation.)

Record the outcome here: ______


---

## PART 2B — THE STACK (locked by D1–D4)

### The RFB client: we write it ourselves

**This is the central engineering decision and it follows directly from D1.**

Off-the-shelf VNC libraries (libvncclient, and everything built on it including
bVNC) manage the update loop *for you*. They request frames automatically, as
fast as they can, because that is what every other VNC client wants.

**Our entire product is deciding exactly when a frame is requested, and for
which rectangle.** A library that hides `FramebufferUpdateRequest` hides the
one thing we are building. So we implement RFB directly in Kotlin.

Good news: RFB is a small, old, stable, well-documented protocol. We need a
fraction of it.

**Minimum viable RFB — implement in this order:**

| Piece | Notes |
|---|---|
| Version handshake | Trivial. `RFB 003.008` |
| Security type 2 (VNC auth) | DES challenge-response. ~40 lines. See auth note below |
| `ClientInit` / `ServerInit` | Gets screen size and pixel format |
| `SetPixelFormat` | This is **F16**. BGR233 for 8-bit |
| `SetEncodings` | Declare what we can decode |
| `FramebufferUpdateRequest` | **The heart of the app.** F13, F14, F15, F18 |
| `KeyEvent` / `PointerEvent` | Phase D input |
| Decoder: **Raw** | Mandatory fallback. Easiest |
| Decoder: **CopyRect** | Nearly free, huge win on window drags |
| Decoder: **Zlib** | Inflate, then raw pixels. **Start here** — much simpler than ZRLE |
| Decoder: **ZRLE** | Better compression, more work (64×64 tiles, palettes, RLE). Add after the app works |
| Decoder: Hextile | Optional fallback |

Skip RRE, CoRRE, and all Apple ARD-specific encodings.

### Authentication — the one real trap

macOS Screen Sharing defaults to **Apple's security type 30** (Diffie-Hellman
against your macOS account password). Implementing that in Kotlin is genuinely
unpleasant.

**Escape hatch:** on the Mac, System Settings → General → Sharing → Screen
Sharing → *Computer Settings…* → tick **"VNC viewers may control screen with
password"** and set one. That enables **standard RFB security type 2**, which
is a simple DES challenge-response we can implement in an afternoon.

Caveats, both acceptable here:
- The legacy VNC password is limited to 8 characters and the scheme is weak.
- **It is only reachable over the tailnet.** Nothing on the public internet can
  see port 5900. The weak password is defence-in-depth, not the front door.
- Use a password that is *not* your Mac login password.

This should be folded into **F04** and added to `mac/mantra-mac-setup.sh`.

### Libraries

| Need | Choice | Why |
|---|---|---|
| Language | Kotlin | D3 |
| Min SDK | 34 (Android 14) | Nothing Phone 2a. No legacy baggage |
| UI | Jetpack Compose | The amber TUI look is easy with custom Canvas + Text |
| SSH | **sshj** | Maintained, modern crypto, Ed25519. (Original JSch is abandoned; if it must be JSch, use the `mwiede/jsch` fork) |
| RFB | **our own** | See above |
| Byte metering | `TrafficStats.getUidRxBytes()` / `getUidTxBytes()` | Per-app UID, built into Android, free |
| Persistence | DataStore | Budget ledger, reset date, profile |
| Fonts | IBM Plex Mono, bundled | Period-honest, and IBM is the whole logo concept |
| Transport | none — the Tailscale app provides it | We just dial `100.x.x.x`. No SDK, no VPN permission needed in our app |

### What the Mac side needs (D2 = SSH only)

No agent. `mac/mantra-mac-setup.sh` gains:

- enable the legacy VNC password (auth escape hatch above)
- confirm Tailscale SSH is on
- install `obs-cli` for Phase E
- a `~/.mantra/` folder holding small shell scripts the phone calls by name,
  so the phone sends `mantra status` rather than a long shell one-liner

That last point matters: **the phone should send short verbs, not commands.**
Fewer bytes, and the Mac side can be fixed by editing a script instead of
rebuilding the APK.

---

## PART 3 — THE MENU

### PHASE A — SIGNS OF LIFE
*No pixels. Cheapest possible things. Proves the whole chain works.*

| # | Feature | Why it's here |
|---|---|---|
| **F01** | **PING button.** Big amber button at top. TCP-connect to `mantra-mac:5900` and `:22`, show round-trip in ms and `SERVER ALIVE / NO ANSWER`. Costs bytes, not kilobytes. | Marko's own idea, and the correct first feature. Everything else assumes reachability; this proves it in isolation. |
| **F02** | **Link quality readout.** Below ping: `DIRECT` or `RELAY (fra)`, from `tailscale status`. Plus rolling min/avg/max ms over last 10 pings. | Tells you *why* it's slow before you blame the app. |
| **F03** | **The shell.** App skeleton with the amber terminal look — header bar, status strip, box-drawn panels, the font, the palette. No features, just the room. | Build the aesthetic once, early. Retrofitting a look is misery. |
| **F04** | **Connection profile, baked in.** Host, port, user, key — compiled in, no setup screen. App opens knowing your Mac. | The "it's only for my computer" requirement. |
| **F05** | **Health panel.** One button runs `mantra-status.sh` over SSH, renders the result as amber terminal output. Disk free, OBS running, on AC, sleep disabled, FileVault. | Turns the existing script into a screen. Almost free. |

### PHASE B — THE BUDGET
*Before spending bandwidth, learn to count it. 100 GB/month is the constraint.*

| # | Feature | Notes |
|---|---|---|
| **F06** | **Live byte counter.** Always-visible strip: `↓ 4.21 MB  ↑ 0.34 MB` for the current session. Android `TrafficStats` per-UID — accurate and cheap. | The verbose readout Marko asked for. |
| **F07** | **Rate meter.** Instantaneous kbit/s down and up, updated every second, next to the totals. | Lets you *see* the cost of disabling the OBS preview. Very satisfying. |
| **F08** | **Cumulative ledger.** Persistent total since a chosen reset date. `SINCE 2026-08-15 ......... 3.42 GB`. | The real budget tracker. |
| **F09** | **Reset + date picker.** Set the billing-cycle start date. Reset button with a confirm. | Matches the SIM billing cycle. |
| **F10** | **Budget bar.** Set cap (100 GB). Show used / remaining / **% of month elapsed vs % of data used**. Amber when tracking, red when data is running ahead of the calendar. | The comparison is the useful part, not the raw number. |
| **F11** | **Budget alarms.** Warn at 50/75/90%. At 95%, refuse to open a video session without a hold-to-confirm. | A guard rail that has to be deliberately overridden. |
| **F12** | **Per-mode accounting.** Attribute bytes to what spent them: PING / STATUS / SCREEN / FILE / CLAUDE. Simple table. | Shows where the gigabytes actually go. Usually surprising. |

### PHASE C — PIXELS, RATIONED
*The core idea. Frame rate as the primary control.*

| # | Feature | Notes |
|---|---|---|
| **F13** | **Frame-rate dropdown.** The centrepiece. `NO VIDEO / 0.1 / 0.2 / 0.5 / 1 / 2 / 5 / 10 / 15 / 25 FPS`. Show the estimated cost of each option in the list itself, e.g. `2 FPS ~ 90 KB/s`. | With VNC (D1=VNC) this is genuinely just how often you send a FramebufferUpdateRequest. Clean. |
| **F14** | **NO VIDEO mode.** Screen stays dark/blank. Input still works — you can type and click blind. Byte cost approximately zero. | Better name than "no images", as Marko said. Sounds deliberate, because it is. |
| **F15** | **SNAP button.** In NO VIDEO mode, request exactly **one** frame. Costs one JPEG, maybe 40 KB. | The killer feature. 95% of remote work is: look once, click twice, look once. This is a whole session for ~100 KB. |
| **F16** | **Colour depth selector.** `8-BIT (64c) / 16-BIT / FULL`. Default 8-bit. | 3–4× saving. Ugly and correct. |
| **F17** | `PARK` **JPEG quality slider.** 1–9, default 4, live KB/frame estimate. | Blocked: Apple's server has no Tight/JPEG. Unblocks only if D4 = RealVNC. |
| **F18** | **Region of interest.** Drag a rectangle; only that part of the screen updates. The rest freezes. | Watching *only* the OBS record button costs almost nothing. This may be the single biggest saving in the whole app. |
| **F19** | **Auto-throttle.** If measured throughput drops below a threshold, step the FPS down automatically and say so in the status strip. | |
| **F20** | **Freeze / thaw.** One tap suspends all frame requests without dropping the session. Resume instantly. | For when you put the phone in your pocket mid-session. |

### PHASE D — HANDS
*Input, made usable on a 6.7" screen.*

| # | Feature | Notes |
|---|---|---|
| **F21** | **Full-screen immersive**, both orientations, rotation lock toggle. | An accidental rotate = a full redraw = seconds of bandwidth. Lock matters. |
| **F22** | **Trackpad mode.** Bottom third of the screen as a relative trackpad instead of absolute touch. Far more precise than poking at a scaled desktop. | |
| **F23** | **Macro keys row.** Amber function-key strip: `⌘Q ⌘W ⌘S ⌘TAB ESC ⏎` + a user-defined row. | Marko already built exactly this in the TTT keyboard. Same pattern, same muscle memory. |
| **F24** | **Voice → remote typing.** Dictate into the phone, text is typed on the Mac. | He dictates everything anyway. Reuses the AssemblyAI path he already has. |
| **F25** | **Clipboard bridge.** Two-way text clipboard, manual push/pull buttons (not automatic — automatic costs bytes). | |
| **F26** | **CRT skin toggle.** Scanlines + phosphor glow, off by default. | Pure joy. Zero function. Build it last in this phase and enjoy it. |

### PHASE E — THE JOB
*OBS and Zoom, without streaming pixels.*

| # | Feature | Notes |
|---|---|---|
| **F27** | **REC panel.** Big button: START / STOP recording via `obs-cli` over SSH. Shows state, elapsed time, file size, free disk. **No video needed at all.** | The actual reason this app exists. Costs a few hundred bytes per poll. |
| **F28** | **Scene switcher.** List OBS scenes, tap to switch. | |
| **F29** | **Audio level meter.** Poll OBS for input levels, draw as an amber bar. | Guards against the silent-recording disaster. Worth more than video. |
| **F30** | **Zoom control.** Join a meeting from a pasted link, mute/unmute, leave — via AppleScript over SSH. | |
| **F31** | **Watchdog.** Background check every N minutes while recording. Notify the phone if OBS dies, disk fills, or the recording stops unexpectedly. | Very cheap, very high value. This is what lets you stop watching. |

### PHASE G — THE BIG SCREEN
*Marko's eyes hurt on a 6.7" screen. This is a hard accessibility requirement,
not a nice-to-have. Any borrowed monitor should become a window onto the Mac.*

#### The scrcpy question, answered

**A fork of scrcpy cannot avoid ADB. This is architectural, not a bug.**

scrcpy works by using adb to push `scrcpy-server.jar` into `/data/local/tmp`
and running it as the **shell user**. The privileged screen capture it performs
is granted *by ADB's shell UID*. A normal Android app does not have it.

Without ADB, an app can only capture via `MediaProjection` (consent dialog
every time), and it can only get that stream off the device through a
**network socket**. USB file transfer is MTP — a file-browsing protocol with
no mechanism for a live stream. You cannot open a socket over MTP.

So "send the picture over the USB cable without ADB" is not a thing that can
be built. Not by us, not by anyone. Close that door.

#### CONSTRAINT UPDATE 2026-08-15 — the Nova TV edit stations have NO INTERNET

Internet is disabled on the editing workstations by policy. This kills F39–F41
**for Nova TV specifically**. They remain valid for hotels, cafes, airports and
anywhere else with a browser and a connection — see their entry below.

For Nova TV, the constraints are now:

| Constraint | Consequence |
|---|---|
| No internet | Nothing cloud-based. Nothing through Tailscale |
| No software may be installed | **This is the binding constraint.** Not video quality |
| No admin rights | No drivers, no MSI, possibly no unsigned `.exe` |
| USB works for file transfer | So the physical port is open and enumerating |
| ADB blocked / unavailable | scrcpy is out (see above) |

#### On SRT and NDI — right instinct, wrong layer

Both are **transport** protocols. They move video *across a network that
already exists*. Neither one creates connectivity. With no network path
between the phone and the workstation, both are irrelevant — they are the
cargo, not the road.

And on the licensing question directly:

- **SRT** — genuinely open source, MPL-2.0, from Haivision. Good protocol.
- **NDI** — **not** open source. Proprietary to Vizrt NDI AB, royalty-free SDK
  under a license agreement, trademark requirements attached. Also designed for
  gigabit LAN; it is deliberately bandwidth-hungry.

But the disqualifier for both is the same and it is fatal:
**they need a receiver application installed on the PC.** VLC, OBS, NDI Studio
Monitor. Installing software is exactly the thing that is blocked.

**The only receiver already installed on that workstation is the web browser.**
Everything below follows from that single fact.

#### The road: USB tethering makes a network where there was none

Android's **USB tethering** turns the phone into a USB Ethernet device (RNDIS).
Windows 10/11 carry that class driver in-box, so it usually appears without an
install prompt. The PC gets an IP, the phone gets `192.168.42.129`, and there
is now a real IP link — **with no internet on it, which is fine, because we
only need phone-to-PC.**

Critically: USB tethering is a normal user toggle. **No ADB. No developer
options. No admin. No install.**

```
  Phone (MediaProjection -> JPEG -> HTTP server)
        |
        |  USB cable, RNDIS, ~480 Mbit/s, no internet
        v
  Workstation browser  ->  http://192.168.42.129:8080
        |
        v
  Big monitor. Eyes happy.
```

Note the reversal from the rest of this project: **bandwidth here is enormous
and free.** No frugality needed. Send big, sharp, high quality frames.

| # | Feature | Notes |
|---|---|---|
| **F45** | **USB tether link check.** Document and test the RNDIS path. Phone: Hotspot & tethering → USB tethering ON. PC: `ipconfig` shows a new adapter. | 60-second test, installs nothing. **Do this first.** |
| **F46** | **MJPEG broadcast mode in Mantra Remote.** MediaProjection → ImageReader → JPEG → `multipart/x-mixed-replace` over a tiny embedded HTTP server. An `<img>` tag renders it natively in any browser. | Dead simple, no JS, no codec, no install. Ship this first. |
| **F47** | **Zero-install landing page.** The phone serves one HTML page: the image, a rotation button, a size selector, BIG mode. Amber on black. | The entire PC-side "app" is a URL. Nothing to install, nothing to leave behind. |
| **F48** | **Rotation button.** CSS `transform: rotate(90deg)` in the served page. Portrait phone, landscape monitor — the mismatch Marko described, fixed in one line. | Also add fit-to-width / fit-to-height / 1:1. |
| **F49** | `LATER` **WebRTC broadcast.** Smooth H.264 instead of JPEG frames. Signalling over the same local HTTP server, host ICE candidates only, no STUN/TURN needed on a direct link. | Better, but browsers gate parts of WebRTC behind secure contexts and self-signed HTTPS means cert warnings. MJPEG has none of these problems. Only build if F46 proves too choppy. |
| **F50** | `LATER` **Input back to the phone.** Type on the workstation keyboard, characters go to the phone. A WebSocket to the phone app plus an InputMethod or Accessibility service. | Marko said picture is the minimum and typing is a bonus. Correct priority. Do not let this delay F46. |

**Note the deliberate choice:** MJPEG beats SRT, NDI, and WebRTC here **not
because it is technically better, but because it is the only one whose
receiver is already installed.** On a locked machine that beats every quality
argument.

#### If USB tethering is also blocked

Fallback: the phone writes a JPEG to shared storage every N seconds; the PC
watches the folder over MTP and displays it. Roughly 0.5–1 fps, laggy, and MTP
caching makes new files appear unreliably. It also still needs *something* to
render on the PC.

It is genuinely bad. But at ~1 fps it is arguably enough to read a big
transcript while dictating — which is exactly the frugal philosophy of the
rest of this project. Keep in the back pocket. Do not build until F45 fails.

#### The internet-available variant (hotels, cafes, airports)

**Where there IS internet, this remains the best answer:**

**Marko does not need to see his phone. He needs a big view of his Mac.**

At a corporate workstation there is already a big monitor, a browser and an
internet connection. The phone is a detour. Cut it out:

```
  Corporate browser  ──HTTPS/443──>  Tailscale Funnel  ──>  noVNC  ──>  Mac :5900
        (no install, no admin, no USB, no ADB)
```

| # | Feature | Notes |
|---|---|---|
| **F39** | **noVNC on the Mac.** `websockify` + noVNC in front of port 5900. Gives a browser-based VNC client at full monitor size. | Pure web. Nothing to install on the borrowed machine. |
| **F40** | **Tailscale Funnel + auth.** `tailscale funnel` publishes it on `https://mantra-mac.<tailnet>.ts.net` — reachable from *any* browser, on 443, which corporate firewalls allow. Caddy in front with **basic auth**. | See the warning below. Auth is mandatory, not optional. |
| **F41** | **Funnel kill switch, from the phone.** One button in Mantra Remote: `FUNNEL ON` / `FUNNEL OFF` over SSH. Default OFF. Auto-off after N hours. | This is what makes F40 acceptable. Public only while Marko is actually sitting at the borrowed machine. |
| **F42** | **Rotation control in the browser view.** Manual 0/90/180/270 button. CSS transform, costs nothing. | Marko holds the phone portrait but the Mac is landscape — the mismatch he described. |
| **F43** | **BIG mode.** A layout profile with minimum 22px type, maximum contrast, no thin weights, generous spacing. Toggle in the phone app *and* the browser view. | Amber-on-black is already a good high-contrast, low-blue-light choice for tired eyes. Lean into it. |
| **F44** | `PARK` **Phone screen broadcast.** MediaProjection → MJPEG over HTTP, published through Funnel, viewed in a browser. | Only if a phone-only app genuinely needs to be seen. Tailscale on Android does not expose Funnel controls, so this is harder than it looks. Do not start here. |

#### Security warning on F40 — read it

Funnel makes a service **publicly reachable on the internet**. This is a real
departure from the "nothing is public" posture in `docs/SECURITY.md`, and it
must be handled deliberately:

- **Basic auth in front, always.** A long password, not the 8-character VNC one.
- **Default OFF.** F41 exists precisely so the door is shut by default.
- Funnel URLs are guessable-ish and get scanned. Assume it will be found.
- If it is ever left on and unauthenticated, someone has your Mac.

Build **F41 before F40 ships.** Same rule as F38 before F37.

#### One practical note

Running personal remote-access tooling on a Nova TV workstation may sit
outside their acceptable-use policy, and corporate networks often do TLS
inspection that will see it. Worth knowing where the line is before relying
on it daily.


### PHASE F — THE MACHINE THAT BUILDS ITSELF
*The real vision. See Part 4 before building any of this.*

| # | Feature | Notes |
|---|---|---|
| **F32** | **Claude Code terminal.** A text panel that runs `claude -p "<prompt>"` on the Mac over SSH and streams the output back as amber text. Pure text — costs almost nothing. | This alone is a huge unlock, independent of everything else. |
| **F33** | **Voice → prompt.** Hold to record, AssemblyAI transcribes, text goes to F32. Talk to the Mac from the jungle. | Marko's entire working style, made native. |
| **F34** | **Job queue.** Prompts go into a queue on the Mac. Survives disconnection. Check back later for results. | Essential — you will lose signal mid-build. Fire and forget. |
| **F35** | **Build & diff review.** After Claude Code works, show `git diff --stat` and the commit message as amber text. Approve or discard from the phone. | **Never auto-merge.** A human gate, always. |
| **F36** | **Remote build trigger.** Approving a change pushes to GitHub, fires the Actions workflow, and reports back when the APK artifact is ready. | |
| **F37** | **In-app update.** Download the new APK from the Actions artifact and prompt to install. Keep the previous APK on disk as a rollback. | |
| **F38** | **PANIC / ROLLBACK.** One button: reinstall the last known-good APK and `git revert` the Mac side to the last known-good commit. | See Part 4. This must exist *before* F37 ships. |

---

## PART 4 — THE SELF-DEVELOPING APP: HONEST NOTES

The vision is real and mostly buildable today. Every piece already exists in
Marko's stack: Termux, AssemblyAI, GitHub Actions, Claude Code, Tailscale.
Nothing has to be invented. It has to be *wired*.

The loop:

```
  voice note (phone)
        ↓  AssemblyAI
  text prompt
        ↓  SSH over Tailscale
  claude -p on the Mac in Zagreb
        ↓
  code change + commit on a branch
        ↓  human approval from the phone   ← the important arrow
  push → GitHub Actions → APK artifact
        ↓
  phone downloads and installs
```

### The one thing that will kill it

**The app modifies the thing you use to reach the app.**

If a self-generated change breaks the client, you lose the ability to send the
fix. You are in the Amazon and your only tool for repairing the machine is the
broken machine. This is the whole risk, and it is not theoretical.

### Four rules that make it safe

1. **The rescue channel is sacred.** Plain Tailscale SSH from Termux must
   *never* be touched by any generated change. It is not a feature of the app.
   It is the ladder out of the pit. Keep it dumb, keep it separate, never
   refactor it.

2. **Never write to `main`.** Claude Code works on `auto/<timestamp>` branches.
   A human on a phone merges. F35 before F36, always.

3. **Two APKs, always.** `mantra-remote.apk` and `mantra-remote-last-good.apk`
   on the phone's disk. F38 before F37. The rollback button must exist before
   the update button does.

4. **The Mac agent has a supervisor.** Whatever runs the job queue is under
   `launchd` with `KeepAlive`, and a generated change can never modify its own
   plist. Something outside the loop must always be able to restart the loop.

### What I'd actually build first

**F32 alone, standing by itself, before anything else in this phase.**

A text box on your phone that runs Claude Code on your Mac and shows the
output. No self-modification, no auto-build, no update mechanism. Just a
remote terminal into Claude Code.

That is ~90% of the value of the whole vision, at ~5% of the risk. Live with
it for two weeks. You will discover what you actually want the loop to do —
and it will not be what you think today. Then build F34–F38 around what you
learned.

The self-updating part is the exciting part. It is also the part that can
strand you. Earn it.

---

## PART 5 — OPEN QUESTIONS FOR MARKO

1. **D1, D2, D3** above — the three architecture calls.
2. The SIM is 100 GB — is that per calendar month, or a rolling 30 days?
   (Changes how F10's projection maths works.)
3. Is the Mac an Apple Silicon laptop or a desktop? Affects sleep behaviour
   and whether the lid matters.
4. Is `mantra-mac` the final hostname? It gets baked into the client.
5. Amber only, or is a second theme wanted later (green P1 phosphor)?

---

## CHANGELOG

| Date | Change |
|---|---|
| 2026-08-15 | Document created. Features F01–F38 drafted. Decisions D1–D3 open. |
| 2026-08-15 | **D1 = VNC**, decided by Marko. Consequences written up. D4 opened (which VNC server). F17 parked. |
| 2026-08-15 | **D2 = SSH only. D3 = Native Android/Kotlin. D4 = Apple built-in.** All decisions closed. STACK section added. Ready to build F01. |
| 2026-08-15 | **Phase G — THE BIG SCREEN** added (F39–F44). scrcpy-without-ADB ruled out as architecturally impossible. D5 opened + scrcpy diagnostic. Eye strain recorded as a hard design constraint. |
| 2026-08-15 | **No internet at Nova TV edit stations.** F39–F41 rescoped to travel only. SRT/NDI assessed and ruled out (wrong layer + need an installed receiver; NDI also proprietary). **F45–F50 added: USB tethering + MJPEG to the browser.** Binding constraint recorded as *no software may be installed*, not bandwidth. |

### 9.11.2026, v41: mouse lock and monitor layout (Marko's request in momentaryupdates.md)
Locked L/R = button held (drag lock), relative finger movement, tap does nothing, long press on the key releases. MON 1/2 from the framebuffer size (both Mac arrangements). Status: TEST.
