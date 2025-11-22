# Rentool

A comprehensive Android toolkit for Ren'Py game modding: extract/create RPA archives, decompile RPYC scripts, compress game assets, and edit .rpy files directly on your device.

## Features

### Core Operations
- **Extract RPA Archives**: Unpack Ren'Py game archives to access images, scripts, audio, and other assets
- **Create RPA Archives**: Package files and folders into RPA v3 format archives with optional auto-creation after compression
- **Decompile RPYC Scripts**: Convert compiled .rpyc files back to readable .rpy source scripts
- **Compress Games**: Reduce game size with multi-format compression (WebP for images, Opus for audio, H.265 for video)
- **Edit .rpy Scripts**: Built-in syntax-highlighted editor with Ren'Py language support

### Compression Features
- **Image Compression**: Convert PNG/JPG/BMP to WebP with lossless or quality-based compression (1-100%)
- **Audio Compression**: Convert OGG/MP3/WAV/FLAC to Opus with configurable bitrate
- **Video Compression**: Encode MP4/AVI/MKV/WebM to H.265 with quality presets
- **Speed Control**: Choose Fast/Average/Slow compression modes for different performance needs
- **Selective Compression**: Enable/disable specific media types, prevent empty operations

### User Experience
- **Background Operations**: Continue operations when app is minimized with persistent notifications
- **Batch Processing**: Extract multiple RPA files or decompile multiple RPYC files at once
- **Multi-Select Support**: Long-press to select multiple files or folders for batch operations
- **Case-Insensitive Filtering**: Handles uppercase extensions (.RPA, .RPYC) from Windows distributions
- **Smart Directory Defaults**: Output directory automatically defaults to the location of selected input files
- **Accurate Progress Tracking**: Real-time file counts, speed, ETA, and detailed failure reporting
- **Auto-Update Checker**: Notifies when new versions are available on GitHub

## Requirements

- **Android 13 or higher** (API 33+)
- **MANAGE_EXTERNAL_STORAGE permission**: Required for direct file system access
- **POST_NOTIFICATIONS permission**: Required for background operation notifications (Android 13+)
- **Storage**: Enough space for extracted/created/compressed archives
- **Processing**: Compression operations benefit from multi-core devices

## Installation

1. Download the latest APK from the [Releases](../../releases) page
2. Enable "Install from Unknown Sources" if needed
3. Install the APK
4. Grant storage permissions when prompted (MANAGE_EXTERNAL_STORAGE)

## Building from Source

### Prerequisites

- Android Studio Hedgehog or later
- Android SDK with API 34+
- Python 3.8+ (for Chaquopy build)
- Gradle 8.0+
- FFmpeg-Kit AAR (included in `app/libs/`)

