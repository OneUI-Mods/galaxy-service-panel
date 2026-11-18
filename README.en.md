# Galaxy Service Panel

[日本語](README.md) · English

A Vector module that makes Samsung Service Mode, opened with `*#0011#` in the phone keypad, easier to read.

## First unlock after boot

Hook initialization that needs settings or cached discovery results waits for the first user unlock after boot. A later ordinary screen lock does not repeat initialization. Existing screen-off power-saving and security behavior is preserved. Unlock delivery is event-driven, with no polling or new resident service.

## Features

- Separate SIM/connectivity, LTE, and NR sections with organized diagnostic values.
- Adapt presentation to screen width and font size.
- Inspect and copy original strings from the **Original** tab or by long-pressing.
- Preserve stock menus and diagnostic-value updates.
- Display enable/disable control, font size, light/dark themes, and Japanese/English UI.

## Requirements

Targets a compatible Service Mode on Galaxy S26 Ultra (SM-S948Q), Android 17 / One UI 9, with Vector API 102. An Android 16 compatibility profile is prepared but has not been physically validated in this version. Unsupported screens retain their stock presentation.

## Installation and use

1. Install the signed release APK from [Releases](https://github.com/OneUI-Mods/galaxy-service-panel/releases).
2. Enable the module and **Service mode RIL** in Vector.
3. Load it into the target process after installation or updates. Service Mode shares a process with phone functionality, so reload only when a brief interruption to calls/connectivity is acceptable.
4. Enter `*#0011#` in the phone keypad.

Use **Display** for enablement and font size, **Preview** for samples, and **About** for status. Errors or protection stops return to the stock display.

## Limitations

Only presentation is reorganized. Modem settings, frequencies, SIM settings, and update intervals are unchanged. Diagnostic values are neither saved nor transmitted; copying occurs only on explicit user action. The app never automatically restarts the phone process or device.

## Development and licenses

Build instructions, architecture, and verification are in the [developer guide (Japanese)](docs/development.md). Report problems through [Issues](https://github.com/OneUI-Mods/galaxy-service-panel/issues), including device, OS and target-app versions, and reproduction steps.

See [LICENSE](LICENSE) for the app's license and the in-app or bundled notices for dependencies. This is not an official Samsung app.

[OneUI Mods app catalog](https://github.com/OneUI-Mods)

## String compatibility

Fixed Service Mode rows disappearing or losing text when they contain certain whitespace.

## Compatibility after updates

After a target-app update, open this module once to finish preparation, then reopen the target. Discovery results are stored in this module's own internal app data and reused while unchanged. No other OneUI Mods app is required. Features stop applying when a unique compatible target cannot be confirmed. Compatibility with every OS or app update is not guaranteed.

[Build environment / ビルド環境](docs/build-environment.md)
