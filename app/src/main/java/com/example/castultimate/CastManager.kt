package com.example.castultimate

import android.content.Context
import android.util.Log
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastDevice
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadOptions
import com.google.android.gms.cast.MediaTrack
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
    data class DeviceInfo(val id: String, val name: String)

    private val knownRoutes = mutableMapOf<String, MediaRouter.RouteInfo>()
    private var discoveryActive = false

    private val routerCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
            maybeNotifyRouteAdded(route)
        }

        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
            maybeNotifyRouteAdded(route)
        }

        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
            val deviceName = getDeviceName(route) ?: route.name
            if (knownRoutes.remove(route.id) != null) {
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
        if (discoveryActive) return
        discoveryActive = true
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
        if (!discoveryActive) return
        discoveryActive = false
        discoveryListener?.onDiscoveryStopped()
        mediaRouter?.removeCallback(routerCallback)
        knownRoutes.clear()
        Log.d(TAG, "Discovery stopped")
    }

    fun ensureDiscovery() {
        if (!discoveryActive) {
            startDiscovery()
        }
    }

    fun getDevices(): List<DeviceInfo> {
        return knownRoutes.values.map { route ->
            val name = getDeviceName(route) ?: route.name
            DeviceInfo(route.id, name)
        }.sortedBy { it.name }
    }

    fun selectDevice(identifier: String): Boolean {
        val router = mediaRouter ?: return false
        val route = knownRoutes.values.firstOrNull { it.id == identifier }
            ?: knownRoutes.values.firstOrNull { (getDeviceName(it) ?: it.name).equals(identifier, ignoreCase = true) }
            ?: return false
        router.selectRoute(route)
        return true
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

    @Suppress("DEPRECATION")
    fun castVideoWithSubtitle(
        url: String,
        subtitleUrl: String,
        subtitleLanguage: String = "en",
        subtitleName: String = "Subtitles",
        title: String = "Video",
        description: String = ""
    ) {
        if (currentSession == null) {
            Log.w(TAG, "No active session to cast video")
            return
        }

        val session = currentSession ?: return
        val remoteMediaClient = session.remoteMediaClient
        if (remoteMediaClient != null) {
            val trackId = 1L
            val textTrack = MediaTrack.Builder(trackId, MediaTrack.TYPE_TEXT)
                .setContentId(subtitleUrl)
                .setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
                .setLanguage(subtitleLanguage)
                .setName(subtitleName)
                .build()

            val mediaInfo = MediaInfo.Builder(url)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType("video/mp4")
                .setMetadata(com.google.android.gms.cast.MediaMetadata(com.google.android.gms.cast.MediaMetadata.MEDIA_TYPE_MOVIE).apply {
                    putString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE, title)
                    putString(com.google.android.gms.cast.MediaMetadata.KEY_SUBTITLE, description)
                })
                .setMediaTracks(listOf(textTrack))
                .build()

            val options = MediaLoadOptions.Builder()
                .setAutoplay(true)
                .setPlayPosition(0)
                .setActiveTrackIds(longArrayOf(trackId))
                .build()

            remoteMediaClient.load(mediaInfo, options)
            Log.d(TAG, "Casting video with subtitles: $url")
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

    fun getPositionMs(): Long {
        val remote = currentSession?.remoteMediaClient ?: return 0L
        return remote.mediaStatus?.streamPosition ?: 0L
    }

    fun getDurationMs(): Long {
        val remote = currentSession?.remoteMediaClient ?: return 0L
        return remote.mediaStatus?.mediaInfo?.streamDuration ?: 0L
    }

    @Suppress("DEPRECATION")
    fun applySubtitle(subtitleUrl: String, language: String = "en", name: String = "Subtitles"): Boolean {
        val session = currentSession ?: return false
        val remoteMediaClient = session.remoteMediaClient ?: return false
        val status = remoteMediaClient.mediaStatus ?: return false
        val currentMedia = status.mediaInfo ?: return false

        val existingTracks = (currentMedia.mediaTracks as? List<MediaTrack>)
            ?.filter { it.type != MediaTrack.TYPE_TEXT }
            ?.toMutableList()
            ?: mutableListOf()

        val newTrackId = (existingTracks.map { it.id }.maxOrNull() ?: 0L) + 1
        val textTrack = MediaTrack.Builder(newTrackId, MediaTrack.TYPE_TEXT)
            .setContentId(subtitleUrl)
            .setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
            .setLanguage(language)
            .setName(name)
            .build()

        existingTracks.add(textTrack)

        val newMediaInfo = MediaInfo.Builder(currentMedia.contentId)
            .setStreamType(currentMedia.streamType)
            .setContentType(currentMedia.contentType)
            .setMetadata(currentMedia.metadata)
            .setMediaTracks(existingTracks)
            .build()

        remoteMediaClient.load(newMediaInfo, true, status.streamPosition)
        remoteMediaClient.setActiveMediaTracks(longArrayOf(newTrackId))
        return true
    }

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
        discoveryActive = false
    }

    private fun maybeNotifyRouteAdded(route: MediaRouter.RouteInfo) {
        val deviceName = getDeviceName(route) ?: route.name
        if (!knownRoutes.containsKey(route.id)) {
            knownRoutes[route.id] = route
            discoveryListener?.onDeviceDiscovered(deviceName)
        }
    }

    private fun getDeviceName(route: MediaRouter.RouteInfo): String? {
        val extras = route.extras ?: return null
        val device = CastDevice.getFromBundle(extras) ?: return null
        return device.friendlyName ?: route.name
    }
}
