# Social Video Downloader – automated test report

## Android unit tests (JVM)

| Test | Result | Time (s) |
|---|---|---|
| `fileNames` | PASS | 0.035 |
| `acceptsLinkWithoutScheme` | PASS | 0.035 |
| `extractsUrlFromShareText` | PASS | 0.0 |
| `httpIsUpgraded` | PASS | 0.001 |
| `invalidLinks` | PASS | 0.004 |
| `instagramLinks` | PASS | 0.002 |
| `tiktokLinks` | PASS | 0.002 |
| `facebookLinks` | PASS | 0.004 |
| `facebookPlayableUrlFallback` | PASS | 0.014 |
| `tiktokPlayAddrFromRehydrationJson` | PASS | 0.0 |
| `instagramDoubleEscapedEmbed` | PASS | 0.0 |
| `instagramLoginWall` | PASS | 0.001 |
| `facebookPrefersHd` | PASS | 0.0 |
| `instagramVideoVersions` | PASS | 0.001 |
| `ignoresBlobAndMissing` | PASS | 0.0 |
| `tiktokRemovedStatus` | PASS | 0.001 |

## Android emulator tests (instrumented)

| Test | Result | Time (s) |
|---|---|---|
| `home_showsSupportedAppsAndPermissionButton_tapSelectsPlatform` | PASS | 3.502 |
| `pasteShortcut_readsClipboard_andAutoSelectsPlatform` | PASS | 1.741 |
| `cancelDuringDownload_removesPartialFile` | PASS | 7.226 |
| `interruptedDownload_showsRetry_thenSucceeds` | PASS | 7.983 |
| `unavailablePost_explainsAndOffersOriginal` | PASS | 4.574 |
| `facebook_pasteToDownload_savesPlayableVideo` | PASS | 5.396 |
| `instagramLoginWall_offersSignIn` | PASS | 4.63 |
| `invalidLink_keepsDownloadDisabled` | PASS | 2.396 |
| `settings_showsStorageFolderAndSignIns_noServiceAddress` | PASS | 1.787 |
| `instagram_pasteToDownload_savesPlayableVideo` | PASS | 5.632 |
| `tiktok_pasteToDownload_savesPlayableVideo` | PASS | 5.46 |
| `liveExtraction_report` | PASS | 8.73 |
| `captureStoreScreens` | PASS | 13.181 |

_Device: emulator-5554 - 11_

**Overall: ALL PASSED**

