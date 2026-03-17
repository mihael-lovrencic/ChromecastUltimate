package com.example.castultimate

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

class CastServer(port: Int = 5000) : NanoHTTPD(port) {

    private val TAG = "CastServer"
    private val ALLOWED_ACTIONS = setOf("play", "pause", "stop", "seek", "toggleplaypause")
    
    companion object {
        private const val MIME_JSON = "application/json"
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        Log.d(TAG, "$method $uri")

        // Add security headers
        return try {
            when {
                uri == "/devices" && method == Method.GET -> handleGetDevices()
                uri == "/cast" && method == Method.POST -> handleCast(session)
                uri == "/control" && method == Method.POST -> handleControl(session)
                uri == "/seek" && method == Method.POST -> handleSeek(session)
                uri == "/volume" && method == Method.POST -> handleVolume(session)
                uri == "/subtitle" && method == Method.POST -> handleSubtitle(session)
                uri == "/mirror" && method == Method.POST -> handleMirror(session)
                uri == "/status" && method == Method.GET -> handleStatus()
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_JSON, "{\"error\":\"Not found\"}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_JSON, "{\"error\":\"Internal server error\"}")
        }
    }

    private fun addSecurityHeaders(response: Response): Response {
        response.addHeader("X-Content-Type-Options", "nosniff")
        response.addHeader("X-Frame-Options", "DENY")
        response.addHeader("X-XSS-Protection", "1; mode=block")
        return response
    }

    private fun handleGetDevices(): Response {
        return try {
            val devices = JSONArray()
            val device = JSONObject().apply {
                put("address", "chromecast")
                put("name", "Chromecast Device")
            }
            devices.put(device)

            addSecurityHeaders(newFixedLengthResponse(
                Response.Status.OK,
                MIME_JSON,
                devices.toString()
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Error getting devices", e)
            addSecurityHeaders(newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_JSON, "{\"error\":\"Internal error\"}"))
        }
    }

    private fun handleCast(session: IHTTPSession): Response {
        return try {
            val videoUrl = extractVideoUrl(session)
            
            if (videoUrl.isNullOrBlank()) {
                return addSecurityHeaders(newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_JSON, "{\"error\":\"Invalid or missing URL\"}"))
            }

            // Validate URL format
            if (!isValidUrl(videoUrl)) {
                return addSecurityHeaders(newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_JSON, "{\"error\":\"Invalid URL format\"}"))
            }

            CastManager.castVideo(videoUrl)
            Log.d(TAG, "Casting: $videoUrl")
            
            val result = JSONObject().put("success", true).put("message", "Casting started")
            addSecurityHeaders(newFixedLengthResponse(Response.Status.OK, MIME_JSON, result.toString()))
        } catch (e: Exception) {
            Log.e(TAG, "Error casting", e)
            addSecurityHeaders(newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_JSON, "{\"error\":\"Failed to cast\"}"))
        }
    }

    private fun extractVideoUrl(session: IHTTPSession): String? {
        val params = session.parameters
        val url = params["url"]?.firstOrNull() ?: params["videoUrl"]?.firstOrNull()

        if (!url.isNullOrBlank()) {
            return url
        }

        // Try to parse from body
        return try {
            val files = HashMap<String, String>()
            session.parseBody(files)
            val body = files["postData"] ?: ""
            if (body.isNotBlank()) {
                val json = JSONObject(body)
                json.optString("url", json.optString("videoUrl", "")).takeIf { it.isNotBlank() }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun isValidUrl(url: String): Boolean {
        return try {
            val parsedUrl = URL(url)
            val protocol = parsedUrl.protocol
            protocol == "http" || protocol == "https"
        } catch (e: Exception) {
            false
        }
    }

    private fun handleControl(session: IHTTPSession): Response {
        return try {
            val params = session.parameters
            val action = params["action"]?.firstOrNull()

            if (action.isNullOrBlank()) {
                return addSecurityHeaders(newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_JSON, "{\"error\":\"Missing action\"}"))
            }

            // Validate action is allowed
            if (!ALLOWED_ACTIONS.contains(action.lowercase())) {
                return addSecurityHeaders(newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_JSON, "{\"error\":\"Invalid action\"}"))
            }

            CastManager.control(action)
            Log.d(TAG, "Control: $action")
            
            val result = JSONObject().put("success", true)
            addSecurityHeaders(newFixedLengthResponse(Response.Status.OK, MIME_JSON, result.toString()))
        } catch (e: Exception) {
            Log.e(TAG, "Error controlling", e)
            addSecurityHeaders(newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_JSON, "{\"error\":\"Control failed\"}"))
        }
    }

    private fun handleSeek(session: IHTTPSession): Response {
        return try {
            val params = session.parameters
            val value = params["value"]?.firstOrNull()?.toLongOrNull() ?: 0L

            if (value < 0) {
                return addSecurityHeaders(newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_JSON, "{\"error\":\"Invalid seek position\"}"))
            }

            CastManager.control("seek", value)
            Log.d(TAG, "Seek: $value")
            
            val result = JSONObject().put("success", true)
            addSecurityHeaders(newFixedLengthResponse(Response.Status.OK, MIME_JSON, result.toString()))
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking", e)
            addSecurityHeaders(newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_JSON, "{\"error\":\"Seek failed\"}"))
        }
    }

    private fun handleVolume(session: IHTTPSession): Response {
        return try {
            val params = session.parameters
            val value = params["value"]?.firstOrNull()?.toFloatOrNull() ?: 0.5f

            // Clamp volume to valid range
            val volume = value.coerceIn(0f, 1f)

            CastManager.setVolume(volume)
            Log.d(TAG, "Volume: $volume")
            
            val result = JSONObject().put("success", true)
            addSecurityHeaders(newFixedLengthResponse(Response.Status.OK, MIME_JSON, result.toString()))
        } catch (e: Exception) {
            Log.e(TAG, "Error setting volume", e)
            addSecurityHeaders(newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_JSON, "{\"error\":\"Volume control failed\"}"))
        }
    }

    private fun handleSubtitle(session: IHTTPSession): Response {
        return try {
            val result = JSONObject().put("success", true).put("message", "Subtitle loaded")
            addSecurityHeaders(newFixedLengthResponse(Response.Status.OK, MIME_JSON, result.toString()))
        } catch (e: Exception) {
            Log.e(TAG, "Error loading subtitle", e)
            addSecurityHeaders(newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_JSON, "{\"error\":\"Subtitle failed\"}"))
        }
    }

    private fun handleMirror(session: IHTTPSession): Response {
        return try {
            val result = JSONObject().put("success", true).put("message", "Mirror started")
            addSecurityHeaders(newFixedLengthResponse(Response.Status.OK, MIME_JSON, result.toString()))
        } catch (e: Exception) {
            Log.e(TAG, "Error starting mirror", e)
            addSecurityHeaders(newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_JSON, "{\"error\":\"Mirror failed\"}"))
        }
    }

    private fun handleStatus(): Response {
        return try {
            val status = JSONObject().apply {
                put("connected", CastManager.isConnected())
                put("volume", CastManager.getVolume())
            }
            addSecurityHeaders(newFixedLengthResponse(Response.Status.OK, MIME_JSON, status.toString()))
        } catch (e: Exception) {
            Log.e(TAG, "Error getting status", e)
            addSecurityHeaders(newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_JSON, "{\"error\":\"Status failed\"}"))
        }
    }
}
