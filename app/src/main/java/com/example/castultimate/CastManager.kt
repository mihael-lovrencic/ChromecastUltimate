package com.example.castultimate

import android.content.Context
import android.util.Log
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient

class CastManager : SessionManagerListener<CastSession> {

    private companion object {
        private const val TAG = "CastManager"
        private const val CAST_APP_ID = "CC1AD845"

        @Volatile
        private var instance: CastManager? = null

        fun getInstance(): CastManager = instance ?: synchronized(this) {
            instance ?: CastManager().also { instance = it }
        }
    }

    private var castContext: CastContext? = null
    private var sessionManager: SessionManager? = null
    private var currentSession: CastSession? = null
    private var discoveryListener: DiscoveryListener? = null
    private var sessionListener: SessionListener? = null

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
        try {
            castContext = CastContext.getSharedInstance(context)
            sessionManager = castContext?.sessionManager
            sessionManager?.addSessionManagerListener(this, CastSession::class.java)
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
        Log.d(TAG, "Discovery started")
    }

    fun stopDiscovery() {
        discoveryListener?.onDiscoveryStopped()
        Log.d(TAG, "Discovery stopped")
    }

    fun castVideo(url: String, title: String = "Video", description: String = "") {
        currentSession?.let { session ->
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
        } ?: run {
            Log.w(TAG, "No active session to cast video")
        }
    }

    fun castVideoStream(url: String, title: String = "Stream") {
        castVideo(url, title)
    }

    fun control(action: String, position: Long = 0) {
        currentSession?.let { session ->
            val remoteMediaClient = session.remoteMediaClient
            when (action.lowercase()) {
                "play" -> remoteMediaClient?.play()
                "pause" -> remoteMediaClient?.pause()
                "stop" -> remoteMediaClient?.stop()
                "seek" -> if (position > 0) remoteMediaClient?.seek(position)
            }
            Log.d(TAG, "Control action: $action")
        }
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

    override fun onSessionStarted(session: CastSession, sessionId: String) {
        Log.d(TAG, "Session started: $sessionId")
        currentSession = session
        sessionListener?.onSessionStarted(session)
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

    override fun onSessionEnded(session: CastSession, error: Int) {
        Log.d(TAG, "Session ended, error: $error")
        currentSession = null
        sessionListener?.onSessionEnded("Error code: $error")
    }

    override fun onSessionStartFailed(session: CastSession, error: Int) {
        Log.e(TAG, "Session start failed, error: $error")
        sessionListener?.onSessionEnded("Error code: $error")
    }

    fun release() {
        sessionManager?.removeSessionManagerListener(this, CastSession::class.java)
        castContext = null
        sessionManager = null
        currentSession = null
    }
}
