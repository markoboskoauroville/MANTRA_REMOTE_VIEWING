# RECORDING ZOOM CALLS REMOTELY

The real use case: you are not at home, a call is happening, you want the Mac
to record it.

---

## THE LAZY WAY (recommended)

Do not remote-control OBS at all. **Automate it, then just verify.**

### 1. Turn on the OBS WebSocket server

OBS → Tools → **WebSocket Server Settings** → Enable, set a password,
port 4455. It now listens on the Mac, reachable over Tailscale only.

### 2. Control it with one line from anywhere

Install `obs-cli` on the Mac, then over Tailscale SSH from your phone
(Termux — you already live there):

```bash
ssh marko@mantra-mac 'obs-cli record start'
ssh marko@mantra-mac 'obs-cli record status'
ssh marko@mantra-mac 'obs-cli record stop'
```

That costs a few kilobytes. No screen streaming at all. This is the fastest
possible remote control and it works on 200 kbit/s in a tunnel near Zadar.

### 3. Keep the GUI as the fallback

When something is genuinely wrong, open Mantra Remote and look at the screen.

---

## OBS SETTINGS FOR UNATTENDED RECORDING

| Setting | Value | Why |
|---|---|---|
| Output mode | Advanced | You need the recording tab |
| Recording format | **mkv** (remux later) | Survives a crash or power cut. `mp4` does not. |
| Encoder | Apple VT H264 Hardware | Low CPU, machine stays responsive for you |
| Rate control | CQP / CRF ~20 | Constant quality, predictable |
| Recording path | An external/large disk | Check free space before you leave |
| Automatic file splitting | on, 1 GB or 30 min | Limits damage from a crash |
| **Settings → General → "Automatically record when streaming"** | off | avoid surprises |

**Enable "Keep recording when stream stops"** and turn off any auto-update
prompts — a modal dialog on a headless Mac will block everything.

---

## ZOOM SETTINGS

- Settings → Recording → set the path, **enable "Record a separate audio file
  for each participant"** if you plan to edit later.
- Enable **"Automatically record meeting on local computer"** if it is *your*
  meeting — then you barely need remote control at all.
- Disable **"Ask me to confirm when I leave a meeting"**.
- Disable Zoom auto-updates. An update dialog at the wrong moment kills the run.

## Audio routing — the thing everyone forgets

macOS cannot record system audio natively. For OBS to capture the Zoom call
audio you need a virtual audio device:

```bash
brew install --cask blackhole-2ch
```

Then in Audio MIDI Setup create a **Multi-Output Device** (BlackHole +
your speakers), set that as system output, and add BlackHole as an
**Audio Input Capture** source in OBS.

Test this **at home, before you need it.** Silent recordings are the classic
failure and you only discover them afterwards.

---

## PRE-FLIGHT, EVERY TIME

```bash
./mac/mantra-status.sh
df -h                      # free disk space
ssh marko@mantra-mac 'obs-cli record status'
```
