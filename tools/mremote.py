#!/usr/bin/env python3
"""mremote: drive Mantra Remote VNC on a phone with one command (v30, Marko 5.11.2026: "There is only one command to
call to make multiple actions, so we save on our credits").

The phone's app listens on port 8841 (Tailscale and the phone itself only). A script is the Macro Recorder's language
(AutoHotkey v2: Click, Send, SendText, Sleep, Loop, MouseMove, MouseClickDrag) plus Press "WORD", Back and Log; the
whole reference is at the top of android/.../MacroDoor.kt.

    mremote state [phone]                       what is on screen, the buttons, the debugging switches
    mremote run [phone] 'Press "CHROMIUM-VM"' 'Sleep 3000' 'Press "⌘"'     one line per argument, all in one call
    mremote run [phone] -f macros/open_dock.ahk  a saved macro
    mremote do [phone] '...'                     run, then a small screenshot to /tmp/mremote.png (one look)
    mremote shot [phone] [out.png] [width]       the app's screen, small (default 360 px wide)
    mremote keep [phone]                         once per phone over adb: lets the app switch debugging back on itself

phone: pixel (default), nothing, or a Tailscale address.  From Python:  from mremote import Remote; Remote("pixel").run(...)
"""
import json, os, subprocess, sys, urllib.request

PHONES = {"pixel": "100.70.23.53", "nothing": "100.67.149.40"}
PORT = 8841
PKG = "com.mantra.remote"
TOKEN_FILE = os.path.expanduser("~/.config/mantra-door.token")   # the Mac's Mantra Door token (modules/remote-door.md)


def _token():
    try:
        return open(TOKEN_FILE).read().strip()
    except OSError:
        return ""


class Remote:
    def __init__(self, phone="pixel"):
        self.host = PHONES.get(phone, phone)

    def _req(self, path, data=None, timeout=20):
        req = urllib.request.Request(f"http://{self.host}:{PORT}{path}", data=data, method="POST" if data else "GET",
                                     headers={"X-Mantra-Token": _token()})
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.read()

    _get = _req

    def state(self):
        return json.loads(self._get("/api/state"))

    def run(self, *lines):
        """Every line in one request; the answer says ok, what was done, the error and the time."""
        return json.loads(self._req("/api/run", "\n".join(lines).encode(), timeout=600))

    def shot(self, out="/tmp/mremote.png", width=360):
        open(out, "wb").write(self._get(f"/api/shot?w={width}"))
        return out


def keep(phone):
    serial = {"pixel": "41241FDH2S10SJ", "nothing": "0005534BC000140"}.get(phone, phone)
    for target in (serial, PHONES.get(phone, phone) + ":5555"):
        r = subprocess.run(["adb", "-s", target, "shell", "pm", "grant", PKG, "android.permission.WRITE_SECURE_SETTINGS"],
                           capture_output=True, text=True)
        if r.returncode == 0:
            # the door's token into the app's own files (debuggable build: run-as), through stdin, never on a command line
            tok = _token()
            if tok:
                subprocess.run(["adb", "-s", target, "shell", "run-as", PKG, "sh", "-c", "'cat > files/door.token'"],
                               input=tok, text=True, capture_output=True)
            print(f"{phone}: the app may now keep debugging on (via {target})")
            return 0
    print(f"{phone}: adb cannot reach it (USB or {PHONES.get(phone, phone)}:5555): {r.stderr.strip()}")
    return 1


def main(a):
    if not a or a[0] in ("-h", "--help"):
        print(__doc__); return 0
    cmd, rest = a[0], a[1:]
    phone = "pixel"
    if rest and (rest[0] in PHONES or rest[0].startswith("100.")):
        phone, rest = rest[0], rest[1:]
    r = Remote(phone)
    if cmd == "state":
        print(json.dumps(r.state(), indent=1, ensure_ascii=False))
    elif cmd in ("run", "do"):
        lines = open(rest[1]).read().splitlines() if rest[:1] == ["-f"] else rest
        res = r.run(*lines)
        print(json.dumps(res, ensure_ascii=False))
        if cmd == "do":
            print("shot:", r.shot())
        return 0 if res.get("ok") else 1
    elif cmd == "shot":
        print(r.shot(rest[0] if rest else "/tmp/mremote.png", int(rest[1]) if len(rest) > 1 else 360))
    elif cmd == "keep":
        return keep(phone)
    else:
        print(__doc__); return 2
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
