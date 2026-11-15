package com.example.opendash.google

import android.app.Activity
import android.app.Application
import android.app.Presentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.example.opendash.dash.nav.GeoPoint
import com.example.opendash.dash.video.DashEncoder
import com.example.opendash.util.DebugLog
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.libraries.navigation.ForceNightMode
import com.google.android.libraries.navigation.NavigationApi
import com.google.android.libraries.navigation.NavigationViewForAuto
import com.google.android.libraries.navigation.Navigator
import com.google.android.libraries.navigation.RoutingOptions
import com.google.android.libraries.navigation.Waypoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class GoogleNavStatus { IDLE, STARTING, ROUTING, GUIDING, ERROR }

data class GoogleNavState(
    val status: GoogleNavStatus = GoogleNavStatus.IDLE,
    val message: String? = null,
    /** Google's route line, exported so the offline renderer can draw it if frames stall. */
    val route: List<GeoPoint> = emptyList(),
)

/**
 * Google Maps on the dash with the phone locked.
 *
 * The Navigation SDK's map view is hosted in a [Presentation] on a private
 * [VirtualDisplay] the size of the Tripper frame (526 x 300). That display has nothing
 * to do with the phone's real screen, so the phone can sleep. The display's pixels land
 * in an [ImageReader]; the dash frame loop copies the latest image into its frame with
 * [copyLatestFrame] and hands it to the existing H.264 encoder.
 *
 * Guidance (route, rerouting, traffic, voice) is all Google's. Turn data for the dash's
 * own widget arrives through [GoogleNavInfoService].
 *
 * All public methods except [copyLatestFrame] and [isFrameFresh] must be called on the
 * main thread: the SDK, the Presentation and the map view are main-thread objects.
 */
class GoogleNavEngine private constructor(private val app: Application) {

    private val config = GoogleNavConfig.get(app)
    private val main = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(GoogleNavState())
    val state = _state.asStateFlow()

    private var navigator: Navigator? = null
    private var keyApplied: String? = null

    // ── Off-screen display ──────────────────────────────────────────────────
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var mapView: NavigationViewForAuto? = null
    private var map: GoogleMap? = null
    private var perspective = GoogleMap.CameraPerspective.TILTED

    private val frameLock = Any()
    private var staging: Bitmap? = null          // row-padded copy target
    @Volatile private var lastFrameAt = 0L       // uptime of the last image Google drew
    @Volatile private var haveFrame = false

    val isConfigured: Boolean get() = config.hasKey
    /** True when the saved key differs from the one the SDK started with. */
    val keyChangeNeedsRestart: Boolean get() = keyApplied != null && keyApplied != config.apiKey
    val termsAccepted: Boolean
        get() = applyKey() && runCatching { NavigationApi.areTermsAccepted(app) }.getOrDefault(false)
    val isActive: Boolean
        get() = _state.value.status.let {
            it == GoogleNavStatus.STARTING || it == GoogleNavStatus.ROUTING || it == GoogleNavStatus.GUIDING
        }

    /** True when Google has produced a map image recently enough to trust on the dash. */
    fun isFrameFresh(maxAgeMs: Long = STALE_MS): Boolean =
        haveFrame && SystemClock.uptimeMillis() - lastFrameAt < maxAgeMs

    private fun applyKey(): Boolean {
        val key = config.apiKey
        if (key.isBlank()) return false
        // The SDK accepts a key once per app launch. A changed key takes effect after
        // the app is closed and reopened; until then the first key stays in use.
        if (keyApplied == null) {
            runCatching { NavigationApi.setApiKey(key) }
                .onFailure { DebugLog.w(TAG) { "setApiKey failed: ${it.message}" } }
            keyApplied = key
        }
        return true
    }

    /**
     * Google requires each user to accept the Navigation SDK terms once. Must be shown
     * from an Activity; call it from Settings before the first ride.
     */
    fun showTerms(activity: Activity, onResult: (Boolean) -> Unit) {
        if (!applyKey()) { onResult(false); return }
        runCatching {
            NavigationApi.showTermsAndConditionsDialog(activity, "Aditya Dash") { accepted ->
                onResult(accepted)
            }
        }.onFailure {
            DebugLog.w(TAG) { "Terms dialog failed: ${it.message}" }
            onResult(false)
        }
    }

