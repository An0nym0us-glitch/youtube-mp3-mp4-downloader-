# YT Downloader (Android)

Android app that downloads a **YouTube video or a whole playlist** as **MP3** (audio) or **MP4** (video).
Everything runs on the phone: the app ships with [yt-dlp](https://github.com/yt-dlp/yt-dlp), Python and ffmpeg
(via [youtubedl-android](https://github.com/JunkFood02/youtubedl-android)). No server is needed.

## Features

- Paste a link, or tap **Share → YT Downloader** in the YouTube app
- Video **and** playlist links. For playlists, tick which videos you want
- **MP3** at 128–320 kbps, or **MP4** in any resolution the video actually has (H.264/AAC preferred so it plays everywhere)
- Saves **one ZIP per download** to `Download/YT Downloader/`, for a single video or a full playlist. Extract it with any file manager
- Downloads keep running in the background with a progress notification and a Cancel button
- **Update yt-dlp** button (⟳) plus a daily auto-update, because YouTube changes often

### Settings (nothing hard-coded)

| Setting | Default |
| --- | --- |
| Pack into a ZIP (off = loose files) | On |
| Save folder inside `Download/` | `YT Downloader` |
| File name template (yt-dlp syntax) | `%(title)s` |
| Embed tags & cover art | On |
| Default format / MP3 bitrate / MP4 resolution | MP3 / 320 kbps / Best |
| Auto-update yt-dlp | On |

## Install

1. Open the repo's **Actions** tab, then the latest **Build Android APK** run, then download the `yt-downloader-apks` artifact.
   (Pushing a tag like `v1.0.0` also attaches the APKs to a GitHub Release.)
2. Install the APK on your phone:
   - `app-arm64-v8a-release.apk` for almost every phone from the last ~8 years
   - `app-armeabi-v7a-release.apk` for older 32-bit phones
   - `app-universal-release.apk` if you're unsure (bigger, works everywhere)
3. Allow "Install unknown apps" for your browser or file manager when Android asks.

Requires Android 8.0+. The first launch takes a few seconds while the bundled engine unpacks.

## Build locally

Needs JDK 17 and the Android SDK (Android Studio works).

```sh
./gradlew assembleRelease
# APKs: app/build/outputs/apk/release/
```

Release builds are signed with the debug key so they can be sideloaded straight away. Use your own keystore before publishing anywhere.

## Notes

Only download content you own or have permission to download. Respect YouTube's Terms of Service and copyright law.
