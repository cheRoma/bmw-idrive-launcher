# Enabling ADB / Developer Options on Chinese Android head units (XTRONS · Microntek · HCT · MTCE) — the `adbon` password

> **TL;DR:** On many Chinese Android car head units the normal "tap *Build number* 7 times" trick **does nothing** and *Developer Options* never appears. The fix: open the unit's **Factory Settings** and type **`adbon`** in the password field. That unlocks Developer Options / ADB. Then enable **Wireless debugging** (no USB cable needed) and connect over Wi-Fi.

This is written up because it cost a full evening to discover and there's almost nothing about it online. If it saves you that evening, it did its job.

## Who this is for

Aftermarket Android head units (car stereos / "Android autoradio") built on the **Microntek / HCT / MTCE** platform. Common giveaways:

- Brands: **XTRONS**, Dasaita, Eonon, Pumpkin, and countless AliExpress no-names.
- Settings app is a custom **`com.hct.*` / `com.microntek.*`** thing, not stock Android.
- `ro.build.*` looks like `qssi-userdebug 13 … eng.hct.…`, brand often **`Doro`**, SoC **QCM6125** (Snapdragon 665-class), platform `trinket`.
- Verified on: **XTRONS IQ-series, Android 13, QCM6125** (2024–2025 ROM).

## The symptom

You want ADB (to sideload, debug, screenshot, automate). The standard path is *Settings → About → tap Build number 7×*. On these units:

- The **About** screen has no tappable *Build number*; the version row you *can* tap just shows an **MCU** string like **`if2 - V2`** and does nothing.
- *Settings → System → **Developer options*** simply **isn't there**, and firing the intent (`am start -a android.settings.APPLICATION_DEVELOPMENT_SETTINGS`) opens an empty/no-op screen.

Developer mode is gated behind the vendor's **Factory Settings**, and the entry password is a secret.

## The fix — `adbon`

1. Open the head unit's **Factory Settings** (a.k.a. *Заводские настройки* / *工厂设置*). It's usually reached from the unit's own **Settings** app → *Factory* (not Android's Settings). It prompts for a **password**.
2. In the password field type **`adbon`** (lowercase letters — switch the keyboard to text).
   - If the field is numeric-only, first enter the factory menu with the common factory code (**`8888`**, `1234`, `3368`, or the current date `YYYYMMDD`) and look for an **ADB / USB debug** toggle inside.
3. `adbon` flips the ROM's internal ADB flag → **Developer Options / ADB become available**.

### Why `adbon` works (for the curious)

Decompiling the ROM's privileged system service (`/system/priv-app/HCTManagerService`, `android.microntek.service`) shows an `adbon` boolean and a `StartAdbOn()` method. The vendor wired the string **`adbon`** as a factory-menu shortcut that enables ADB. (On some builds `StartAdbOn()` is a stub, but the password path still toggles the setting via the factory app, which holds the `WRITE_SECURE_SETTINGS` privilege that normal apps and even a Termux shell do **not** have.)

## After ADB is unlocked — connect without a USB cable

These units often have **no accessible USB data port**, so use **Wireless debugging** (Android 11+):

1. *Settings → System → Developer options →* enable **Wireless debugging**.
2. Tap **Pair device with pairing code** → note the **IP:port** and the **6-digit code**.
3. From a computer on the same network (or, cleverly, from an ADB client running *on the unit itself* via Termux):
   ```sh
   adb pair 192.168.x.x:PAIRPORT 123456
   adb connect 192.168.x.x:CONNECTPORT   # connect port ≠ pair port; it's shown on the Wireless-debugging screen
   ```
4. Because these are **`userdebug` / `ro.debuggable=1`** builds, **`adb root` works**:
   ```sh
   adb root        # adbd restarts as root; reconnect afterwards
   adb shell id    # uid=0(root) … context=u:r:su:s0
   ```

### Make it survive reboots (optional)

Wireless debugging often turns itself off after a reboot. With a root shell you can pin a classic ADB-over-TCP port that persists:

```sh
adb shell 'settings put global adb_enabled 1'
adb shell 'setprop persist.adb.tcp.port 5555'   # read at every boot
# after a reboot: adb connect <ip>:5555 ; adb root
```

## What you get — and what you don't

- ✅ **`adb root`** (real uid 0 via adbd) as long as you're connected. Screenshots (`screencap`), input injection (`input tap`), `pm`, `settings`, `dumpsys`, silent installs — all work.
- ✅ **Bootloader is typically unlocked** (`ro.boot.flash.locked=0`, `ro.boot.vbmeta.device_state=unlocked`, verifiedbootstate `orange`).
- ❌ **No *persistent* on-device root.** There's no `su`/Magisk; root exists only through the live adb connection. A background app or Termux script can **not** get root on its own.
- ➡️ For permanent root (Magisk): the bootloader is unlocked, so `fastboot flash boot magisk_patched.img` works — **if** you can reach a USB/fastboot port. Flashing the boot partition is the only real brick risk here; everything above is safe and reversible (`adb unroot`, or toggle Wireless debugging off).

## Non-destructive by design

Nothing in the `adbon` / Wireless-debugging / `adb root` flow touches the bootloader or flashes anything — worst case a command just fails and you toggle debugging back off. Brick risk only appears if you go down the `fastboot flash boot` route for permanent root.

---

*Discovered while building [bmw-idrive-launcher](../README.md) for an XTRONS-based BMW X5 E53 head unit. Keywords: XTRONS enable ADB, Microntek developer options password, HCT MTCE factory password, Android car stereo adb root, QCM6125 Doro userdebug, "if2 V2" build number, `adbon`.*
