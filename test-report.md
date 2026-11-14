# Social Video Downloader – automated test report

## Android unit tests (JVM)

| Test | Result | Time (s) |
|---|---|---|
| `fileNames` | PASS | 0.017 |
| `acceptsLinkWithoutScheme` | PASS | 0.026 |
| `extractsUrlFromShareText` | PASS | 0.0 |
| `httpIsUpgraded` | PASS | 0.001 |
| `invalidLinks` | PASS | 0.001 |
| `instagramLinks` | PASS | 0.001 |
| `tiktokLinks` | PASS | 0.001 |
| `facebookLinks` | PASS | 0.002 |
| `facebookPlayableUrlFallback` | PASS | 0.007 |
| `tiktokPlayAddrFromRehydrationJson` | PASS | 0.0 |
| `instagramDoubleEscapedEmbed` | PASS | 0.001 |
| `instagramLoginWall` | PASS | 0.0 |
| `facebookPrefersHd` | PASS | 0.0 |
| `instagramVideoVersions` | PASS | 0.001 |
| `ignoresBlobAndMissing` | PASS | 0.0 |
| `tiktokRemovedStatus` | PASS | 0.001 |

## Android emulator tests (instrumented)

| Test | Result | Time (s) |
|---|---|---|
| `home_showsSupportedAppsAndPermissionButton_tapSelectsPlatform` | PASS | 3.41 |
| `pasteShortcut_readsClipboard_andAutoSelectsPlatform` | PASS | 2.248 |
| `cancelDuringDownload_removesPartialFile` | PASS | 6.665 |
| `interruptedDownload_showsRetry_thenSucceeds` | PASS | 7.186 |
| `unavailablePost_explainsAndOffersOriginal` | PASS | 3.91 |
| `facebook_pasteToDownload_savesPlayableVideo` | PASS | 4.933 |
| `instagramLoginWall_offersSignIn` | PASS | 3.84 |
| `invalidLink_keepsDownloadDisabled` | PASS | 2.988 |
| `settings_showsStorageFolderAndSignIns_noServiceAddress` | PASS | 1.444 |
| `instagram_pasteToDownload_savesPlayableVideo` | PASS | 4.412 |
| `tiktok_pasteToDownload_savesPlayableVideo` | PASS | 5.144 |
| `liveExtraction_report` | PASS | 6.523 |
| `captureStoreScreens` | PASS | 12.71 |

_Device: emulator-5554 - 11_

**Overall: ALL PASSED**

