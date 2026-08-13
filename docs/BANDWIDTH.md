# SURVIVING 2 Mbit/s

2 Mbit/s is 250 KB per second. That is not a lot, but it is **plenty** for
what you actually need: clicking buttons, typing, starting a recording.

It is **not** enough to watch video smoothly. Accept that and design around it.

---

## THE SINGLE BIGGEST WIN

**Bandwidth is spent on pixels that change.**

A still desktop costs almost nothing. A Zoom gallery view with nine moving
faces costs everything you have and more.

So: **hide the moving things before you need to work.**

- In **OBS**: right-click the preview area → **Disable Preview**.
  OBS keeps recording perfectly. The screen just stops moving.
  This alone can drop your usage by 90%.
- In **Zoom**: minimise the window, or switch to speaker view, or turn off
  incoming video (Zoom still records the real video to disk).
- Set your Mac wallpaper to a **flat solid colour**. Dynamic wallpapers and
  photos cost real bandwidth every time a window moves over them.
- Turn off macOS transparency: System Settings → Accessibility → Display →
  **Reduce transparency** and **Reduce motion**. Big win.

Remember: **the recording quality is decided on the Mac, not on your link.**
Your phone connection being ugly does not make the Zoom recording ugly.

---

## VNC (bVNC) SETTINGS

| Setting | Value | Why |
|---|---|---|
| Colour model | **8-bit / 64 colours** | 3–4× less data. Ugly, readable, fast. |
| Encoding | **Tight + JPEG** | Best compression for UI |
| JPEG quality | **3–5** | Text stays legible below 5 |
| Compression level | **9** | CPU is free, bandwidth is not |
| Remote cursor | **Local drawing** | Cursor doesn't cost a round trip |
| Scaling | **Fit to screen** | Avoid re-fetching on zoom |
| Copy rect | **on** | Free window drags |

You asked for 8-bit colour — this is exactly where it lives. It is not a
compromise here, it is the correct engineering choice.

---

## RUSTDESK SETTINGS

RustDesk is smarter than VNC because it uses a real video codec with adaptive
bitrate. On a bad link it usually beats VNC.

In the session toolbar:

- Quality: **Low** (or Custom → bitrate 0.5–1.0 Mbit/s)
- Codec: **VP9** for still-ish screens, **H264/H265** if the Mac has hardware
  encode and the screen moves a lot
- **Disable audio** — you do not need to hear the Mac to press Record
- **Disable clipboard sync of files**
- Resolution: **1280×720** on the Mac while remote (see below)

## Drop the Mac's resolution while you are away

A 5K Retina screen scaled down to a 6-inch phone is a waste of every bit.

```bash
brew install --cask betterdisplay
# or, no install, from the terminal over Tailscale SSH:
/usr/sbin/system_profiler SPDisplaysDataType | grep Resolution
```

Simplest: keep a second **1280×720** display profile in Display settings and
switch to it before you leave. Roughly 4× less data than 2560×1440.

---

## REALISTIC EXPECTATIONS AT 2 Mbit/s

| Task | Feel |
|---|---|
| Typing in a text field | Instant-ish, ~150–300 ms echo |
| Clicking menus, opening apps | Fine |
| Starting/stopping an OBS or Zoom recording | Fine |
| Dragging windows around | Sluggish, usable |
| Watching the Zoom call live | Slideshow. Don't. |
| Scrubbing video in Premiere/Resolve | No. Use Tailscale SSH + a render script. |

## Measure it

From the phone, before blaming the software:

```bash
# On the Mac, over Tailscale SSH from anywhere:
tailscale ping mantra-mac      # is it direct or relayed?
tailscale status               # "direct" = good, "relay" = slower
```

If it says **relay**, traffic is going through a Tailscale DERP server instead
of P2P. Still works, adds latency. Usually fixes itself; enabling UPnP/NAT-PMP
on the home router helps.
