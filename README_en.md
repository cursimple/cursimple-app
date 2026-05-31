[English](README_en.md) | [Chinese](README.md)

# CurSimple

A Kotlin-based Android timetable app "CurSimple" that helps you manage your class schedule with ease.

## Features

- **Wide Compatibility**: Supports Android 7.0 (API 24) to Android 16 (targetSdk 36)
- **Multi-architecture**: Provides `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64` splits and a universal package
- **Modern UI**: Built with Jetpack Compose for a smooth, modern interface
- **Home Screen Widgets**: Glance-based widgets for quick schedule viewing
- **Plugin System**: Uses manifest + WebView JS plugins for school timetable data collection
- **Plugin Marketplace**: Browse and install plugins from the GitHub registry ([cursimple-plugins](https://github.com/cursimple/cursimple-plugins))
- **Course Reminders**: Dual backend reminders (system alarm / in-app alarm)
- **Open Source**: Fully open-source with GitHub Actions CI/CD

## Getting Started

### Download

1. Visit the [Releases](https://github.com/cursimple/cursimple-app/releases) page
2. Download the appropriate APK for your device:
   - `app-armeabi-v7a-release.apk` for older 32-bit ARM devices
   - `app-arm64-v8a-release.apk` for modern 64-bit ARM devices (most phones)
   - `app-x86_64-release.apk` for Intel-based devices
   - `app-universal-release.apk` if unsure (works on all architectures but larger)
3. Install the APK (you may need to enable "Install from unknown sources")

### First Launch

1. Open the app
2. You'll be guided to set up your first timetable
3. Use the plugin marketplace to find and install a plugin for your school
4. Follow the plugin's instructions to import your schedule

## Using Plugins

### Plugin Marketplace

The plugin marketplace is powered by the GitHub repository [cursimple/cursimple-plugins](https://github.com/cursimple/cursimple-plugins). It provides a curated list of plugins for various schools and institutions.

1. Go to the **Plugins** tab in the app
2. Browse the "Plugin Marketplace" section (displayed in a 2-column grid)
3. Each plugin shows: name, author, stars, description, and latest plugin version
4. Tap a plugin to view details, then choose "Install" or "View on GitHub"

### Installing Plugins

Each plugin repository on GitHub must have at least one Release with `manifest.json` and the plugin package named by its `filename` field. The app will:
1. Load the plugin list from `plugin-stars-data/plugins-stars.json`
2. Read `https://github.com/{owner}/{repo}/releases/latest/download/manifest.json`
3. Download the plugin package named by `filename`
4. Install the plugin automatically

Note: GitHub's auto-generated "Source code" archives are not used as plugin packages.

### Managing Plugins

- Installed plugins appear in your plugin list
- You can enable/disable plugins as needed
- Plugin settings can be configured individually

## Features in Detail

### Course Reminders

- **System Alarm**: Uses Android's built-in alarm system for reliable notifications
- **In-app Alarm**: Alternative backend for devices with restricted alarm permissions
- Configure reminder times in Settings → Reminders

### Home Screen Widgets

- Add CurSimple widgets to your home screen
- Widgets show upcoming classes and can be customized
- Widgets refresh automatically at configurable intervals

### Data Management

- Your schedule data is stored locally using Android DataStore
- Backup and restore functionality is available in Settings
- Plugin data is stored separately for easy management

## Troubleshooting

### Plugin Installation Fails

- Ensure you have a stable internet connection
- Check if the plugin repository has a valid Release with a `.zip` asset
- Try switching to a different network (Wi-Fi/mobile data)

### Schedule Not Showing

- Verify that a plugin is installed and enabled
- Check that the plugin has successfully imported your schedule
- Try manually refreshing the schedule view

### Widget Not Updating

- Ensure the app has background refresh permissions
- Check widget refresh interval settings
- Try removing and re-adding the widget

## Support & Feedback

- **Issues**: Report bugs or request features via [GitHub Issues](https://github.com/cursimple/cursimple-app/issues)
- **Discussions**: Join the conversation in [GitHub Discussions](https://github.com/cursimple/cursimple-app/discussions)
- **Plugin Development**: See [Plugin Development Guide](docs/plugin-system.md) for creating your own plugins (Note: Documentation is in Chinese)

## Technical Details

For detailed build instructions, module structure, and CI/CD configuration, see the [Developer Documentation](README_dev.md).

<details>
<summary>Quick Overview</summary>

### Build from Source

#### Requirements
- JDK 17
- Android SDK with `platforms;android-36`

#### Configuration
Local signing configuration via `keystore.properties` (see `keystore.example.properties`):

```properties
CLASS_VIEWER_KEYSTORE_FILE=.signing/class-viewer.jks
CLASS_VIEWER_KEYSTORE_PASSWORD=your-store-password
CLASS_VIEWER_KEY_ALIAS=your-key-alias
CLASS_VIEWER_KEY_PASSWORD=your-key-password
```

#### Build Commands
```bash
# Debug build
./gradlew assembleDebug

# Release build (all architectures)
./gradlew assembleRelease
```

### Project Structure
- `app`: Application shell, dependency assembly, entry pages, update checking
- `core-kernel`: Unified timetable model and core protocols
- `core-plugin`: Plugin manifest, installation, components, web session model
- `core-data`: DataStore repositories
- `core-reminder`: Reminder rules, scheduling, and dispatch backends
- `feature-schedule`: Schedule UI and sync logic
- `feature-plugin`: Plugin marketplace UI and WebView sessions
- `feature-widget`: Home screen widgets and scheduled refresh

### CI/CD
- GitHub Actions workflows in `.github/workflows/`
- CI runs on PRs and pushes to `main`
- Release runs on version tags (e.g., `v1.0.0`)

</details>

## License

This project is open source. See the [LICENSE](LICENSE) file for details.
