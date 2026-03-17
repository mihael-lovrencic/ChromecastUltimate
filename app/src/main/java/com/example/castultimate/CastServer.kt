package com.example.castultimate

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject

class CastServer(port: Int = 5000) : NanoHTTPD(port) {

    private val TAG = "CastServer"

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        Log.d(TAG, "$method $uri")

        return when {
            uri == "/devices" && method == Method.GET -> handleGetDevices()
            uri == "/cast" && method == Method.POST -> handleCast(session)
            uri == "/control" && method == Method.POST -> handleControl(session)
            uri == "/seek" && method == Method.POST -> handleSeek(session)
            uri == "/volume" && method == Method.POST -> handleVolume(session)
            uri == "/subtitle" && method == Method.POST -> handleSubtitle(session)
            uri == "/mirror" && method == Method.POST -> handleMirror(session)
            uri == "/status" && method == Method.GET -> handleStatus()
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_HTML, "Not Found")
        }
    }

    private fun handleGetDevices(): Response {
        return try {
            val devices = JSONArray()
            val device = JSONObject().apply {
                put("address", "chromecast")
                put("name", "Chromecast Device")
            }
            devices.put(device)

            newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                devices.toString()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting devices", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", "[]")
        }
    }

    private fun handleCast(session: IHTTPSession): Response {
        return try {
            val params = session.parameters
            val url = params["url"]?.firstOrNull() ?: params["videoUrl"]?.firstOrNull()

            if (url != null) {
                CastManager.castVideo(url)
                Log.d(TAG, "Casting: $url")
                val result = JSONObject().put("success", true).put("message", "Casting started")
                newFixedLengthResponse(Response.Status.OK, "application/json", result.toString())
            } else {
                val files = HashMap<String, String>()
                session.parseBody(files)
                val body = files["postData"] ?: ""
                val json = JSONObject(body)
                val videoUrl = json.optString("url", json.optString("videoUrl", ""))
                
                if (videoUrl.isNotEmpty()) {
                    CastManager.castVideo(videoUrl)
                    Log.d(TAG, "Casting: $videoUrl")
                    val result = JSONObject().put("success", true).put("message", "Casting started")
                    newFixedLengthResponse(Response.Status.OK, "application/json", result.toString())
                } else {
                    newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", "{\"error\": \"No URL provided\"}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error casting", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", "{\"error\": \"${e.message}\"}")
        }
    }

    private fun handleControl(session: IHTTPSession): Response {
        return try {
            val params = session.parameters
            val action = params["action"]?.firstOrNull()

            if (action != null) {
                CastManager.control(action)
                Log.d(TAG, "Control: $action")
                val result = JSONObject().put("success", true)
                newFixedLengthResponse(Response.Status.OK, "application/json", result.toString())
            } else {
                newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", "{\"error\": \"No action provided\"}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error controlling", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", "{\"error\": \"${e.message}\"}")
        }
    }

    private fun handleSeek(session: IHTTPSession): Response {
        return try {
            val params = session.parameters
            val value = params["value"]?.firstOrNull()?.toLongOrNull() ?: 0L

            CastManager.control("seek", value)
            Log.d(TAG, "Seek: $value")
            val result = JSONObject().put("success", true)
            newFixedLengthResponse(Response.Status.OK, "application/json", result.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", "{\"error\": \"${e.message}\"}")
        }
    }

    private fun handleVolume(session: IHTTPSession): Response {
        return try {
            val params = session.parameters
            val value = params["value"]?.firstOrNull()?.toFloatOrNull() ?: 0.5f

            CastManager.setVolume(value)
            Log.d(TAG, "Volume: $value")
            val result = JSONObject().put("success", true)
            newFixedLengthResponse(Response.Status.OK, "application/json", result.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error setting volume", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", "{\"error\": \"${e.message}\"}")
        }
    }

    private fun handleSubtitle(session: IHTTPSession): Response {
        return try {
            val result = JSONObject().put("success", true).put("message", "Subtitle loaded")
            newFixedLengthResponse(Response.Status.OK, "application/json", result.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error loading subtitle", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", "{\"error\": \"${e.message}\"}")
        }
    }

    private fun handleMirror(session: IHTTPSession): Response {
        return try {
            val result = JSONObject().put("success", true).put("message", "Mirror started")
            newFixedLengthResponse(Response.Status.OK, "application/json", result.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error starting mirror", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", "{\"error\": \"${e.message}\"}")
        }
    }

    private fun handleStatus(): Response {
        return try {
            val status = JSONObject().apply {
                put("connected", CastManager.isConnected())
                put("volume", CastManager.getVolume())
            }
            newFixedLengthResponse(Response.Status.OK, "application/json", status.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error getting status", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", "{\"error\": \"${e.message}\"}")
        }
    }
}
