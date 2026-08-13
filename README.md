# MANTRA REMOTE

**Control my Mac from my Nothing Phone 2a, from anywhere, over 2 Mbit/s.**

Private. No open ports. No public IP needed. No DDNS needed.

---

## THE ONE THING YOU MUST UNDERSTAND FIRST

You asked for **Dynamic DNS** (IP changes → name always points to you).

**Do not use DDNS. It will not work on Telemach.**

Croatian mobile and most modern cable connections put you behind **CGNAT**
(Carrier Grade NAT). That means:

- The public IP you see is **shared with hundreds of other customers**.
- It is **not yours**. You cannot forward a port to it.
- A DDNS name pointing at it is a name pointing at Telemach's router, not your Mac.

DDNS only works if you have a real public IPv4 and can open a port. You almost
certainly do not, and even if you did, an open VNC/RustDesk port on the public
internet is exactly the thing that gets you owned.

**The correct answer is an overlay network.** Both devices dial *out* to a
coordination server, punch through NAT, and then talk **directly, encrypted,
peer to peer**. Your Mac gets a permanent address that never changes even when
Telemach changes your IP five times a day.

That tool is **Tailscale** (WireGuard under the hood).

---

## ARCHITECTURE

```
  Nothing Phone 2a                              Mac at home
  ┌──────────────┐                          ┌──────────────┐
  │ Tailscale    │                          │ Tailscale    │
  │ 100.x.x.x    │                          │ 100.y.y.y    │
  │              │  <=== WireGuard P2P ===>  │ mantra-mac   │
  │ RustDesk /   │      encrypted, direct    │ Screen       │
  │ bVNC client  │      NAT-punched          │ Sharing +    │
  └──────────────┘                          │ RustDesk     │
         │                                  └──────────────┘
         │                                          │
         └──── both dial OUT to Tailscale ──────────┘
               coordination server (control only,
               your pixels never touch it)
```

**Layer 1 — Transport: Tailscale.** Solves dynamic IP + CGNAT + encryption.
**Layer 2 — Pixels: RustDesk over the tailnet** (or macOS Screen Sharing/VNC).

Because Tailscale already gives a stable private IP, RustDesk connects by
**direct IP** — you do not need RustDesk's public servers *or* your own relay
VPS at all. Nothing is published anywhere. This is the quiet setup.

Cost: **0 €.** Tailscale Personal is free for 6 users with unlimited personal
devices (as of the April 2026 pricing change).

---

## PLAN: TWO STAGES

### Stage 1 — Working today, 20 minutes, zero code
Tailscale + macOS built-in Screen Sharing + **bVNC** on Android.
8-bit colour mode. Good enough to click Record in Zoom from a beach in Kukljica.

### Stage 2 — The custom APK, "Mantra Remote"
Fork `rustdesk/rustdesk`, bake your server/key into the build with compile-time
env vars, build the APK in GitHub Actions, install it on the phone.
It opens and it already knows your Mac. Own name, own icon, own colours.

Do Stage 1 first. It is the fallback that always works when Stage 2 breaks.

---

## STAGE 1 — SETUP

### 1. Mac side

```bash
git clone https://github.com/markoboskoauroville/MANTRA_REMOTE.git
cd MANTRA_REMOTE
chmod +x mac/*.sh
./mac/mantra-mac-setup.sh
```

The script installs Tailscale, enables Screen Sharing, stops the Mac from
sleeping, and prints your permanent address.

Then, **critically**, in the Tailscale admin console
(https://login.tailscale.com/admin/machines):

- find `mantra-mac` → `...` menu → **Disable key expiry**

If you skip this, the Mac silently drops off the tailnet in ~6 months while you
are in another country. This is the number one way this setup fails.

### 2. Phone side

1. Install **Tailscale** from Play Store, log in with the same account.
2. Install **bVNC Free** (or bVNC Pro, ~10 €, worth it for gestures).
3. New connection:
   - Type: **VNC**
   - Address: `mantra-mac` (or the `100.x.x.x` shown by the setup script)
   - Port: `5900`
   - Username: your Mac login name
   - Password: your Mac login password
4. Under advanced settings set **Colour: 8-bit** and **Compression: Tight,
   quality 4**. See `docs/BANDWIDTH.md`.

### 3. Test

Turn **Wi-Fi off** on the phone. Use mobile data only. Connect.
If it works on 4G it works from anywhere.

---

## STAGE 2 — CUSTOM APK

See [`rustdesk/BUILD.md`](rustdesk/BUILD.md).

Short version:

- Fork is **not** enough — GitHub forks of public repos are always public and
  can never be made private. To keep it secret you do a **mirror clone** into a
  fresh private repo. Command is in that file.
- Set `RENDEZVOUS_SERVER` and `RS_PUB_KEY` at build time so the APK ships
  pre-configured.
- Rename, re-icon, build with the provided Actions workflow, download APK from
  the run artifacts, sideload.

---

## FILES IN THIS REPO

| Path | What |
|---|---|
| `mac/mantra-mac-setup.sh` | One-shot Mac setup |
| `mac/mantra-status.sh` | Health check — run before you leave the house |
| `mac/com.mantra.remote.awake.plist` | LaunchDaemon, never sleep |
| `docs/BANDWIDTH.md` | Making 2 Mbit/s actually usable |
| `docs/OBS_ZOOM.md` | The recording workflow |
| `docs/SECURITY.md` | Locking it down |
| `docs/TROUBLESHOOTING.md` | When it breaks and you are in Zadar |
| `rustdesk/BUILD.md` | Building the custom Mantra Remote APK |
| `rustdesk/self-host-server.md` | Optional: own relay on a VPS |

---

## BEFORE YOU LEAVE THE HOUSE

Run this. Every time.

```bash
./mac/mantra-status.sh
```

Green across the board = you can leave.