    /**
     * Start Google guidance to a destination and begin drawing the map off-screen.
     * [onFailed] fires (on the main thread) if Google cannot be used, so the caller can
     * fall back to the offline engine.
     */
    fun start(name: String, lat: Double, lng: Double, voice: Boolean, onFailed: (String) -> Unit) {
        if (!applyKey()) { fail("Add your Google API key in Settings", onFailed); return }
        if (!termsAccepted) { fail("Accept the Google navigation terms in Settings", onFailed); return }
        _state.value = GoogleNavState(GoogleNavStatus.STARTING)
        haveFrame = false
        GoogleNavInfoService.clear()

        val begin: (Navigator) -> Unit = { nav -> route(nav, name, lat, lng, voice, onFailed) }
        navigator?.let { begin(it); return }
        runCatching {
            NavigationApi.getNavigator(app, object : NavigationApi.NavigatorListener {
                override fun onNavigatorReady(nav: Navigator) {
                    navigator = nav
                    // Keep guiding when the app's task is swiped away mid-ride.
                    runCatching { nav.setTaskRemovedBehavior(Navigator.TaskRemovedBehavior.CONTINUE_SERVICE) }
                    begin(nav)
                }
                override fun onError(errorCode: Int) {
                    fail(
                        when (errorCode) {
                            NavigationApi.ErrorCode.NOT_AUTHORIZED ->
                                "Google rejected the API key. Check that the Navigation SDK is enabled for it"
                            NavigationApi.ErrorCode.TERMS_NOT_ACCEPTED ->
                                "Accept the Google navigation terms in Settings"
                            NavigationApi.ErrorCode.NETWORK_ERROR -> "No internet for Google navigation"
                            NavigationApi.ErrorCode.LOCATION_PERMISSION_MISSING -> "Location permission is missing"
                            else -> "Google navigation error $errorCode"
                        },
                        onFailed,
                    )
                }
            })
        }.onFailure { fail("Google navigation unavailable: ${it.message}", onFailed) }
    }

    private fun route(
        nav: Navigator, name: String, lat: Double, lng: Double, voice: Boolean,
        onFailed: (String) -> Unit,
    ) {
        _state.value = GoogleNavState(GoogleNavStatus.ROUTING)
        setVoice(voice)
        runCatching {
            nav.registerServiceForNavUpdates(
                app.packageName, GoogleNavInfoService::class.java.name, 1,
            )
        }.onFailure { DebugLog.w(TAG) { "Turn feed registration failed: ${it.message}" } }

        val waypoint = runCatching {
            Waypoint.builder().setLatLng(lat, lng).setTitle(name.ifBlank { "Destination" }).build()
        }.getOrElse { fail("Invalid destination", onFailed); return }
        val options = RoutingOptions().travelMode(RoutingOptions.TravelMode.TWO_WHEELER)

        runCatching {
            nav.setDestination(waypoint, options).setOnResultListener { status ->
                when (status) {
                    Navigator.RouteStatus.OK -> {
                        runCatching { nav.startGuidance() }
                        ensureDisplay()
                        _state.value = GoogleNavState(GoogleNavStatus.GUIDING, route = exportRoute(nav))
                        runCatching {
                            nav.addRouteChangedListener {
                                _state.value = _state.value.copy(route = exportRoute(nav))
                            }
                        }
                    }
                    Navigator.RouteStatus.NETWORK_ERROR -> fail("No internet for Google routing", onFailed)
                    Navigator.RouteStatus.NO_ROUTE_FOUND -> fail("Google found no route", onFailed)
                    Navigator.RouteStatus.LOCATION_DISABLED,
                    Navigator.RouteStatus.LOCATION_UNKNOWN -> fail("Waiting for GPS. Try again in a moment", onFailed)
                    Navigator.RouteStatus.QUOTA_CHECK_FAILED -> fail("Google API quota or billing problem", onFailed)
                    else -> fail("Google routing failed: $status", onFailed)
                }
            }
        }.onFailure { fail("Google routing failed: ${it.message}", onFailed) }
    }

    private fun exportRoute(nav: Navigator): List<GeoPoint> = runCatching {
        nav.routeSegments.flatMap { seg -> seg.latLngs.map { GeoPoint(it.latitude, it.longitude) } }
    }.getOrDefault(emptyList())

    private fun fail(message: String, onFailed: (String) -> Unit) {
        DebugLog.w(TAG) { message }
        stopInternal()
        _state.value = GoogleNavState(GoogleNavStatus.ERROR, message)
        onFailed(message)
    }

    fun setVoice(enabled: Boolean) {
        runCatching {
            navigator?.setAudioGuidance(
                if (enabled) Navigator.AudioGuidance.VOICE_ALERTS_AND_GUIDANCE
                else Navigator.AudioGuidance.SILENT
            )
        }
    }

    fun stop() {
        stopInternal()
        _state.value = GoogleNavState(GoogleNavStatus.IDLE)
    }

    private fun stopInternal() {
        runCatching { navigator?.stopGuidance() }
        runCatching { navigator?.clearDestinations() }
        runCatching { navigator?.unregisterServiceForNavUpdates() }
        GoogleNavInfoService.clear()
        releaseDisplay()
    }

    // ── Camera controls (joystick / voice) ──────────────────────────────────
    fun zoomIn() = zoomBy(1f)
    fun zoomOut() = zoomBy(-1f)

    private fun zoomBy(delta: Float) {
        val m = map ?: return
        runCatching { m.animateCamera(CameraUpdateFactory.zoomBy(delta)) }
        // The SDK stops following once the camera is moved by hand; resume after a pause.
        main.removeCallbacks(refollow)
        main.postDelayed(refollow, REFOLLOW_MS)
    }

