package com.example.castultimate

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.HttpURLConnection

object AppUpdater {

    private const val GITHUB_API_URL = "https://api.github.com/repos/mihael-lovrencic/ChromecastUltimate/releases/latest"
    private const val GITHUB_RELEASES_URL = "https://github.com/mihael-lovrencic/ChromecastUltimate/releases"

    private var updateListener: UpdateListener? = null

    private data class InstalledVersion(val name: String, val code: Long)

    interface UpdateListener {
        fun onUpdateAvailable(version: String, downloadUrl: String, releaseNotes: String?)
        fun onUpdateNotAvailable(currentVersion: String)
        fun onUpdateError(error: String)
    }

    fun setUpdateListener(listener: UpdateListener?) {
        updateListener = listener
    }

    fun getInstalledVersionDisplay(context: Context): String {
        val installed = getInstalledVersion(context)
        return formatInstalledVersion(installed)
    }

    fun checkForUpdate(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val installed = getInstalledVersion(context)
                
                val url = URL(GITHUB_API_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.setRequestProperty("User-Agent", "ChromecastUltimate")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    
                    val latestVersion = json.optString("tag_name", "").removePrefix("v")
                    val downloadUrl = getApkDownloadUrl(json)
                    val releaseNotes = json.optString("body", "")
                    val latestVersionCode = latestVersion.toLongOrNull()
                    
                    withContext(Dispatchers.Main) {
                        val isUpdateAvailable = if (latestVersionCode != null && installed.code > 0) {
                            latestVersionCode > installed.code
                        } else {
                            Versioning.compareVersions(latestVersion, installed.name) > 0
                        }

                        if (isUpdateAvailable) {
                            updateListener?.onUpdateAvailable(latestVersion, downloadUrl, releaseNotes)
                        } else {
                            updateListener?.onUpdateNotAvailable(formatInstalledVersion(installed))
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        updateListener?.onUpdateError("Failed to check for updates: $responseCode")
                    }
                }
                
                connection.disconnect()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateListener?.onUpdateError("Error checking for updates: ${e.message}")
                }
            }
        }
    }

    private fun getInstalledVersion(context: Context): InstalledVersion {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            val name = packageInfo.versionName ?: "1.0.0"
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            InstalledVersion(name, code)
        } catch (e: Exception) {
            InstalledVersion("1.0.0", 0L)
        }
    }

    private fun formatInstalledVersion(installed: InstalledVersion): String {
        return if (installed.code > 0) {
            "${installed.name} (${installed.code})"
        } else {
            installed.name
        }
    }

    private fun getApkDownloadUrl(json: JSONObject): String {
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.endsWith(".apk")) {
                    return asset.optString("browser_download_url", "")
                }
            }
        }
        return GITHUB_RELEASES_URL
    }

}
