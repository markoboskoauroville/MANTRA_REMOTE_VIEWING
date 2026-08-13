# BUILDING "MANTRA REMOTE" — THE CUSTOM APK

Goal: an APK that you install on the Nothing Phone 2a, you tap it, and it is
**already pointed at your Mac**. Your name, your icon, your colours. Nothing
to type, nothing to configure, nothing public.

---

## FIRST: WHICH REPO, AND WHY NOT A NORMAL FORK

**Repo to base on:** `rustdesk/rustdesk` — AGPL-3.0, written in Rust + Flutter,
Android client is officially supported, ~1.4.6+ current.

**Do not use the GitHub "Fork" button.** A fork of a public repository is
always public and **can never be converted to private**. That is a hard GitHub
rule, not a setting.

To get a private copy you do a **mirror clone**:

```bash
# 1. Make the empty private repo (already done: MANTRA_REMOTE_CLIENT)
# 2. Mirror the upstream into it
git clone --bare https://github.com/rustdesk/rustdesk.git
cd rustdesk.git
git push --mirror https://github.com/markoboskoauroville/MANTRA_REMOTE_CLIENT.git
cd .. && rm -rf rustdesk.git

# 3. Work in it normally
git clone https://github.com/markoboskoauroville/MANTRA_REMOTE_CLIENT.git
cd MANTRA_REMOTE_CLIENT
git remote add upstream https://github.com/rustdesk/rustdesk.git
```

To pull in upstream fixes later:

```bash
git fetch upstream && git merge upstream/master
```

**AGPL note:** you are free to modify and use this privately. The licence only
requires you to publish source if you *distribute* the binary or offer it as a
network service to others. An APK for your own phone triggers nothing.

---

## BAKING YOUR CONFIG IN AT BUILD TIME

RustDesk reads these at **compile time** via `option_env!`. Set them and the
values are burned into the binary — this is the free/OSS equivalent of the
Pro-only "Custom Client Generator".

| Variable | Meaning |
|---|---|
| `RENDEZVOUS_SERVER` | Your ID/signalling server hostname |
| `RS_PUB_KEY` | The server's public key (from `id_ed25519.pub`) |
| `API_SERVER` | Optional, Pro web console only — leave empty |

Look in `libs/hbb_common/src/config.rs` to confirm the names in the version you
mirrored; they have been stable for a long time but check.

### Two ways to point it at your Mac

**Option A — no server at all (recommended, most private).**
Tailscale already gives you a fixed private address, so use RustDesk's
**direct IP access**. On the Mac: RustDesk → Settings → Security →
enable **"Enable direct IP access"** and set a permanent password. From the
phone, connect to `100.x.x.x` directly. No rendezvous server, no relay,
nothing published anywhere on the internet.

**Option B — your own relay.** See `self-host-server.md`. Useful only if you
later want to reach machines that are not on your tailnet.

---

## COSMETICS

| What | Where |
|---|---|
| App name | `flutter/android/app/src/main/AndroidManifest.xml` → `android:label` |
| Icon | `flutter/android/app/src/main/res/mipmap-*/` |
| Package id | `flutter/android/app/build.gradle` → `applicationId "com.mantra.remote"` |
| Colours | `flutter/lib/common.dart` — theme definitions |
| Splash / about text | `flutter/lib/desktop/pages/`, `res/` strings |

Changing `applicationId` means it installs **alongside** the official RustDesk
rather than replacing it. Do that — you want the official one as a fallback.

You mentioned Sunrise as your palette elsewhere; drop those hex values into the
Flutter theme and it will feel like the rest of your ecosystem.

---

## BUILDING

The Android build is heavy (Rust cross-compile + Flutter + vcpkg). **Build it in
GitHub Actions, not on your Mac.** Upstream already ships workflows in
`.github/workflows/` — `flutter-build.yml` is the one that produces the APK.

1. In your private repo: Settings → Secrets and variables → Actions → add
   `RENDEZVOUS_SERVER` and `RS_PUB_KEY` as repository secrets.
2. Trim the upstream workflow down to only the `android-arm64v8a` job — you
   need one architecture, not fourteen. Cuts build time from ~2 h to ~25 min.
3. Export the secrets as env vars in the build step so the Rust compile picks
   them up.
4. Run the workflow → download the APK from the run's **Artifacts**.
5. Sideload it on the Nothing Phone (allow install from unknown sources).

**Free-tier reality check:** private repos get 2,000 Actions minutes/month.
A trimmed single-architecture build is ~25 minutes. That is roughly 80 builds
a month. Plenty, but do not leave a matrix build looping.

---

## FULL-SCREEN ON THE PHONE

You wanted it to take the whole screen of the Nothing Phone 2a.

- RustDesk Android already supports immersive full-screen and both
  orientations. In session: toolbar → display → **Fit / Original / Stretch**.
- For a 6.7" phone driving a 16:10 Mac, **landscape + "Fit"** wastes the least.
- Enable **"Touch mode"** rather than mouse mode for tapping UI, switch to
  mouse mode for precise work like OBS source dragging.
- In `flutter/lib/`, `SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky)`
  is the call to force it permanently if the default annoys you.

---

## HONEST ADVICE

Stage 2 is a real weekend of work — Rust toolchains, Flutter versions, NDK
mismatches, vcpkg. It is genuinely fun and it is genuinely fiddly.

**Get Stage 1 working first.** Tailscale + bVNC takes twenty minutes and will
let you record a Zoom call tomorrow. Build the beautiful custom client after
that, with the pressure off.