## Emulator log (SVD-TEST)
```
--------- beginning of main
--------- beginning of system
--------- beginning of kernel
11-14 12:17:17.667 D/SVD-EXTRACT( 4018): extract start INSTAGRAM target=https://www.instagram.com/reel/SLOWreel001/ fake=true
11-14 12:17:19.081 D/SVD-EXTRACT( 4018): poll url=https://www.instagram.com/reel/SLOWreel001/ html=175 video= media=0
11-14 12:17:23.558 D/SVD-EXTRACT( 4018): extract start FACEBOOK target=https://www.facebook.com/SomePage/videos/1234567890123/ fake=true
11-14 12:17:24.854 D/SVD-EXTRACT( 4018): poll url=https://www.facebook.com/SomePage/videos/1234567890123/ html=231 video= media=0
11-14 12:17:25.322 D/SVD-EXTRACT( 4018): extract start FACEBOOK target=https://www.facebook.com/SomePage/videos/1234567890123/ fake=true
11-14 12:17:26.552 D/SVD-EXTRACT( 4018): poll url=https://www.facebook.com/SomePage/videos/1234567890123/ html=223 video= media=0
11-14 12:17:28.345 I/SVD-TEST( 4018): ACTION_VIEW handler for saved video: com.google.android.apps.photos
11-14 12:17:30.535 D/SVD-EXTRACT( 4018): extract start TIKTOK target=https://www.tiktok.com/@someone/video/9999999999123 fake=true
11-14 12:17:31.771 D/SVD-EXTRACT( 4018): poll url=https://www.tiktok.com/@someone/video/9999999999123 html=157 video= media=0
11-14 12:17:34.376 D/SVD-EXTRACT( 4018): extract start FACEBOOK target=https://www.facebook.com/watch/?v=1234567890123 fake=true
11-14 12:17:35.606 D/SVD-EXTRACT( 4018): poll url=https://www.facebook.com/watch/?v=1234567890123 html=223 video= media=0
11-14 12:17:37.385 I/SVD-TEST( 4018): ACTION_VIEW handler for saved video: com.google.android.apps.photos
11-14 12:17:37.386 I/SVD-TEST( 4018): PASS facebook saved=content://media/external_primary/video/media/24 status='Saved to Gallery: Movies/SocialVideoDownloader/facebook_1794658655608.mp4'
11-14 12:17:39.466 D/SVD-EXTRACT( 4018): extract start INSTAGRAM target=https://www.instagram.com/accounts/login/?next=%2Freel%2F fake=true
11-14 12:17:40.737 D/SVD-EXTRACT( 4018): poll url=https://www.instagram.com/accounts/login/?next=%2Freel%2F html=45 video= media=0
11-14 12:17:47.534 D/SVD-EXTRACT( 4018): extract start INSTAGRAM target=https://www.instagram.com/reel/C1a2B3c4D5e/ fake=true
11-14 12:17:48.775 D/SVD-EXTRACT( 4018): poll url=https://www.instagram.com/reel/C1a2B3c4D5e/ html=173 video= media=0
11-14 12:17:50.613 I/SVD-TEST( 4018): ACTION_VIEW handler for saved video: com.google.android.apps.photos
11-14 12:17:50.614 I/SVD-TEST( 4018): PASS instagram saved=content://media/external_primary/video/media/33 status='Saved to Gallery: Movies/SocialVideoDownloader/instagram_1794658668776.mp4'
11-14 12:17:52.068 D/SVD-EXTRACT( 4018): extract start TIKTOK target=https://vm.tiktok.com/ZMeAbCdEf/ fake=true
11-14 12:17:53.297 D/SVD-EXTRACT( 4018): poll url=https://vm.tiktok.com/ZMeAbCdEf/ html=274 video= media=0
11-14 12:17:55.119 I/SVD-TEST( 4018): ACTION_VIEW handler for saved video: com.google.android.apps.photos
11-14 12:17:55.120 I/SVD-TEST( 4018): PASS tiktok saved=content://media/external_primary/video/media/35 status='Saved to Gallery: Movies/SocialVideoDownloader/tiktok_1794658673299.mp4'
11-14 12:17:56.171 D/SVD-EXTRACT( 4018): extract start TIKTOK target=https://www.tiktok.com/@scout2015/video/6718335390845095173 fake=false
11-14 12:17:57.554 D/SVD-EXTRACT( 4018): poll url=https://www.tiktok.com/@scout2015/video/6718335390845095173 html=416047 video= media=0
11-14 12:17:58.474 I/SVD-LIVE( 4018): TikTok: FOUND video -> file HTTP 206 video/mp4 65536 bytes
11-14 12:17:58.474 D/SVD-EXTRACT( 4018): extract start FACEBOOK target=https://www.facebook.com/facebook/videos/10153231379946729/ fake=false
11-14 12:17:59.695 D/SVD-EXTRACT( 4018): poll url=https://www.facebook.com/facebook/videos/10153231379946729/ html=122208 video= media=0
11-14 12:18:00.747 D/SVD-EXTRACT( 4018): poll url=https://www.facebook.com/facebook/videos/10153231379946729/ html=567607 video= media=0
11-14 12:18:01.373 I/SVD-LIVE( 4018): Facebook: FOUND video -> file HTTP 206 video/mp4 65536 bytes
11-14 12:18:01.374 D/SVD-EXTRACT( 4018): extract start INSTAGRAM target=https://www.instagram.com/p/aye83DjauH/ fake=false
11-14 12:18:02.590 D/SVD-EXTRACT( 4018): poll url=https://www.instagram.com/p/aye83DjauH/ html=126028 video= media=0
11-14 12:18:02.684 I/SVD-LIVE( 4018): Instagram: FOUND video -> file HTTP 206 video/mp4 65536 bytes
11-14 12:18:04.172 I/SVD-TEST( 4018): store screenshot store_1_link_detected 1080x1920
11-14 12:18:04.325 D/SVD-EXTRACT( 4018): extract start INSTAGRAM target=https://www.instagram.com/reel/C7301abcDEF/ fake=true
11-14 12:18:05.554 D/SVD-EXTRACT( 4018): poll url=https://www.instagram.com/reel/C7301abcDEF/ html=275 video= media=0
11-14 12:18:11.790 I/SVD-TEST( 4018): store screenshot store_2_downloading 1080x1920
11-14 12:18:12.160 D/SVD-EXTRACT( 4018): extract start FACEBOOK target=https://www.facebook.com/watch/?v=1029384756102938 fake=true
11-14 12:18:13.386 D/SVD-EXTRACT( 4018): poll url=https://www.facebook.com/watch/?v=1029384756102938 html=269 video= media=0
11-14 12:18:14.124 I/SVD-TEST( 4018): store screenshot store_3_saved 1080x1920
11-14 12:18:14.799 I/SVD-TEST( 4018): store screenshot store_4_settings 1080x1920
```
