# HVAC Float

Floating HVAC overlay app for the MG4 head unit.

#### This app was built on the 2024 MG4 Xpower, Your car version may or may not function with this application. 
#### Feedback welcome

Current app version: `0.5.3`

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
- Themeable expanded bar background colour and opacity
- Overlay mode selection:
  - `Bar` keeps the current HVAC Float control bar.
  - `Factory HVAC` keeps only the hidden square handle; double-tap toggles the factory SystemUI HVAC overlay open/closed.
- Autostart modes:
  - `Autostart Full`
  - `Autostart Hidden`
  - `Autostart Off`
- Update checker:
  - Checks for a newer GitHub release automatically when the settings app opens.
  - Shows a custom dark update prompt with release notes/changelog when an update is available.
  - Checks the latest GitHub release.
  - Downloads release APKs manually to the public Downloads folder.
  - Opens the Downloads folder after download so the APK can be installed manually.
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
- Icon Set 1 now includes centered HVAC-style icons for temperature, fan, air loop, air flow, defrost, heated seats, and heated steering wheel.
- Icon Set 1 uses OG-style heated seat ring icons and an adaptive taller bar so the larger icons are not clipped.
- The full expanded overlay can be moved by long-press dragging controls, not only from hidden mode.

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
  - Theme plumbing exists, and Icon Set 1 is now available.
  - `TEXT` is the sane/default mode.
- `Bar Colour`
  - Adjust the expanded overlay bar background using RGB sliders, a `#RRGGBB` field, and 0-100% opacity.
- `Overlay Mode`
  - Choose between the normal HVAC Float bar and the factory HVAC overlay launcher handle.
- `Select Controls`
  - Choose which HVAC groups appear on the floating bar.
  - `HIDE` is always shown.
- `Autostart Mode`
  - Controls boot behaviour.
- `Check Updates`
  - Manually checks GitHub releases and downloads newer APK releases to Downloads.
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
dist\MG4-HVACFloat-V0.5.3.apk
```

Latest named build:

```text
dist\MG4-HVACFloat-V0.5.3.apk
```

GitHub releases attach the APK and matching `.sha256` checksum as release assets. APK files in `dist` are local build outputs and are not committed to the repository.

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
