# WHEN IT BREAKS AND YOU ARE NOT AT HOME

Work down this list in order. Most problems are one of the first three.

---

## "I cannot reach the Mac at all"

1. **Is the Mac online?** Tailscale admin console
   (https://login.tailscale.com/admin/machines) — is `mantra-mac` green?
   - Grey = the Mac is asleep, off, or lost internet. Nothing you can do
     remotely. Someone at home must wake it.
   - Green = keep going.

2. **Did the key expire?** If the machine shows "Key expired", that is the
   classic 6-month failure. You cannot fix it remotely. Prevention:
   disable key expiry. Do it now if you have not.

3. **Is your phone on the tailnet?** Open Tailscale on the phone — the toggle
   silently turns itself off after some Android battery-optimisation events.
   Settings → Apps → Tailscale → Battery → **Unrestricted**.

4. **Try SSH first.** Lighter than the GUI and tells you a lot:
   ```bash
   ssh marko@mantra-mac
   ```
   If SSH works but the screen does not, it is a Screen Sharing problem, not a
   network problem. Jump to the next section.

---

## "SSH works but I get no picture"

Restart the screen sharing service over SSH:

```bash
sudo launchctl kickstart -k system/com.apple.screensharing
```

Or the full re-enable:

```bash
sudo /System/Library/CoreServices/RemoteManagement/ARDAgent.app/Contents/Resources/kickstart \
  -activate -configure -access -on -restart -agent -privs -all
```

Check something is listening:

```bash
lsof -nP -iTCP:5900 -sTCP:LISTEN
```

---

## "It connects but it is unusably slow"

```bash
tailscale status
```

Look at the line for your phone. `direct` is good. `relay "fra"` means it is
bouncing through a Tailscale DERP server — works, but adds latency.

Fixes:
- Enable **UPnP / NAT-PMP** on your home router.
- On the Mac: `sudo tailscale up --advertise-exit-node=false` and let it
  re-negotiate.
- Some mobile carriers block direct paths entirely. If Telemach is one, relay
  is your permanent normal — turn the quality down and live with it.

Then go through `docs/BANDWIDTH.md`. Disabling the OBS preview is the single
biggest fix.

---

## "The Mac rebooted and now I am locked out"

If FileVault is on: you are locked out until someone physically types the disk
password. There is no remote fix. See `docs/SECURITY.md` for the trade-off.

If FileVault is off, the Mac should come back on its own. Make sure it does:

```bash
sudo pmset -a autorestart 1      # restart automatically after a power failure
```

Run that now, not later.

---

## "OBS is recording but the file has no sound"

BlackHole is not routed. See `docs/OBS_ZOOM.md`. **Test this at home**, because
you will not notice it remotely until the recording is already ruined.

---

## LAST RESORT

A cheap Wi-Fi **smart plug** on the Mac's charger, on your home Wi-Fi, gives you
a remote hard power cycle from your phone. Ugly, but it has saved many people
a flight home. Combined with `autorestart 1` and FileVault off, it means you
can always get the machine back.
