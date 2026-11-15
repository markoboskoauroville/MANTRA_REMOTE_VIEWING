#!/usr/bin/env python3
"""rvctl — drive one of Marko's phones THROUGH Mantra Remote Viewing, with a big visible pointer (Marko, 15.11.2026:
"Every time you control my phones, you are going to open this Remote Viewer ... a local network loop inside the phone,
so nothing is spent over the internet ... I want to see a big mouse pointer ... I need to see feedback on the screen.")

The phone's app runs a Claude Control door (ClaudeControl.kt, port 8790) that acts on the phone ITSELF through its
AccessibilityService and paints a big white arrow that glides to the target, a ripple on a tap, a ring on a long-press,
a trail + "SCROLL ↓" on a swipe/scroll, and a caption ("Claude: tapping NDI"). Whoever watches the phone — the Android
viewer, the Mac viewer, scrcpy — sees it.

rvctl reaches that door over `adb forward` (local loop: USB first, then the LAN/Tailscale adb address), so nothing goes
over the internet. Loopback needs no token.

    rvctl pixel  tap  540 1200 --label "NDI"      a tap, the pointer glides there first
    rvctl nothing swipe 540 1600 540 400 --ms 500 --label "up"
    rvctl pixel  scroll down --amount 900 --label "feed"
    rvctl pixel  longpress 540 1200 --ms 900 --label "app"
    rvctl pixel  text "hello"
    rvctl pixel  key back|home|recents
    rvctl pixel  shot out.png
    rvctl pixel  tree                              the accessibility node tree (text + bounds), reliable
    rvctl pixel  open-viewer                       open the Mac viewer (scrcpy) on this phone over the LAN/adb
    rvctl pixel  health

Phones (edit PHONES): each is an ordered list of adb targets to try. USB serial is fully local; the LAN/Tailscale
address is the fallback. The token (for a non-loopback reach) is ~/.config/mantra-door.token, never printed.
"""
import http.client, json, os, subprocess, sys, time, urllib.error, urllib.request

ADB = os.environ.get("ADB", os.path.expanduser("~/Library/Android/sdk/platform-tools/adb"))
if not os.path.exists(ADB):
    ADB = "adb"
DOOR_PORT = 8790
LOCAL_PORT = 8790  # Mac side of the adb forward

# Each phone: adb targets tried in order (most-local first), the scrcpy window title, and the screen helper script.
PHONES = {
    "pixel": {
        "targets": ["41241FDH2S10SJ", "192.168.1.101:5555", "100.70.23.53:5555"],
        "title": "PIXEL 7 · REMOTE VIEWING",
        "screen": "pixel-screen",
    },
    "nothing": {
        "targets": ["0005534BC000140", "100.67.149.40:5555"],
        "title": "NOTHING · REMOTE VIEWING",
        "screen": "nothing-screen",
    },
}


def _token():
    try:
        return open(os.path.expanduser("~/.config/mantra-door.token")).read().strip()
    except OSError:
        return ""


def _adb(target, *args, **kw):
    return subprocess.run([ADB, "-s", target, *args], capture_output=True, text=True, **kw)


def _pick_target(phone):
    """The first adb target that answers; connect LAN/Tailscale addresses on the way."""
    cfg = PHONES.get(phone)
    if not cfg:
        sys.exit(f"unknown phone '{phone}'; known: {', '.join(PHONES)}")
    for t in cfg["targets"]:
        if ":" in t:  # a network address: connect first
            subprocess.run([ADB, "connect", t], capture_output=True, text=True)
            time.sleep(0.5)
        if _adb(t, "get-state").returncode == 0:
            return t, cfg
    sys.exit(f"{phone}: no adb target answered (tried {', '.join(cfg['targets'])}). Plug it in, or `nothing-adb arm`.")


def _forward(target):
    """Open a local loop to the phone's door; returns the Mac URL base. Nothing leaves the machine but over adb."""
    r = _adb(target, "forward", f"tcp:{LOCAL_PORT}", f"tcp:{DOOR_PORT}")
    if r.returncode != 0:
        sys.exit(f"adb forward failed: {r.stderr.strip()}")
    return f"http://127.0.0.1:{LOCAL_PORT}"


def _req(base, path, data=None, timeout=20, raw=False):
    body = json.dumps(data).encode() if data is not None else None
    req = urllib.request.Request(base + path, data=body, method="POST" if data is not None else "GET",
                                 headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            b = r.read()
            return b if raw else json.loads(b)
    except (urllib.error.URLError, OSError, http.client.HTTPException) as e:
        sys.exit(f"the Claude Control door did not answer ({type(e).__name__}: {e}). Enable it once on the phone:\n"
                 f"  Settings → Accessibility → 'Mantra Remote Viewing — Claude Control' → On")


def _opt(args, name, default=None, cast=str):
    if name in args:
        i = args.index(name)
        return cast(args[i + 1])
    return default


def open_viewer(phone, cfg):
    """Open the Mac viewer (scrcpy) on this phone over the LAN/adb, so Marko watches the big pointer. Local only."""
    helper = cfg.get("screen")
    for cand in (os.path.expanduser(f"~/Developer/MANTRA_REMOTE_VIEWING/scripts/{helper}"),
                 os.path.join(os.path.dirname(__file__), "..", "scripts", helper),
                 helper):
        if helper and (os.path.exists(cand) or cand == helper):
            subprocess.Popen(["/bin/zsh", cand] if os.path.exists(cand) else [cand])
            print(f"viewer opening on {phone} (scrcpy, {cfg['title']})")
            return
    print(f"no screen helper for {phone}")


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 1
    phone, cmd = argv[0], argv[1]
    rest = argv[2:]
    target, cfg = _pick_target(phone)

    if cmd == "open-viewer":
        open_viewer(phone, cfg)
        return 0

    base = _forward(target)

    if cmd == "health":
        print(json.dumps(_req(base, "/health"), indent=2)); return 0
    if cmd == "tree":
        print(json.dumps(_req(base, "/tree"), indent=2)); return 0
    if cmd == "shot":
        out = rest[0] if rest else "/tmp/rvctl.png"
        open(out, "wb").write(_req(base, "/shot", raw=True))
        print(out); return 0

    label = _opt(rest, "--label", "")
    if cmd == "tap":
        x, y = int(rest[0]), int(rest[1])
        print(json.dumps(_req(base, "/tap", {"x": x, "y": y, "label": label}))); return 0
    if cmd == "longpress":
        x, y = int(rest[0]), int(rest[1]); ms = _opt(rest, "--ms", 700, int)
        print(json.dumps(_req(base, "/longpress", {"x": x, "y": y, "ms": ms, "label": label}))); return 0
    if cmd == "swipe":
        x1, y1, x2, y2 = (int(rest[i]) for i in range(4)); ms = _opt(rest, "--ms", 400, int)
        print(json.dumps(_req(base, "/swipe", {"x1": x1, "y1": y1, "x2": x2, "y2": y2, "ms": ms, "label": label}))); return 0
    if cmd == "scroll":
        d = rest[0]; amount = _opt(rest, "--amount", 800, int)
        print(json.dumps(_req(base, "/scroll", {"dir": d, "amount": amount, "label": label}))); return 0
    if cmd == "text":
        print(json.dumps(_req(base, "/text", {"text": rest[0]}))); return 0
    if cmd == "key":
        print(json.dumps(_req(base, "/key", {"name": rest[0]}))); return 0

    print(__doc__); return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
