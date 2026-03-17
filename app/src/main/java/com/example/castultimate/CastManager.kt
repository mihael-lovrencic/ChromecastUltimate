package com.example.castultimate

import android.content.Context
import android.util.Log
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastDevice
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener

object CastManager : SessionManagerListener<CastSession> {

    private const val TAG = "CastManager"
    private const val CAST_APP_ID = "CC1AD845"

    private var castContext: CastContext? = null
    private var sessionManager: SessionManager? = null
    private var currentSession: CastSession? = null
    private var discoveryListener: DiscoveryListener? = null
    private var sessionListener: SessionListener? = null
    private var contextRef: Context? = null
    private var mediaRouter: MediaRouter? = null
    private var routeSelector: MediaRouteSelector? = null
    private val knownRoutes = mutableSetOf<String>()

    private val routerCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
            maybeNotifyRouteAdded(route)
        }

        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
            maybeNotifyRouteAdded(route)
        }

        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
            val deviceName = getDeviceName(route) ?: return
            if (knownRoutes.remove(deviceName)) {
                discoveryListener?.onDeviceRemoved(deviceName)
            }
        }
    }

    interface DiscoveryListener {
        fun onDeviceDiscovered(deviceName: String)
        fun onDeviceRemoved(deviceName: String)
        fun onDiscoveryStarted()
        fun onDiscoveryStopped()
    }

    interface SessionListener {
        fun onSessionStarted(session: CastSession)
        fun onSessionEnded(error: String?)
        fun onConnectivityChanged(connected: Boolean)
    }

    fun initialize(context: Context) {
        contextRef = context
        try {
            castContext = CastContext.getSharedInstance(context)
            sessionManager = castContext?.sessionManager
            sessionManager?.addSessionManagerListener(this, CastSession::class.java)
            mediaRouter = MediaRouter.getInstance(context)
            routeSelector = MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(CAST_APP_ID))
                .build()
            Log.d(TAG, "CastManager initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize CastContext", e)
        }
    }

    fun setDiscoveryListener(listener: DiscoveryListener?) {
        this.discoveryListener = listener
    }

    fun setSessionListener(listener: SessionListener?) {
        this.sessionListener = listener
    }

    fun startDiscovery() {
        discoveryListener?.onDiscoveryStarted()
        val router = mediaRouter
        val selector = routeSelector
        if (router != null && selector != null) {
            router.addCallback(
                selector,
                routerCallback,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY or MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN
            )
            Log.d(TAG, "MediaRouter discovery started")
        } else {
            Log.w(TAG, "MediaRouter not initialized for discovery")
        }
        Log.d(TAG, "Discovery started")
    }

    fun stopDiscovery() {
        discoveryListener?.onDiscoveryStopped()
        mediaRouter?.removeCallback(routerCallback)
        knownRoutes.clear()
        Log.d(TAG, "Discovery stopped")
    }

    @Suppress("DEPRECATION")
    fun castVideo(url: String, title: String = "Video", description: String = "") {
        if (currentSession == null) {
            Log.w(TAG, "No active session to cast video")
            return
        }
        
        val session = currentSession ?: return
        val remoteMediaClient = session.remoteMediaClient
        if (remoteMediaClient != null) {
            val mediaInfo = com.google.android.gms.cast.MediaInfo.Builder(url)
                .setStreamType(com.google.android.gms.cast.MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType("video/mp4")
                .setMetadata(com.google.android.gms.cast.MediaMetadata(com.google.android.gms.cast.MediaMetadata.MEDIA_TYPE_MOVIE).apply {
                    putString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE, title)
                    putString(com.google.android.gms.cast.MediaMetadata.KEY_SUBTITLE, description)
                })
                .build()

            remoteMediaClient.load(mediaInfo, true, 0)
            Log.d(TAG, "Casting video: $url")
        }
    }

    fun castVideoStream(url: String, title: String = "Stream") {
        castVideo(url, title)
    }

    @Suppress("DEPRECATION")
    fun control(action: String, position: Long = 0) {
        val session = currentSession ?: return
        val remoteMediaClient = session.remoteMediaClient
        when (action.lowercase()) {
            "play" -> remoteMediaClient?.play()
            "pause" -> remoteMediaClient?.pause()
            "stop" -> remoteMediaClient?.stop()
            "seek" -> if (position > 0) remoteMediaClient?.seek(position)
        }
        Log.d(TAG, "Control action: $action")
    }

    fun setVolume(volume: Float) {
        currentSession?.setVolume(volume.toDouble())
    }

    fun getVolume(): Double = currentSession?.volume ?: 0.0

    fun isConnected(): Boolean = currentSession != null

    fun endSession() {
        sessionManager?.endCurrentSession(true)
        currentSession = null
    }

    override fun onSessionStarting(session: CastSession) {
        Log.d(TAG, "Session starting")
    }

    override fun onSessionStarted(session: CastSession, sessionId: String) {
        Log.d(TAG, "Session started: $sessionId")
        currentSession = session
        sessionListener?.onSessionStarted(session)
    }

    override fun onSessionResuming(session: CastSession, sessionId: String) {
        Log.d(TAG, "Session resuming: $sessionId")
    }

    override fun onSessionResumed(session: CastSession, wasActive: Boolean) {
        Log.d(TAG, "Session resumed, wasActive: $wasActive")
        currentSession = session
        sessionListener?.onConnectivityChanged(true)
    }

    override fun onSessionSuspended(session: CastSession, reason: Int) {
        Log.d(TAG, "Session suspended, reason: $reason")
        sessionListener?.onConnectivityChanged(false)
    }

    override fun onSessionEnding(session: CastSession) {
        Log.d(TAG, "Session ending")
    }

    override fun onSessionEnded(session: CastSession, error: Int) {
        Log.d(TAG, "Session ended, error: $error")
        currentSession = null
        sessionListener?.onSessionEnded("Error code: $error")
    }

    override fun onSessionStartFailed(session: CastSession, error: Int) {
        Log.e(TAG, "Session start failed, error: $error")
        sessionListener?.onSessionEnded("Error code: $error")
    }

    override fun onSessionResumeFailed(session: CastSession, error: Int) {
        Log.e(TAG, "Session resume failed, error: $error")
    }

    fun release() {
        sessionManager?.removeSessionManagerListener(this, CastSession::class.java)
        mediaRouter?.removeCallback(routerCallback)
        castContext = null
        sessionManager = null
        currentSession = null
        contextRef = null
        mediaRouter = null
        routeSelector = null
        knownRoutes.clear()
    }

    private fun maybeNotifyRouteAdded(route: MediaRouter.RouteInfo) {
        val deviceName = getDeviceName(route) ?: return
        if (knownRoutes.add(deviceName)) {
            discoveryListener?.onDeviceDiscovered(deviceName)
        }
    }

    private fun getDeviceName(route: MediaRouter.RouteInfo): String? {
        val extras = route.extras ?: return null
        val device = CastDevice.getFromBundle(extras) ?: return null
        return device.friendlyName ?: route.name
    }
}
