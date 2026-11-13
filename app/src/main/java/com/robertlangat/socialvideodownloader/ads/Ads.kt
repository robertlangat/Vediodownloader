package com.robertlangat.socialvideodownloader.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.robertlangat.socialvideodownloader.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AdMob integration: Google UMP consent first, then a bottom banner and an interstitial
 * shown at most once every [MIN_INTERSTITIAL_INTERVAL_MS], only at a natural break
 * (after a finished download, when the user opens or shares the video).
 */
object Ads {
    private const val TAG = "Ads"
    private const val MIN_INTERSTITIAL_INTERVAL_MS = 120_000L

    /** Set by the automated UI tests so full-screen ads never cover the screen under test. */
    @Volatile
    var disabled: Boolean = false

    /** True once consent allows ads and the SDK has been initialised. Drives the banner. */
    var ready by mutableStateOf(false)
        private set

    var privacyOptionsRequired by mutableStateOf(false)
        private set

    private val initStarted = AtomicBoolean(false)
    private var interstitial: InterstitialAd? = null
    private var loadingInterstitial = false
    private var lastInterstitialAt = 0L

    fun gatherConsentAndInit(activity: Activity) {
        if (disabled) return
        val consent = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().build()
        consent.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) Log.w(TAG, "Consent form: ${formError.message}")
                    updatePrivacyState(consent)
                    if (consent.canRequestAds()) initSdk(activity)
                }
            },
            { error ->
                Log.w(TAG, "Consent info update failed: ${error.message}")
                if (consent.canRequestAds()) initSdk(activity)
            },
        )
        // Consent from a previous session can be used straight away.
        if (consent.canRequestAds()) initSdk(activity)
    }

    private fun updatePrivacyState(consent: ConsentInformation) {
        privacyOptionsRequired =
            consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    private fun initSdk(activity: Activity) {
        if (disabled || !initStarted.compareAndSet(false, true)) return
        val app = activity.applicationContext
        MobileAds.initialize(app) {
            ready = true
            loadInterstitial(app)
        }
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            if (formError != null) Log.w(TAG, "Privacy options: ${formError.message}")
        }
    }

    private fun loadInterstitial(context: Context) {
        if (disabled || loadingInterstitial || interstitial != null) return
        loadingInterstitial = true
        InterstitialAd.load(
            context,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                    loadingInterstitial = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Interstitial failed to load: ${error.message}")
                    interstitial = null
                    loadingInterstitial = false
                }
            },
        )
    }

    /** Shows an interstitial if one is ready and the frequency cap allows it, then runs [then]. */
    fun showInterstitialThen(activity: Activity?, then: () -> Unit) {
        val ad = interstitial
        val now = SystemClock.elapsedRealtime()
        val capped = lastInterstitialAt != 0L && now - lastInterstitialAt < MIN_INTERSTITIAL_INTERVAL_MS
        if (disabled || activity == null || ad == null || capped) {
            then()
            return
        }
        interstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                lastInterstitialAt = SystemClock.elapsedRealtime()
                loadInterstitial(activity.applicationContext)
                then()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                loadInterstitial(activity.applicationContext)
                then()
            }
        }
        ad.show(activity)
    }
}

/** Anchored adaptive banner. Renders nothing until consent allows ads. */
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    if (Ads.disabled || !Ads.ready) return
    val widthDp = LocalConfiguration.current.screenWidthDp
    Box(modifier.fillMaxWidth().testTag("banner_ad"), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                AdView(ctx).apply {
                    setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, widthDp))
                    adUnitId = BuildConfig.ADMOB_BANNER_ID
                    loadAd(AdRequest.Builder().build())
                }
            },
            onRelease = { it.destroy() },
        )
    }
}
