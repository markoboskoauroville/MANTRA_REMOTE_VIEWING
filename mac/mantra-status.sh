#!/usr/bin/env bash
# MANTRA REMOTE — pre-flight check. Run before leaving the house.
BOLD=$'\033[1m'; GRN=$'\033[32m'; YEL=$'\033[33m'; RED=$'\033[31m'; RST=$'\033[0m'
pass(){ printf "  ${GRN}[ OK ]${RST}  %s\n" "$*"; }
fail(){ printf "  ${RED}[FAIL]${RST}  %s\n" "$*"; BAD=1; }
warn(){ printf "  ${YEL}[WARN]${RST}  %s\n" "$*"; }
BAD=0

printf "\n${BOLD}MANTRA REMOTE — pre-flight${RST}\n\n"

TS=/Applications/Tailscale.app/Contents/MacOS/Tailscale
[[ -x "$TS" ]] || TS="$(command -v tailscale || echo /nonexistent)"

# 1 Tailscale up
if "$TS" status >/dev/null 2>&1; then
  IP="$("$TS" ip -4 2>/dev/null | head -1)"
  pass "Tailscale connected — $IP"
else
  fail "Tailscale NOT connected"
fi

# 2 Screen sharing listening
if lsof -nP -iTCP:5900 -sTCP:LISTEN >/dev/null 2>&1; then
  pass "Screen Sharing listening on 5900"
else
  fail "Nothing listening on 5900 — Screen Sharing is off"
fi

# 3 RustDesk (optional)
if pgrep -qx RustDesk 2>/dev/null || pgrep -qi rustdesk >/dev/null 2>&1; then
  pass "RustDesk running"
else
  warn "RustDesk not running (fine if you only use VNC)"
fi

# 4 Sleep settings
SLEEP=$(pmset -g custom 2>/dev/null | awk '/^ *sleep/{print $2; exit}')
if [[ "$SLEEP" == "0" ]]; then pass "System sleep disabled"; else fail "System sleep = ${SLEEP:-?} (must be 0)"; fi

# 5 Caffeinate daemon
if pgrep -qx caffeinate; then pass "caffeinate daemon alive"; else warn "caffeinate not running"; fi

# 6 Internet
if ping -c1 -W2000 1.1.1.1 >/dev/null 2>&1; then pass "Internet reachable"; else fail "No internet"; fi

# 7 Battery / power
PWR=$(pmset -g ps | head -1)
case "$PWR" in *"AC Power"*) pass "On AC power";; *) fail "ON BATTERY — plug it in";; esac

# 8 FileVault
if fdesetup status 2>/dev/null | grep -q "On"; then
  warn "FileVault ON — after a reboot you cannot get in remotely"
else
  pass "FileVault off — survives a reboot unattended"
fi

# 9 Key expiry reminder
printf "\n  ${YEL}Reminder:${RST} key expiry must be DISABLED for this node at\n"
printf "  https://login.tailscale.com/admin/machines\n"

if [[ $BAD -eq 0 ]]; then
  printf "\n  ${GRN}${BOLD}READY. You can leave.${RST}\n\n"
else
  printf "\n  ${RED}${BOLD}NOT READY. Fix the FAIL lines above.${RST}\n\n"
  exit 1
fi
