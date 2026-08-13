# PHONE SIDE — NOTHING PHONE 2A

## Install

1. **Tailscale** (Play Store) — log in with the same account as the Mac.
   Then: Settings → Apps → Tailscale → Battery → **Unrestricted**.
   Android will otherwise kill it in the background and you will get
   "cannot connect" from a train.

2. **bVNC** (Free or Pro) — for Stage 1.
   Or **Mantra Remote** (your custom APK) — for Stage 2.

3. **Termux** — you already have it. Add:
   ```bash
   pkg install openssh
   ssh marko@mantra-mac
   ```
   This is your lightest, fastest, most reliable path in. Use it first.

## bVNC connection profile

| Field | Value |
|---|---|
| Connection type | VNC |
| Address | `mantra-mac` (or `100.x.x.x`) |
| Port | 5900 |
| Username | your Mac short username |
| Password | your Mac login password |
| Colour | **8-bit (64 colours)** |
| Compression | Tight, quality 4, level 9 |
| Keep screen on | yes |

## Gestures worth learning

| Gesture | Action |
|---|---|
| One-finger drag | Move the pointer |
| Tap | Left click |
| Two-finger tap | Right click |
| Two-finger drag | Scroll |
| Pinch | Zoom the view |
| Three-finger tap | Show the soft keyboard |

## Orientation

**Landscape** wastes far fewer pixels on a 16:10 Mac screen. Portrait is only
better for reading a terminal.

Lock the rotation once you are in — an accidental rotate forces a full screen
redraw, which on 2 Mbit/s costs you several seconds.