### Build Steps

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/Rentool.git
   cd Rentool
   ```

2. Open the project in Android Studio

3. Sync Gradle files (Android Studio should prompt automatically)

4. Build the APK:
   - Via Android Studio: **Build → Build Bundle(s) / APK(s) → Build APK(s)**
   - Via Command Line:
     ```bash
     ./gradlew assembleDebug
     ```

5. The APK will be located at:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

### Chaquopy Python Configuration

The project uses [Chaquopy](https://chaquo.com/chaquopy/) to integrate Python for RPA archive operations and RPYC decompilation. Chaquopy will automatically detect your Python installation during build.

If auto-detection fails, you can manually specify your Python path in `app/build.gradle`:
```gradle
python {
    buildPython "/path/to/python"
}
```

## Usage

### Extracting RPA Archives

1. Tap the **"Extract RPA"** card
2. Browse and select one or more `.rpa` files:
   - Single tap: Select one file
   - Long-press: Enter multi-select mode to choose multiple files
3. Select the destination folder (defaults to the folder containing the RPA files)
4. Wait for extraction to complete
5. Files will be extracted to the chosen directory (overwrites existing files)

### Creating RPA Archives

1. Tap the **"Create RPA"** card
2. Browse and select files/folders to archive:
   - Single tap: Select one folder
   - Long-press: Enter multi-select mode to choose multiple items
3. Enter a name for the output RPA file (e.g., `archive.rpa`)
4. Wait for creation to complete
5. The RPA file will be created in the parent directory of selected items

### Decompiling RPYC Scripts

1. Tap the **"Decompile RPYC"** card
2. Browse and select `.rpyc` files or folders containing scripts:
   - Single tap: Select individual files or an entire folder (e.g., `/game` folder)
   - Long-press: Enter multi-select mode to choose multiple files/folders
3. Select the destination folder (defaults to the folder containing the RPYC files)
4. Wait for decompilation to complete
5. Decompiled `.rpy` files will be saved to the chosen directory

**Tip**: Selecting a game's `/game` folder is the fastest way to decompile all scripts at once.

### Compressing Games

1. Tap the **"Compress Game"** card
2. Select the source folder containing game assets (e.g., the `game` folder)
3. Choose an output directory for compressed files
4. Configure compression settings:
   - **Image Quality**: 1-100 for lossy, or enable lossless compression
   - **Speed Mode**: Fast/Average/Slow (affects lossless compression effort)
   - **Audio Quality**: Low/Medium/High bitrate
   - **Video Quality**: Low/Medium/High/Very High
   - **Threads**: Number of parallel compression tasks
   - **Selective Types**: Skip images, audio, or video if desired
   - **Auto RPA**: Optionally create RPA archive from compressed output
5. Monitor real-time progress with file counts and compression statistics
6. Failed files are counted as original size for accurate reduction metrics

### Editing .rpy Scripts

1. Tap the **"Edit .rpy"** card
2. Browse and select a `.rpy` file (case-insensitive)
3. Edit code with syntax highlighting and line numbers
4. Save changes directly to the file
5. Return to file picker to open another script

## Technical Details

### RPA Format Support

- **RPA Version**: v3 (Ren'Py 7.4+)
- **Format Key**: `0xDEADBEEF` (standard Ren'Py key)
- **Compression**: ZLib

### File Operations

- Uses direct `File` API for maximum performance
- Requires `MANAGE_EXTERNAL_STORAGE` permission on Android 11+
- Files are overwritten without prompting during extraction
- Batch creation uses temporary directory for combining multiple sources

### Compression System

- **Image Encoder**: Native Android Bitmap API with WebP format (hardware-accelerated)
- **Audio Encoder**: FFmpeg-Kit with Opus codec
- **Video Encoder**: FFmpeg-Kit with H.265/HEVC codec
- **Parallel Processing**: Configurable thread pool for concurrent image compression
- **Sequential Processing**: Audio and video processed sequentially to manage memory
- **Accurate Metrics**: Tracks actual file counts (X/Y), failed files, and true reduction percentages

### Background Operations

- **Foreground Service**: Operations continue when app is minimized or screen is off
- **Persistent Notifications**: Real-time progress updates in status bar (file count, current file, percentage)
- **Completion Notifications**: Dismissible notifications remain visible after operations complete
- **Thread Safety**: All I/O operations run on Dispatchers.IO to prevent UI freezing

### Progress Tracking

- JSON-based progress file updated in real-time
- Polling interval: 500ms (background operations) / 500ms (UI updates)
- Tracks: file count, current file, speed (files/sec), ETA, batch info, compression statistics
- Case-insensitive file filtering for Windows compatibility (.RPA, .RPYC, .RPY)

## Architecture

- **Language**: Kotlin, Python
- **UI Framework**: Jetpack Compose with Material Design 3
- **Python Integration**: Chaquopy (Python 3.8)
- **RPA Library**: rpatool.py (modified for progress tracking)
- **Decompiler**: unrpyc (CensoredUsername's fork)
- **Compression**: FFmpeg-Kit 6.0 (full build), Android Bitmap API
- **Code Editor**: Sora Editor with TextMate grammar support
- **File Picker**: Jetpack Compose-based picker with multi-select
- **Concurrency**: Kotlin Coroutines with structured concurrency

## Credits

- **RPA Format**: Based on [Ren'Py](https://www.renpy.org/) archive specification
- **Python Integration**: [Chaquopy](https://chaquo.com/chaquopy/)
- **Media Compression**: [FFmpeg-Kit](https://github.com/arthenica/ffmpeg-kit)
- **Code Editor**: [Sora Editor](https://github.com/Rosemoe/sora-editor)
- **Folder Icons**: [Icons8](https://icons8.com/)
- **Rpatool**: [Shizmob](https://codeberg.org/shiz/rpatool)
- **Unrpyc**: [CensoredUsername](https://github.com/CensoredUsername/unrpyc)

## License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Disclaimer

This tool is intended for educational purposes and for modding/backing up Ren'Py games you own. Respect game developers' intellectual property and terms of service.

## Support

For issues, questions, or feature requests, please open an issue on the [GitHub Issues](../../issues) page.
