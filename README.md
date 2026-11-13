# Social Video Downloader

Native Android app (Kotlin · Jetpack Compose · Material 3) for saving **videos you own or have permission to save** from TikTok, Instagram and Facebook into your phone's Gallery.

- **Minimum Android version:** Android 10 (API 29). Target SDK 35.
- **Latest tested APK:** published to the [`apk-builds` branch](https://github.com/robertlangat/Vediodownloader/tree/apk-builds) after every passing CI run on `main`.

## What the app does

The app uses a dark theme with red accents. The home screen has:

- **Storage card:** an "Allow saving videos" card with an **ALLOW** button, shown until you grant the storage permission.
- **Link field:** a white **Paste video link** field with a red Paste shortcut. You can also share a link from the TikTok, Instagram or Facebook app straight into Social Video Downloader.
- **DOWNLOAD button:** large and red. It is enabled only for a valid video link from a supported platform. One tap checks the link and downloads the video as soon as it is available.
- **Supported apps:** TikTok, Instagram and Facebook tiles. The selected app is ringed in red and gets a check badge. Pasting a link selects the matching app automatically. The tiles use the app's own original icons, not the platforms' official logos.
- **OPEN button:** opens the selected app, or its website if the app isn't installed.
- **Banner ad:** an AdMob banner at the bottom.

While it works, the app shows **Checking link…**, then download progress with a **Cancel** button.

- **Network failure or interrupted download:** the app shows **Retry**, and the partial file is deleted.
- **Private, removed, not yours, or not downloadable:** the app explains why and offers **Open original post**.
- **Download finished:** the app shows **Open video** and **Share**. An interstitial ad may appear before the video opens, at most once every 2 minutes.

**Settings** (gear icon):

- **Storage:**
  - the storage permission status, with an **Allow** button;
  - the **Save folder** (default `Movies/SocialVideoDownloader`, which appears in Gallery), with **Choose folder** to pick any other folder on the phone and **Use default** to switch back.
- **Sign in (optional):** Sign in to Instagram, Facebook or TikTok inside the app, plus **Sign out of all**.
- **Ads & privacy:** a **Privacy options** button, shown where consent rules apply.

## Ads (AdMob)

| | ID |
|---|---|
| App ID | `ca-app-pub-8306955081407788~3472373851` |
| Banner (anchored adaptive, bottom of home screen) | `ca-app-pub-8306955081407788/6453760418` |
| Interstitial (after a finished download, on Open video or Share) | `ca-app-pub-8306955081407788/4964263977` |

- **Consent:** Google's User Messaging Platform asks for consent where the law requires it (EEA, UK, Switzerland) before any ad loads.
- **Test ads:** build with `./gradlew assembleDebug -PuseTestAds=true` to use Google's sample ad units while developing.
- **Live ads on your own phone:** don't tap your own live ads. AdMob can suspend accounts for invalid clicks.
- **New apps:** live ads may not fill until the app is set up and reviewed in AdMob.

## How downloads work

Everything happens on the phone; there is no server and nothing to configure.

1. The app opens the post in a hidden, built-in browser (Android WebView) using the phone's own internet connection and any sign-ins you have made in the app.
2. It reads the video file address that the page itself contains:
   - **TikTok:** `playAddr` in the page data. If that's missing, the address the page's player loads.
   - **Instagram:** `video_versions` / `video_url`.
   - **Facebook:** `browser_native_hd_url` / `browser_native_sd_url` / `playable_url`.
3. It downloads that file with the same browser cookies and saves it to your save folder.

| Platform | Links | Notes |
|---|---|---|
| TikTok | `tiktok.com/@user/video/…`, `vm.tiktok.com/…`, `vt.tiktok.com/…`, `tiktok.com/t/…` | Public videos usually work without signing in. |
| Instagram | `/reel/…`, `/p/…`, `/reels/…`, `/tv/…`, `/share/…` | Instagram usually requires sign-in. The app shows **Sign in to Instagram** when needed. Private accounts work only if you follow them. |
| Facebook | `watch?v=…`, `/videos/…`, `/reel/…`, `fb.watch/…`, `/share/v/…`, `/share/r/…` | Public videos often work. Sign in for videos only visible to logged-in users. |

**Limitations:**

- **Unofficial method:** this is not an official API, and it goes against these platforms' terms of service.
- **It can break:** the platforms change their pages often. When they do, the page parser (`data/VideoUrlParser.kt`) needs updating.
- **Store policies:** Google Play and AdMob policies prohibit apps that download from these platforms against their terms. Distributing this app with live ads risks your AdMob account.
- **Rights:** only download videos you own or have permission to save.

## Build

```bash
./gradlew assembleDebug                    # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest                # JVM unit tests (link parsing, API parsing)
./gradlew connectedDebugAndroidTest        # emulator/device tests (local post pages + file server)
```

Requirements: JDK 17 and the Android SDK (API 35). Open the folder in Android Studio, or use the Gradle wrapper (Gradle 8.14.3, AGP 8.10.1, Kotlin 2.1.21).

CI (`.github/workflows/android.yml`) runs all of the above on every push, including the emulator tests on API 30. The APK, `test-report.md` and UI screenshots are saved as the run's **apk-and-reports** artifact.

To publish the test build as a GitHub pre-release, push the tag from your computer:

```bash
git tag v0.1.0-test && git push origin v0.1.0-test
```

You can also create the release `v0.1.0-test` on GitHub's Releases page and tick **Set as a pre-release**. Either way, the workflow builds `SocialVideoDownloader-v0.1.0-test.apk` and attaches it, together with the test report.

## Testing notes

- **Emulator tests:** the app runs against local copies of TikTok, Instagram and Facebook post pages that contain the same data fields the real pages use. They cover:
  - download from each platform;
  - an Instagram sign-in wall;
  - a removed post;
  - an invalid link;
  - an interrupted download followed by Retry;
  - Cancel;
  - the Settings screen.
- **Saved-video checks:** each saved video is checked in MediaStore, decoded, and played with MediaPlayer.
- **Live checks:** `LiveExtractionTest` also tries real public posts from the CI emulator and reports the results without failing the build. CI runs from a data-centre network, which these sites often block or treat differently from a phone, so real results on your phone can be better.
- **Foreground only:** downloads run while the app is open.
- **Updates install in place:** the debug APK is signed with the committed `app/debug.keystore`, so newer test builds install over older ones.

## Project layout

```
app/src/main/java/com/robertlangat/socialvideodownloader/
  data/LinkParser.kt        platform + link recognition
  data/WebVideoExtractor.kt hidden-WebView page loading + sign-in cookies
  data/VideoUrlParser.kt    finds the video address in TikTok / Instagram / Facebook pages
  data/VideoDownloader.kt   streaming download → MediaStore (Gallery)
  ui/DownloadViewModel.kt   state machine: Checking → Downloading → Saved / Retry / Unavailable
  ui/HomeScreen.kt          Compose UI
```

Respect creators' rights and each platform's terms. Only download content you own or have permission to save.
