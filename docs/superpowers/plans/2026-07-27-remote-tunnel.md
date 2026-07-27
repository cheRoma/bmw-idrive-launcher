# Remote tunnel inside the launcher (plan, 2026-07-27)

## Why

Every remote fix so far has depended on Termux on the head unit holding an autossh reverse tunnel.
That tunnel points at a VPS that no longer exists, and repointing it means typing shell commands on
a 7-inch capacitive screen in a parked car. Roma asked for the tunnel to live in the launcher itself,
which also removes Termux from the critical path.

Two decisions taken with him before any code:

- **expose both** the device's `adb` port *and* a small control endpoint of our own. Reason: after a
  reboot this ROM often leaves `adbd` stopped, and a tunnel that only reaches adb would then lead
  nowhere. The control endpoint keeps the car reachable regardless;
- **always on**, started with the launcher and after boot, with a switch and a live status line in
  Settings.

## Server side (done and verified before writing app code)

- User `hu` already exists (nologin). Added the launcher's public key to its `authorized_keys` with
  `restrict,port-forwarding,permitlisten="127.0.0.1:20055",permitlisten="127.0.0.1:20080"`.
- `/etc/ssh/sshd_config.d/60-hu-tunnel.conf`: `Match User hu` → `AllowTcpForwarding remote`,
  `PermitOpen none`, `PermitTTY no`, no agent/X11 forwarding, `ClientAliveInterval 30`.
- Verified by simulating the head unit from this host: a dummy service on 5555, an `ssh -R` as `hu`,
  and an HTTP 200 fetched through `127.0.0.1:20055`. Also verified the limits bite: a shell is
  refused ("This account is currently not available"), a non-listed remote port is refused, and an
  `-L` channel is refused server-side ("administratively prohibited").
- **Learned:** the forward must be requested with an explicit bind address (`-R 127.0.0.1:20055:…`),
  otherwise `permitlisten` does not match and the forward is rejected.
- Key is RSA-3072 in classic PEM (`BEGIN RSA PRIVATE KEY`) — the format JSch parses without
  surprises. A parsing failure on the device would be undebuggable remotely, which is exactly the
  situation this feature exists to end.

## App side

New package `online.k73.bmwlauncher.remote`:

| Piece | What it does | Tested |
|---|---|---|
| `TunnelBackoff` | attempt → delay, capped, reset on success | pure, unit |
| `ControlRoute` | parses the request line + token header into an action | pure, unit |
| `ControlServer` | `ServerSocket` on `127.0.0.1:8973`, serves the actions | — |
| `RemoteTunnel` | JSch session, host-key pinned, two reverse forwards, reconnect loop | — |
| `RemoteTunnelService` | foreground service that owns the loop and publishes status | — |

Control actions (token in `X-Token`, and the whole endpoint is only reachable through the tunnel):

- `GET /status` — version, uptime, I-Bus link, black-screen episodes, default-launcher, screen, whether SFA is installed;
- `POST /logs` — upload a diagnostic report now;
- `POST /vpn` — re-hand the VPN profile to SFA;
- `POST /restart` — restart the launcher process.

No shell, no arbitrary commands: the list above is the whole surface.

## Secrets and the public build

The private key, the host-key pin and the control token reach the app through `keystore.properties`
→ `BuildConfig`, like the log-upload token. **A key inside an APK is only as private as the APK**, and
we attach APKs to public GitHub releases — so the release flow splits:

- OTA build (`k73.online`) — real secrets, tunnel active;
- public build (`-PpublicBuild=true`) — secrets blank, tunnel disabled, everything else identical.

## Verification

Off-device: unit tests for the pure parts; the server side was verified against a simulated device.
On-device: Roma updates over OTA once; the tunnel is confirmed from the VPS by connecting to
`127.0.0.1:20080/status`. Until that first confirmation the feature is unproven — the Settings row
shows the live state precisely so a failure is visible without me.
