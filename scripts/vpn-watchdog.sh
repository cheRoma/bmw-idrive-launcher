#!/usr/bin/env bash
# Keeps the car's YouTube VPN alive.
#
# SFA (sing-box for Android) holds the per-app tunnel that carries ONLY
# com.google.android.youtube. The head unit's ROM kills the app, and Android
# never brings it back: verified with kill -9 — the service stayed down and tun0
# never returned, 65s later still nothing. That is why YouTube "stops working"
# every few days while everything else is fine.
#
# Why it has to be done from here and not on the device:
#   - Android's always-on VPN is cosmetic on this ROM. Re-arming
#     always_on_vpn_app starts nothing at all (measured).
#   - The launcher cannot do it either: SFA's VPNService is not exported, and
#     its BootReceiver only answers BOOT_COMPLETED / MY_PACKAGE_REPLACED, both
#     protected broadcasts that a normal app is refused (SecurityException).
#   - A root adb shell can start the service, and the launcher already keeps a
#     reverse tunnel to this host, so we reach the car from the VPS.
#
# Starting the service does NOT touch the screen: verified with Yandex
# Navigator in the foreground — it kept focus. Safe to run while driving.
#
# SFA's own autostart after a reboot works, so this only covers mid-life kills.
set -uo pipefail

ADB=${ADB:-/home/roma/android-sdk/platform-tools/adb}
DEV=${DEV:-127.0.0.1:20055}   # head unit's adb, through the launcher's tunnel
SFA=io.nekohasekai.sfa
SVC="$SFA/.bg.VPNService"
PROPS=${PROPS:-/home/roma/Projects/bmw/keystore.properties}
LOG=${LOG:-/home/roma/Projects/bmw/reports/vpn-watchdog.log}

mkdir -p "$(dirname "$LOG")"
log() { printf '%s %s\n' "$(date -u +'%Y-%m-%d %H:%M:%SZ')" "$*" >>"$LOG"; }

# The tunnel port only listens while the launcher is up, so this doubles as an
# "is the car online" check. Car asleep in the garage: exit without a word.
ss -ltn 2>/dev/null | grep -q '127.0.0.1:20055' || exit 0

"$ADB" connect "$DEV" >/dev/null 2>&1

# One IPv4 line on tun0 means the tunnel interface is up.
tun_up() {
  local n
  n=$("$ADB" -s "$DEV" shell "ip addr show tun0 2>/dev/null | grep -c 'inet '" 2>/dev/null | tr -d '\r')
  [ "${n:-0}" -ge 1 ] 2>/dev/null
}

tun_up && exit 0   # healthy — stay quiet, cron runs this every 5 minutes

log "VPN down — reviving"

start_service() { "$ADB" -s "$DEV" shell "am start-foreground-service -n $SVC" 2>&1; }

out=$(start_service)
# adbd comes back unrooted after every boot, and an unrooted shell is refused
# with "Requires permission not exported from uid".
if printf '%s' "$out" | grep -qi 'not exported'; then
  log "adbd is unrooted, restarting it as root"
  "$ADB" root >/dev/null 2>&1
  sleep 3
  "$ADB" connect "$DEV" >/dev/null 2>&1
  out=$(start_service)
fi

sleep 8

if tun_up; then
  log "VPN is up again (pid $("$ADB" -s "$DEV" shell pidof $SFA 2>/dev/null | tr -d '\r'))"

  # Guard Roma's one hard requirement: YouTube through the VPN, nothing else.
  # The device re-fetches this profile, so it is the source of truth.
  url=$(grep -m1 '^vpnProfileUrl=' "$PROPS" 2>/dev/null | cut -d= -f2- | tr -d ' \r')
  if [ -n "$url" ]; then
    prof=$(curl -fsS --max-time 15 "$url" 2>/dev/null)
    if [ -z "$prof" ]; then
      log "WARNING: published profile could not be fetched, per-app routing unverified"
    elif ! printf '%s' "$prof" | grep -q 'com.google.android.youtube'; then
      log "WARNING: published profile no longer pins com.google.android.youtube — ALL traffic would go through the VPN"
    fi
  fi
else
  log "FAILED to bring the VPN up: ${out//$'\n'/ }"
fi
