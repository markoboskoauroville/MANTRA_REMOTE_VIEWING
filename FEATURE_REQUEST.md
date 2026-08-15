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

- [ ] **DECIDE: D1 =** ______

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

- [ ] **DECIDE: D2 =** ______

### D3 — How is the client built?

- **(a)** Native Android (Kotlin) — best control, most work
- **(b)** Flutter — one codebase, decent
- **(c)** Termux + a local web UI in the browser — **fastest to something real**

**Recommendation: (c) for prototyping, (a) for the final.** Marko already
lives in Termux and ships web UIs fast (AGY UI, MA Reader Web). A Flask app on
localhost with the amber terminal skin could be testable this week. Port to a
real APK once the feature set has stopped moving.

- [ ] **DECIDE: D3 =** ______

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
| **F17** | **JPEG quality slider.** 1–9, default 4, with a live estimate of KB per frame. | |
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
