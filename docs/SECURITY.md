# SECURITY

You asked for "secret". Here is what that actually means in practice.

---

## WHAT THIS DESIGN GETS RIGHT

- **No open ports.** Nothing on your Mac is reachable from the public internet.
  Both machines dial outward. A port scan of your home IP shows nothing.
- **End-to-end encryption.** WireGuard (Tailscale) encrypts device-to-device.
  Tailscale's coordination server exchanges public keys — it **cannot decrypt
  your traffic**, and in the normal direct case your pixels never pass through
  it at all.
- **No public identifier.** Unlike stock RustDesk or TeamViewer, there is no
  9-digit ID that anyone can try to connect to. Your Mac is invisible.

## WHAT YOU STILL HAVE TO DO

### 1. Lock the tailnet down with ACLs

By default every device in your tailnet can reach every other device. Tighten
it: Tailscale admin console → **Access Controls**. Something like:

```jsonc
{
  "acls": [
    // phone -> mac, only screen sharing + ssh + rustdesk
    {
      "action": "accept",
      "src":    ["autogroup:member"],
      "dst":    ["tag:mantra-mac:5900,22,21118-21119"]
    }
  ],
  "ssh": [
    {
      "action": "check",
      "src":    ["autogroup:member"],
      "dst":    ["autogroup:self"],
      "users":  ["autogroup:nonroot"]
    }
  ]
}
```

`"action": "check"` on SSH means a re-auth prompt in the browser — good if the
phone is ever stolen.

### 2. Protect the phone

The custom APK has your Mac's address and key baked in. **The phone is now the
key to your Mac.** Therefore:

- Strong lock screen on the Nothing Phone. Not a 4-digit PIN.
- Enable **Find My Device** with remote wipe.
- If the phone is lost: Tailscale admin console → **remove that device**.
  Access is revoked in seconds, no matter what is baked into the APK. This is
  why the overlay-network design is safer than a hardcoded DDNS name.

### 3. FileVault — the honest trade-off

| | FileVault ON | FileVault OFF |
|---|---|---|
| Disk stolen | Data safe | Data readable |
| Mac reboots while you are in India | **You are locked out until you fly home** | Boots straight back, you reconnect |

Screen Sharing does not exist before the disk is unlocked, so an encrypted Mac
that reboots is a brick until someone physically types the password.

Middle path: keep FileVault ON, and have someone at home (or your other phone
on a smart plug reboot routine) who can type the password. Or accept the risk
of a machine that only ever sits in your flat.

### 4. Do not disable the login password

Tempting, catastrophic. Instead set:
System Settings → Lock Screen → "Require password after screen saver begins"
→ **immediately**, and let bVNC/RustDesk send your password.

### 5. Rotate the GitHub token

The token you shared with me is now in a chat transcript and in this
container's memory. Treat it as compromised.

Go to https://github.com/settings/tokens → **delete it** → make a new one.
Better: make the new one a **fine-grained** token scoped to only the
`MANTRA_REMOTE` repository, with an expiry date. It does not need
`write:org` or access to all your repos.

---

## THREAT MODEL, PLAINLY

This setup protects you from: the open internet, port scanners, credential
stuffing, RustDesk-ID brute force, your ISP.

It does **not** protect you from: someone who has your unlocked phone, someone
who has your GitHub account, or someone with physical access to the Mac.

Handle those three and you are in good shape.
