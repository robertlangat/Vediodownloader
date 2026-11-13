# Social Video Downloader

Native Android app (Kotlin · Jetpack Compose · Material 3) for saving **videos you own or have permission to save** from TikTok, Instagram and Facebook into your phone's Gallery.

- **Minimum Android version:** Android 10 (API 29). Target SDK 35.
- **Test build:** `SocialVideoDownloader-v0.1.0-test.apk`, from [Releases](https://github.com/robertlangat/Vediodownloader/releases) once `v0.1.0-test` is published (see *Build* below), or from the latest CI run's artifacts.

## What the app does

Home screen, top to bottom:

1. **TikTok / Instagram / Facebook** buttons. The selected one is highlighted. Pasting a supported link selects the matching platform automatically.
2. **Paste video link** field with a **Paste** shortcut. You can also share a link from the TikTok, Instagram or Facebook app straight into Social Video Downloader.
3. A large **Download** button. It is enabled only when the field holds a valid video link from a supported platform. One tap checks the link and starts the download as soon as the video is available.

While it works, the app shows **Checking link…**, then download progress with a **Cancel** button.

- **Network failure or interrupted download:** the app shows **Retry**, and the partial file is deleted.
- **Private, removed, not yours, or not downloadable:** the app explains why and offers **Open original post**.
- **Download finished:** the video is saved with Android MediaStore to `Movies/SocialVideoDownloader`, so it appears in Gallery and Google Photos. The app then shows **Open video** and **Share**.

## How downloads are authorised

The app never scrapes platforms. A small backend (`backend/`) holds the provider secrets and uses only official endpoints:

| Platform | Integration | Links it can resolve | What gets downloaded |
|---|---|---|---|
| **Instagram** | Instagram API with Instagram Login (`graph.instagram.com`, scope `instagram_business_basic`) | `instagram.com/reel/…`, `/p/…`, `/reels/…`, `/tv/…`, `/{user}/reel/…`, and `/share/…` short links (expanded) | Videos and reels posted by **your own Instagram Professional (Business or Creator) account** that you connect. The app matches the shortcode against your media and downloads the `media_url` Instagram provides. Other people's posts return "not in your account". |
| **Facebook** | Facebook Login + Graph API (`graph.facebook.com`, `pages_show_list`, `pages_read_engagement`, `pages_read_user_content`) | `facebook.com/watch?v=…`, `/{page}/videos/…`, `/reel/{id}`, `/video.php?v=…`, and `fb.watch/…` / `/share/v/…` / `/share/r/…` short links (expanded) | Videos on **Facebook Pages you manage**. The app downloads the video `source` the Graph API provides. Personal-profile videos and other people's videos are not available through the API. |
| **TikTok** | Official public oEmbed (`tiktok.com/oembed`) | `tiktok.com/@user/video/…`, `vm.tiktok.com/…`, `vt.tiktok.com/…`, `tiktok.com/t/…` | **Nothing.** TikTok's official APIs (Display API, Content Posting API) do not provide video files. The app confirms whether the post exists, then asks you to open it in TikTok and use **Save video** (shown when the creator allows downloads). |

## Backend setup (required for Instagram and Facebook)

The secrets stay on the backend and never ship in the APK.

1. **Create a Meta app** at <https://developers.facebook.com/apps>. Choose type **Business**.
   - Add **Instagram → API setup with Instagram login**. Copy the *Instagram app ID* and *Instagram app secret*. Add your Instagram Professional account as an Instagram tester and accept the invite in Instagram settings.
   - Add **Facebook Login for Business**. Copy the app ID and app secret from *App settings → Basic*.
   - Set the redirect URIs to `https://YOUR-BACKEND/auth/instagram/callback` and `https://YOUR-BACKEND/auth/facebook/callback`.
   - While the app is in *Development* mode, only app roles and testers can log in. That is enough for testing with your own accounts.
2. **Deploy `backend/`**. It needs Node.js 18+ and has no dependencies. Pick one:
   - **Render:** use *New → Blueprint* and pick this repository (`render.yaml`). Then fill in the environment variables.
   - **Docker:** run `docker build -t svd-backend backend && docker run -p 8080:8080 --env-file backend/.env svd-backend`.
   - **Locally, for development:** run `cd backend && cp .env.example .env`, fill it in, then `node --env-file=.env src/server.js`. Expose it over HTTPS (for example with a tunnel), because the app only talks to HTTPS backends except for `localhost`, `127.0.0.1` and `10.0.2.2`.

   | Variable | Value |
   |---|---|
   | `PUBLIC_BASE_URL` | Public HTTPS URL of the backend |
   | `INSTAGRAM_APP_ID`, `INSTAGRAM_APP_SECRET` | From step 1 |
   | `FACEBOOK_APP_ID`, `FACEBOOK_APP_SECRET` | From step 1 |
   | `SESSION_FILE` | Path for stored tokens (default `./data/sessions.json`). Use a persistent disk. |

   Check that it's running: `GET /health` should return `{"ok":true,...}`.
3. **Point the app at the backend.** Either:
   - in the app, tap ⚙ → **Backend URL** → Save, then **Connect Instagram** or **Connect Facebook**; or
   - bake the URL into the APK: run `./gradlew assembleDebug -PbackendUrl=https://YOUR-BACKEND`, or set the repository variable `BACKEND_URL` so CI builds include it.

### Backend API

```
POST /api/resolve        Authorization: Bearer <session>     {"url": "...", "platform": "instagram"}
→ {"status":"ready","downloadUrl":"...","fileName":"...","mimeType":"video/mp4"}
→ {"status":"unavailable","reason":"private|removed|not_owned|not_downloadable|not_video|invalid|not_configured","message":"...","originalUrl":"..."}
→ {"status":"auth_required","platform":"instagram","authUrl":".../auth/instagram/start","message":"..."}
→ {"status":"retry","message":"..."}          (temporary; the app shows Retry)
GET  /auth/{instagram|facebook}/start?session=…   → OAuth → socialvideodl://auth?session=…&platform=…
```

## Build

```bash
./gradlew assembleDebug                    # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest                # JVM unit tests (link parsing, API parsing)
./gradlew connectedDebugAndroidTest        # emulator/device tests (mock backend + CDN)
cd backend && npm test                     # backend tests (Node 18+)
```

Requirements: JDK 17 and the Android SDK (API 35). Open the folder in Android Studio, or use the Gradle wrapper (Gradle 8.14.3, AGP 8.10.1, Kotlin 2.1.21).

CI (`.github/workflows/android.yml`) runs all of the above on every push, including the emulator tests on API 30. The APK, `test-report.md` and UI screenshots are saved as the run's **apk-and-reports** artifact.

To publish the test build as a GitHub pre-release, push the tag from your computer:

```bash
git tag v0.1.0-test && git push origin v0.1.0-test
```

You can also create the release `v0.1.0-test` on GitHub's Releases page and tick **Set as a pre-release**. Either way, the workflow builds `SocialVideoDownloader-v0.1.0-test.apk` and attaches it, together with the test report.

## Testing notes

- The emulator tests run the real app against a local mock backend and CDN that return the same JSON as the real backend. They cover:
  - paste-to-download for each platform;
  - the Paste shortcut auto-selecting the platform;
  - an invalid link;
  - an unavailable post;
  - an interrupted download followed by Retry;
  - Cancel.
- Each saved video is checked in MediaStore: not pending, has a video track, correct duration, a decodable frame, and MediaPlayer playback.
- Real Instagram and Facebook downloads need the backend set up with your Meta app and your own account or Page.
- This test build downloads in the foreground. Keep the app open until the download finishes.
- The debug APK is signed with the committed `app/debug.keystore`, so newer test builds install over older ones.

## Project layout

```
app/src/main/java/com/robertlangat/socialvideodownloader/
  data/LinkParser.kt        platform + link recognition
  data/ResolverClient.kt    backend API client
  data/VideoDownloader.kt   streaming download → MediaStore (Gallery)
  ui/DownloadViewModel.kt   state machine: Checking → Downloading → Saved / Retry / Unavailable
  ui/HomeScreen.kt          Compose UI
backend/                    Node.js resolver (Instagram, Facebook, TikTok)
```

Respect creators' rights and each platform's terms. Only download content you own or have permission to save.
