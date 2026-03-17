package com.example.castultimate

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.util.UUID

class CastServer(
    port: Int = 5000,
    private val mirrorController: MirrorController? = null
) : NanoHTTPD(port) {

    private val TAG = "CastServer"
    private val ALLOWED_ACTIONS = setOf("play", "pause", "stop", "seek", "toggleplaypause")
    private val subtitles = mutableMapOf<String, SubtitleEntry>()
    private var latestSubtitleId: String? = null
    
    companion object {
        private const val MIME_JSON = "application/json"
    }

    interface MirrorController {
        fun requestMirror(url: String?): Boolean
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        Log.d(TAG, "$method $uri")

        return try {
            val response = when {
                uri == "/devices" && method == Method.GET -> handleGetDevices()
                uri == "/cast" && method == Method.POST -> handleCast(session)
                uri == "/control" && method == Method.POST -> handleControl(session)
                uri == "/seek" && method == Method.POST -> handleSeek(session)
                uri == "/volume" && method == Method.POST -> handleVolume(session)
                uri == "/subtitle" && method == Method.POST -> handleSubtitle(session)
                uri.startsWith("/subtitle/") && method == Method.GET -> handleSubtitleGet(uri)
                uri == "/mirror" && method == Method.POST -> handleMirror(session)
                uri == "/status" && (method == Method.GET || method == Method.HEAD) -> handleStatus(method == Method.HEAD)
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_JSON, "{\"error\":\"Not found\"}")
            }
            addSecurityHeaders(response)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error", e)
            addSecurityHeaders(newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_JSON, "{\"error\":\"Internal server error\"}"))
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
            CastManager.ensureDiscovery()
            // Give discovery a brief window to populate routes.
            var deviceList = CastManager.getDevices()
            val deadline = System.currentTimeMillis() + 2000
            while (deviceList.isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(200)
                deviceList = CastManager.getDevices()
            }
            val devices = JSONArray()
            deviceList.forEach { device ->
                devices.put(JSONObject().apply {
                    put("address", device.id)
                    put("name", device.name)
                })
            }

            newFixedLengthResponse(
                Response.Status.OK,
                MIME_JSON,
                devices.toString()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting devices", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_JSON, "{\"error\":\"Internal error\"}")
        }
    }

    private fun handleCast(session: IHTTPSession): Response {
        return try {
            val bodyJson = parseJsonBody(session)
            val videoUrl = extractString(session, bodyJson, "url", "videoUrl")
            val device = extractString(session, bodyJson, "device")
            
            if (videoUrl.isNullOrBlank()) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_JSON, "{\"error\":\"Invalid or missing URL\"}")
            }

            // Validate URL format
            if (!isValidUrl(videoUrl)) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_JSON, "{\"error\":\"Invalid URL format\"}")
            }

            if (!device.isNullOrBlank()) {
                val selected = CastManager.selectDevice(device)
                if (!selected) {
                    return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_JSON, "{\"error\":\"Device not found\"}")
                }
            }

            if (!CastManager.isConnected()) {
                return newFixedLengthResponse(Response.Status.CONFLICT, MIME_JSON, "{\"error\":\"No active Chromecast session\"}")
            }

            val subtitleId = latestSubtitleId
            val subtitleEntry = subtitleId?.let { subtitles[it] }
            if (subtitleId != null && subtitleEntry != null) {
                val subtitleUrl = "${getBaseUrl(session)}/subtitle/$subtitleId"
                CastManager.castVideoWithSubtitle(videoUrl, subtitleUrl, subtitleEntry.language, subtitleEntry.label)
            } else {
                CastManager.castVideo(videoUrl)
            }
            Log.d(TAG, "Casting: $videoUrl")
            
            val result = JSONObject().put("success", true).put("message", "Casting started")
            newFixedLengthResponse(Response.Status.OK, MIME_JSON, result.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error casting", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_JSON, "{\"error\":\"Failed to cast\"}")
        }
    }

    private fun extractString(session: IHTTPSession, bodyJson: JSONObject?, vararg keys: String): String? {
        for (key in keys) {
            val paramValue = session.parameters[key]?.firstOrNull()
            if (!paramValue.isNullOrBlank()) return paramValue
            val jsonValue = bodyJson?.optString(key, "")
            if (!jsonValue.isNullOrBlank()) return jsonValue
        }
        return null
    }

    private fun extractLong(session: IHTTPSession, bodyJson: JSONObject?, key: String): Long? {
        val paramValue = session.parameters[key]?.firstOrNull()?.toLongOrNull()
        if (paramValue != null) return paramValue
        return if (bodyJson != null && bodyJson.has(key)) bodyJson.optLong(key) else null
    }

    private fun extractDouble(session: IHTTPSession, bodyJson: JSONObject?, key: String): Double? {
        val paramValue = session.parameters[key]?.firstOrNull()?.toDoubleOrNull()
        if (paramValue != null) return paramValue
        return if (bodyJson != null && bodyJson.has(key)) bodyJson.optDouble(key) else null
    }

    private fun parseJsonBody(session: IHTTPSession): JSONObject? {
        return try {
            val files = HashMap<String, String>()
            session.parseBody(files)
            val body = files["postData"] ?: ""
            if (body.isNotBlank()) JSONObject(body) else null
        } catch (_: Exception) {
            null
        }
    }

    private fun getBaseUrl(session: IHTTPSession): String {
        val host = session.headers["host"] ?: "localhost:5000"
        return "http://$host"
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
            val bodyJson = parseJsonBody(session)
            val action = extractString(session, bodyJson, "action")

            if (action.isNullOrBlank()) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_JSON, "{\"error\":\"Missing action\"}")
            }

            // Validate action is allowed
            if (!ALLOWED_ACTIONS.contains(action.lowercase())) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_JSON, "{\"error\":\"Invalid action\"}")
            }

            if (!CastManager.isConnected()) {
                return newFixedLengthResponse(Response.Status.CONFLICT, MIME_JSON, "{\"error\":\"No active Chromecast session\"}")
            }

            CastManager.control(action)
            Log.d(TAG, "Control: $action")
            
            val result = JSONObject().put("success", true)
            newFixedLengthResponse(Response.Status.OK, MIME_JSON, result.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error controlling", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_JSON, "{\"error\":\"Control failed\"}")
        }
    }

    private fun handleSeek(session: IHTTPSession): Response {
        return try {
            val bodyJson = parseJsonBody(session)
            val rawValue = extractLong(session, bodyJson, "value") ?: 0L

            if (rawValue < 0) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_JSON, "{\"error\":\"Invalid seek position\"}")
            }

            if (!CastManager.isConnected()) {
                return newFixedLengthResponse(Response.Status.CONFLICT, MIME_JSON, "{\"error\":\"No active Chromecast session\"}")
            }

            val duration = CastManager.getDurationMs()
            val seekValue = if (rawValue in 0..100 && duration > 0) {
                (duration * rawValue) / 100
            } else {
                rawValue
            }

            CastManager.control("seek", seekValue)
            Log.d(TAG, "Seek: $seekValue")
            
            val result = JSONObject().put("success", true)
            newFixedLengthResponse(Response.Status.OK, MIME_JSON, result.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_JSON, "{\"error\":\"Seek failed\"}")
        }
    }

    private fun handleVolume(session: IHTTPSession): Response {
        return try {
            val bodyJson = parseJsonBody(session)
            val rawValue = extractDouble(session, bodyJson, "value") ?: 0.5

            if (!CastManager.isConnected()) {
                return newFixedLengthResponse(Response.Status.CONFLICT, MIME_JSON, "{\"error\":\"No active Chromecast session\"}")
            }

            val normalized = if (rawValue > 1.0) rawValue / 100.0 else rawValue
            val volume = normalized.coerceIn(0.0, 1.0).toFloat()

            CastManager.setVolume(volume)
            Log.d(TAG, "Volume: $volume")
            
            val result = JSONObject().put("success", true)
            newFixedLengthResponse(Response.Status.OK, MIME_JSON, result.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error setting volume", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_JSON, "{\"error\":\"Volume control failed\"}")
        }
    }

    private fun handleSubtitle(session: IHTTPSession): Response {
        return try {
            val bodyJson = parseJsonBody(session)
            val content = extractString(session, bodyJson, "content")
            val filename = extractString(session, bodyJson, "filename", "name")
            val format = extractString(session, bodyJson, "format")

            if (content.isNullOrBlank()) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_JSON, "{\"error\":\"Missing subtitle content\"}")
            }

            val normalized = normalizeSubtitle(content, filename, format)
            val id = UUID.randomUUID().toString()
            subtitles.clear()
            subtitles[id] = normalized
            latestSubtitleId = id

            val subtitleUrl = "${getBaseUrl(session)}/subtitle/$id"
            val applied = if (CastManager.isConnected()) {
                CastManager.applySubtitle(subtitleUrl, normalized.language, normalized.label)
            } else {
                false
            }

            val result = JSONObject()
                .put("success", true)
                .put("message", if (applied) "Subtitle applied" else "Subtitle queued for next cast")
                .put("url", subtitleUrl)
            newFixedLengthResponse(Response.Status.OK, MIME_JSON, result.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error loading subtitle", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_JSON, "{\"error\":\"Subtitle failed\"}")
        }
    }

    private fun handleSubtitleGet(uri: String): Response {
        val id = uri.removePrefix("/subtitle/").trim()
        val entry = subtitles[id] ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_JSON, "{\"error\":\"Subtitle not found\"}")
        return newFixedLengthResponse(Response.Status.OK, entry.mimeType, entry.content)
    }

    private fun handleMirror(session: IHTTPSession): Response {
        return try {
            val bodyJson = parseJsonBody(session)
            val url = extractString(session, bodyJson, "url")
            val started = mirrorController?.requestMirror(url) ?: false
            if (!started) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_JSON, "{\"error\":\"Mirror requires user approval in app\"}")
            }
            val result = JSONObject().put("success", true).put("message", "Mirror requested")
            newFixedLengthResponse(Response.Status.OK, MIME_JSON, result.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error starting mirror", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_JSON, "{\"error\":\"Mirror failed\"}")
        }
    }

    private fun handleStatus(headOnly: Boolean): Response {
        return try {
            val status = JSONObject().apply {
                put("connected", CastManager.isConnected())
                put("volume", CastManager.getVolume())
                put("positionMs", CastManager.getPositionMs())
                put("durationMs", CastManager.getDurationMs())
            }
            val body = if (headOnly) "" else status.toString()
            newFixedLengthResponse(Response.Status.OK, MIME_JSON, body)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting status", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_JSON, "{\"error\":\"Status failed\"}")
        }
    }

    private data class SubtitleEntry(
        val content: String,
        val mimeType: String,
        val language: String,
        val label: String
    )

    private fun normalizeSubtitle(content: String, filename: String?, format: String?): SubtitleEntry {
        val trimmed = content.trimStart()
        val lowerName = filename?.lowercase() ?: ""
        val lowerFormat = format?.lowercase() ?: ""

        return if (trimmed.startsWith("WEBVTT")) {
            SubtitleEntry(content = content, mimeType = "text/vtt", language = "en", label = "Subtitles")
        } else if (lowerFormat == "vtt" || lowerName.endsWith(".vtt")) {
            SubtitleEntry(content = "WEBVTT\n\n$content".trim(), mimeType = "text/vtt", language = "en", label = "Subtitles")
        } else {
            val vtt = srtToVtt(content)
            SubtitleEntry(content = vtt, mimeType = "text/vtt", language = "en", label = "Subtitles")
        }
    }

    private fun srtToVtt(srt: String): String {
        val lines = srt.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        val out = StringBuilder()
        out.append("WEBVTT\n\n")
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                out.append("\n")
                continue
            }
            if (trimmed.matches(Regex("\\d+"))) {
                continue
            }
            if (trimmed.contains("-->")) {
                out.append(trimmed.replace(",", "."))
            } else {
                out.append(line)
            }
            out.append("\n")
        }
        return out.toString()
    }
}
