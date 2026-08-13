#!/usr/bin/env bash
# MANTRA REMOTE — Mac side setup
# Run once on the Mac that stays at home.
#   chmod +x mac/mantra-mac-setup.sh && ./mac/mantra-mac-setup.sh
set -euo pipefail

BOLD=$'\033[1m'; DIM=$'\033[2m'; GRN=$'\033[32m'; YEL=$'\033[33m'
RED=$'\033[31m'; RST=$'\033[0m'

say()  { printf "\n${BOLD}==> %s${RST}\n" "$*"; }
ok()   { printf "  ${GRN}OK${RST}   %s\n" "$*"; }
warn() { printf "  ${YEL}WARN${RST} %s\n" "$*"; }
die()  { printf "  ${RED}FAIL${RST} %s\n" "$*"; exit 1; }

[[ "$(uname -s)" == "Darwin" ]] || die "This script is for macOS only."

# ---------------------------------------------------------------- 1. Homebrew
say "1/6  Homebrew"
if ! command -v brew >/dev/null 2>&1; then
  warn "Homebrew not found. Installing."
  /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
  # Apple Silicon path
  [[ -x /opt/homebrew/bin/brew ]] && eval "$(/opt/homebrew/bin/brew shellenv)"
fi
ok "brew $(brew --version | head -1)"

# --------------------------------------------------------------- 2. Tailscale
say "2/6  Tailscale"
if [[ ! -d /Applications/Tailscale.app ]] && ! command -v tailscale >/dev/null 2>&1; then
  brew install --cask tailscale-app || brew install --cask tailscale
fi

TS=/Applications/Tailscale.app/Contents/MacOS/Tailscale
if [[ ! -x "$TS" ]]; then
  TS="$(command -v tailscale || true)"
fi
[[ -n "$TS" ]] || die "Tailscale binary not found. Open the Tailscale app once, then re-run."

open -a Tailscale 2>/dev/null || true
sleep 3

if ! "$TS" status >/dev/null 2>&1; then
  warn "Not logged in yet."
  echo "  ${DIM}A browser window will open. Log in, then press Enter here.${RST}"
  "$TS" up --hostname=mantra-mac --ssh 2>/dev/null || \
    echo "  ${DIM}(If that failed, log in via the Tailscale menu bar icon.)${RST}"
  read -r -p "  Press Enter once Tailscale shows 'Connected'... " _
fi

TSIP="$("$TS" ip -4 2>/dev/null | head -1 || true)"
[[ -n "$TSIP" ]] || die "No Tailscale IP. Log in via the menu bar icon, then re-run."
ok "Tailscale IP: ${BOLD}${TSIP}${RST}"

# ------------------------------------------------- 3. Screen Sharing (VNC)
say "3/6  Screen Sharing"
sudo /System/Library/CoreServices/RemoteManagement/ARDAgent.app/Contents/Resources/kickstart \
  -activate -configure -access -on \
  -restart -agent -privs -all >/dev/null 2>&1 \
  && ok "Apple Remote Desktop agent enabled (VNC on port 5900)" \
  || warn "kickstart failed — enable manually: System Settings > General > Sharing > Screen Sharing"

sudo launchctl enable system/com.apple.screensharing 2>/dev/null || true
sudo launchctl load -w /System/Library/LaunchDaemons/com.apple.screensharing.plist 2>/dev/null || true

# ------------------------------------------------------------- 4. No sleep
say "4/6  Never sleep, never lock"
sudo pmset -a sleep 0            2>/dev/null && ok "system sleep disabled"
sudo pmset -a disksleep 0        2>/dev/null && ok "disk sleep disabled"
sudo pmset -a displaysleep 10    2>/dev/null && ok "display sleeps after 10 min (fine, machine stays up)"
sudo pmset -a womp 1             2>/dev/null || true   # wake on network
sudo pmset -a powernap 0         2>/dev/null || true
# Laptops: keep awake with the lid closed on AC power
sudo pmset -c disablesleep 1     2>/dev/null && ok "clamshell sleep disabled on AC" || true

# ------------------------------------------------------ 5. Awake LaunchDaemon
say "5/6  Keep-awake daemon"
PLIST_SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/com.mantra.remote.awake.plist"
if [[ -f "$PLIST_SRC" ]]; then
  sudo cp "$PLIST_SRC" /Library/LaunchDaemons/com.mantra.remote.awake.plist
  sudo chown root:wheel /Library/LaunchDaemons/com.mantra.remote.awake.plist
  sudo chmod 644        /Library/LaunchDaemons/com.mantra.remote.awake.plist
  sudo launchctl bootout system /Library/LaunchDaemons/com.mantra.remote.awake.plist 2>/dev/null || true
  sudo launchctl bootstrap system /Library/LaunchDaemons/com.mantra.remote.awake.plist
  ok "caffeinate daemon installed"
else
  warn "plist not found next to this script — skipping"
fi

# ------------------------------------------------------------- 6. Summary
say "6/6  Done"
HOSTN="$(scutil --get LocalHostName 2>/dev/null || hostname)"
cat <<EOF

  ${BOLD}Connect from the phone with:${RST}

    Address   ${BOLD}${TSIP}${RST}     (or MagicDNS name: mantra-mac)
    Port      ${BOLD}5900${RST}
    User      ${BOLD}$(whoami)${RST}
    Password  your Mac login password

  ${YEL}THREE THINGS YOU MUST STILL DO BY HAND:${RST}

  1. ${BOLD}Disable key expiry${RST} for this machine at
     https://login.tailscale.com/admin/machines
     Machine "mantra-mac" -> ... -> Disable key expiry
     Without this the Mac drops off the network in ~6 months.

  2. ${BOLD}Turn OFF FileVault${RST}, or accept that after a power cut you
     cannot get in until someone types the disk password physically.
     System Settings > Privacy & Security > FileVault
     (Trade-off: an unencrypted disk. Your call. See docs/SECURITY.md.)

  3. ${BOLD}Enable MagicDNS${RST} in the Tailscale admin console (DNS tab)
     so "mantra-mac" works as a name.

  Local hostname: ${HOSTN}

EOF