    private val refollow = Runnable { runCatching { map?.followMyLocation(perspective) } }

    fun setHeadingUp(headingUp: Boolean) {
        perspective = if (headingUp) GoogleMap.CameraPerspective.TILTED
                      else GoogleMap.CameraPerspective.TOP_DOWN_NORTH_UP
        runCatching { map?.followMyLocation(perspective) }
    }

    fun applyNightMode() {
        runCatching {
            map?.setMapColorScheme(
                if (config.nightMap) com.google.android.gms.maps.model.MapColorScheme.DARK
                else com.google.android.gms.maps.model.MapColorScheme.LIGHT
            )
        }
    }

    // ── Off-screen display plumbing ─────────────────────────────────────────
    private fun ensureDisplay() {
        if (presentation != null) return
        runCatching {
            val w = DashEncoder.WIDTH
            val h = DashEncoder.HEIGHT
            val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3)
            r.setOnImageAvailableListener({ onImage(it) }, imageHandler())
            val dm = app.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val vd = dm.createVirtualDisplay(
                "aditya-dash-map", w, h, DISPLAY_DPI, r.surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
            )
            val p = Presentation(app, vd.display)
            // Build the map with the presentation's context so it lays out for the
            // virtual display's density, not the phone screen's.
            val view = NavigationViewForAuto(p.context)
            view.onCreate(null)
            view.onStart()
            view.onResume()
            p.setContentView(view)
            p.show()
            view.getMapAsync { m ->
                map = m
                runCatching { m.isTrafficEnabled = true }
                runCatching { m.setBuildingsEnabled(false) }
                applyNightMode()
                runCatching { m.followMyLocation(perspective) }
            }
            reader = r; display = vd; presentation = p; mapView = view
            DebugLog.i(TAG) { "Off-screen Google map display up (${w}x$h at $DISPLAY_DPI dpi)" }
        }.onFailure {
            DebugLog.e(TAG, { "Off-screen display failed" }, it)
            releaseDisplay()
            _state.value = _state.value.copy(message = "Google map view unavailable: ${it.message}")
        }
    }

    private fun releaseDisplay() {
        main.removeCallbacks(refollow)
        runCatching { mapView?.onPause(); mapView?.onStop(); mapView?.onDestroy() }
        runCatching { presentation?.dismiss() }
        runCatching { display?.release() }
        runCatching { reader?.close() }
        mapView = null; presentation = null; display = null; reader = null; map = null
        haveFrame = false
    }

    private var imageThread: android.os.HandlerThread? = null
    private fun imageHandler(): Handler {
        val t = imageThread ?: android.os.HandlerThread("google-map-frames").also {
            it.start(); imageThread = it
        }
        return Handler(t.looper)
    }

    /** Copy each image Google draws into [staging]; cheap at 526 x 300. */
    private fun onImage(r: ImageReader) {
        val image = runCatching { r.acquireLatestImage() }.getOrNull() ?: return
        try {
            val plane = image.planes[0]
            val rowPixels = plane.rowStride / plane.pixelStride
            synchronized(frameLock) {
                var bmp = staging
                if (bmp == null || bmp.width != rowPixels || bmp.height != image.height) {
                    bmp?.recycle()
                    bmp = Bitmap.createBitmap(rowPixels, image.height, Bitmap.Config.ARGB_8888)
                    staging = bmp
                }
                plane.buffer.rewind()
                bmp.copyPixelsFromBuffer(plane.buffer)
            }
            lastFrameAt = SystemClock.uptimeMillis()
            haveFrame = true
        } catch (e: Exception) {
            DebugLog.w(TAG) { "Frame copy failed: ${e.message}" }
        } finally {
            image.close()
        }
    }

    private val srcRect = Rect(0, 0, DashEncoder.WIDTH, DashEncoder.HEIGHT)
    private val dstRect = Rect(0, 0, DashEncoder.WIDTH, DashEncoder.HEIGHT)

    /** Draw Google's latest map image onto the dash frame. False if there is none yet. */
    fun copyLatestFrame(canvas: Canvas): Boolean {
        if (!haveFrame) return false
        synchronized(frameLock) {
            val bmp = staging ?: return false
            canvas.drawBitmap(bmp, srcRect, dstRect, null)
        }
        return true
    }

    companion object {
        private const val TAG = "GoogleNavEngine"
        /**
         * The Tripper is about 150 px per inch but is read from arm's length, so the map
         * is laid out a little denser than that to keep road names legible at a glance.
         */
        private const val DISPLAY_DPI = 213
        private const val STALE_MS = 4_000L
        private const val REFOLLOW_MS = 8_000L

        @Volatile private var instance: GoogleNavEngine? = null
        fun get(app: Application): GoogleNavEngine =
            instance ?: synchronized(this) {
                instance ?: GoogleNavEngine(app).also { instance = it }
            }
    }
}