## Emulator log (SVD-TEST)
```
--------- beginning of main
--------- beginning of system
--------- beginning of kernel
11-19 11:40:09.414 D/SVD-EXTRACT( 3937): extract start INSTAGRAM target=https://www.instagram.com/reel/SLOWreel001/ fake=true
11-19 11:40:10.810 D/SVD-EXTRACT( 3937): poll url=https://www.instagram.com/reel/SLOWreel001/ html=175 video= media=0
11-19 11:40:15.760 D/SVD-EXTRACT( 3937): extract start FACEBOOK target=https://www.facebook.com/SomePage/videos/1234567890123/ fake=true
11-19 11:40:17.012 D/SVD-EXTRACT( 3937): poll url=https://www.facebook.com/SomePage/videos/1234567890123/ html=231 video= media=0
11-19 11:40:17.606 D/SVD-EXTRACT( 3937): extract start FACEBOOK target=https://www.facebook.com/SomePage/videos/1234567890123/ fake=true
11-19 11:40:18.829 D/SVD-EXTRACT( 3937): poll url=https://www.facebook.com/SomePage/videos/1234567890123/ html=223 video= media=0
11-19 11:40:20.916 I/SVD-TEST( 3937): ACTION_VIEW handler for saved video: com.google.android.apps.photos
11-19 11:40:23.795 D/SVD-EXTRACT( 3937): extract start TIKTOK target=https://www.tiktok.com/@someone/video/9999999999123 fake=true
11-19 11:40:25.015 D/SVD-EXTRACT( 3937): poll url=https://www.tiktok.com/@someone/video/9999999999123 html=157 video= media=0
11-19 11:40:28.124 D/SVD-EXTRACT( 3937): extract start FACEBOOK target=https://www.facebook.com/watch/?v=1234567890123 fake=true
11-19 11:40:29.409 D/SVD-EXTRACT( 3937): poll url=https://www.facebook.com/watch/?v=1234567890123 html=223 video= media=0
11-19 11:40:31.404 I/SVD-TEST( 3937): ACTION_VIEW handler for saved video: com.google.android.apps.photos
11-19 11:40:31.406 I/SVD-TEST( 3937): PASS facebook saved=content://media/external_primary/video/media/24 status='Saved to Gallery: Movies/SocialVideoDownloader/facebook_1795088429410.mp4'
11-19 11:40:33.875 D/SVD-EXTRACT( 3937): extract start INSTAGRAM target=https://www.instagram.com/accounts/login/?next=%2Freel%2F fake=true
11-19 11:40:35.107 D/SVD-EXTRACT( 3937): poll url=https://www.instagram.com/accounts/login/?next=%2Freel%2F html=45 video= media=0
11-19 11:40:42.394 D/SVD-EXTRACT( 3937): extract start INSTAGRAM target=https://www.instagram.com/reel/C1a2B3c4D5e/ fake=true
11-19 11:40:43.612 D/SVD-EXTRACT( 3937): poll url=https://www.instagram.com/reel/C1a2B3c4D5e/ html=173 video= media=0
11-19 11:40:45.474 I/SVD-TEST( 3937): ACTION_VIEW handler for saved video: com.google.android.apps.photos
11-19 11:40:45.476 I/SVD-TEST( 3937): PASS instagram saved=content://media/external_primary/video/media/33 status='Saved to Gallery: Movies/SocialVideoDownloader/instagram_1795088443614.mp4'
11-19 11:40:47.984 D/SVD-EXTRACT( 3937): extract start TIKTOK target=https://vm.tiktok.com/ZMeAbCdEf/ fake=true
11-19 11:40:49.223 D/SVD-EXTRACT( 3937): poll url=https://vm.tiktok.com/ZMeAbCdEf/ html=274 video= media=0
11-19 11:40:51.060 I/SVD-TEST( 3937): ACTION_VIEW handler for saved video: com.google.android.apps.photos
11-19 11:40:51.061 I/SVD-TEST( 3937): PASS tiktok saved=content://media/external_primary/video/media/35 status='Saved to Gallery: Movies/SocialVideoDownloader/tiktok_1795088449225.mp4'
11-19 11:40:52.386 D/SVD-EXTRACT( 3937): extract start TIKTOK target=https://www.tiktok.com/@scout2015/video/6718335390845095173 fake=false
11-19 11:40:53.993 D/SVD-EXTRACT( 3937): poll url=https://www.tiktok.com/@scout2015/video/6718335390845095173 html=406210 video= media=0
11-19 11:40:54.315 I/SVD-LIVE( 3937): TikTok: FOUND video -> file HTTP 206 video/mp4 65536 bytes
11-19 11:40:54.317 D/SVD-EXTRACT( 3937): extract start FACEBOOK target=https://www.facebook.com/facebook/videos/10153231379946729/ fake=false
11-19 11:40:56.195 D/SVD-EXTRACT( 3937): poll url=https://www.facebook.com/facebook/videos/10153231379946729/ html=134527 video= media=0
11-19 11:40:58.711 D/SVD-EXTRACT( 3937): poll url=https://www.facebook.com/facebook/videos/10153231379946729/ html=1176716 video= media=0
11-19 11:40:58.855 I/SVD-LIVE( 3937): Facebook: FOUND video -> file HTTP 206 video/mp4 65536 bytes
11-19 11:40:58.856 D/SVD-EXTRACT( 3937): extract start INSTAGRAM target=https://www.instagram.com/p/aye83DjauH/ fake=false
11-19 11:41:00.364 D/SVD-EXTRACT( 3937): poll url=https://www.instagram.com/p/aye83DjauH/ html=707723 video= media=0
11-19 11:41:00.991 I/SVD-LIVE( 3937): Instagram: FOUND video -> file HTTP 206 video/mp4 65536 bytes
11-19 11:41:03.026 I/SVD-TEST( 3937): store screenshot store_1_link_detected 1080x1920
11-19 11:41:03.215 D/SVD-EXTRACT( 3937): extract start INSTAGRAM target=https://www.instagram.com/reel/C7301abcDEF/ fake=true
11-19 11:41:04.442 D/SVD-EXTRACT( 3937): poll url=https://www.instagram.com/reel/C7301abcDEF/ html=275 video= media=0
11-19 11:41:10.633 I/SVD-TEST( 3937): store screenshot store_2_downloading 1080x1920
11-19 11:41:10.948 D/SVD-EXTRACT( 3937): extract start FACEBOOK target=https://www.facebook.com/watch/?v=1029384756102938 fake=true
11-19 11:41:12.181 D/SVD-EXTRACT( 3937): poll url=https://www.facebook.com/watch/?v=1029384756102938 html=269 video= media=0
11-19 11:41:12.940 I/SVD-TEST( 3937): store screenshot store_3_saved 1080x1920
11-19 11:41:13.676 I/SVD-TEST( 3937): store screenshot store_4_settings 1080x1920
```
