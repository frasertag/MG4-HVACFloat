# HVAC Float

Clean-room floating HVAC overlay app for the MG4 head unit.

Current app version: `0.4`

## Current Features

- Floating HVAC overlay using `TYPE_APPLICATION_OVERLAY`
- Long-press drag to move the bar
- Saved overlay position across restarts/boot
- Position saving now uses top-left screen coordinates so hidden/full modes restore from the same anchor point
- Foreground overlay service for more reliable cold-boot autostart
- Full bar and hidden square handle modes
- Double-tap hidden `HVAC` handle to restore the bar
- Optional settings gear button on the bar to open the app settings screen
- Selectable controls, so the bar can be compact or full
- Autostart modes:
  - `Autostart Full`
  - `Autostart Hidden`
  - `Autostart Off`
- Current working HVAC controls:
  - Temperature down/up
  - Fan down/up
  - Auto
  - Air loop
  - Air flow: `Feet -> Feet Face -> Face`
  - Defrost
  - Passenger heated seat
  - Driver heated seat
  - Heated steering wheel
- A/C button has been removed because the on-screen control was only half functional; use the hardware button.

## Settings

The main app screen currently provides:

- Three-column landscape layout:
  - Left: overlay permission, start/stop, autostart
  - Middle: selected control groups
  - Right: theme selection
- Supplied image background on the settings screen
- Large stylized `HVAC FLOAT` title
- Settings button on the overlay uses a centered gear icon
- Button status text appears under the related button

- `Select Theme`
  - Theme plumbing exists, but icon themes are paused for now.
  - `TEXT` is the sane/default mode.
- `Select Controls`
  - Choose which HVAC groups appear on the floating bar.
  - `HIDE` is always shown.
- `Autostart Mode`
  - Controls boot behaviour.
- `Overlay Permission`
- `Start HVAC Overlay`
- `Stop HVAC Overlay`

## Build

Build from this directory:

```powershell
.\build.ps1
```

The build script outputs:

```text
dist\MG4-HVACFloat-V0.4.apk
```

Latest named build:

```text
dist\MG4-HVACFloat-V0.4.apk
```

GitHub releases attach the APK and matching `.sha256` checksum as release assets.

## Implementation Notes

- Package: `com.custom.hvacfloater`
- Overlay service: `OverlayService`
- Boot receiver: `BootReceiver`
- Settings constants: `HvacTheme`
- HVAC API wrapper: `HvacController`
- SAIC vehicle settings SDK dex is copied into the APK as `classes2.dex` from:

```text
..\known_good_seats_base\build\apk\classes5.dex
```

## Keep Updated

Update this README whenever the app gains, loses, or changes behaviour. This file is now the quick project memory for HVAC Float.
