package com.example.castultimate

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity(), 
    CastManager.DiscoveryListener, 
    CastManager.SessionListener, 
    MediaProjectionManager.ProjectionCallback,
    AppUpdater.UpdateListener {

    private lateinit var castButton: MaterialButton
    private lateinit var mirrorButton: MaterialButton
    private lateinit var serverButton: MaterialButton
    private lateinit var updateButton: MaterialButton
    private lateinit var statusText: TextView
    private lateinit var serverStatusText: TextView
    private lateinit var serverStatusIndicator: View
    private lateinit var toolbar: MaterialToolbar
    private lateinit var versionText: TextView

    private var server: CastServer? = null
    private var serverRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        castButton = findViewById(R.id.castButton)
        mirrorButton = findViewById(R.id.mirrorButton)
        serverButton = findViewById(R.id.serverButton)
        updateButton = findViewById(R.id.updateButton)
        statusText = findViewById(R.id.statusText)
        serverStatusText = findViewById(R.id.serverStatusText)
        serverStatusIndicator = findViewById(R.id.serverStatusIndicator)
        versionText = findViewById(R.id.versionText)

        val installedVersion = AppUpdater.getInstalledVersionDisplay(this)
        versionText.text = "v$installedVersion"

        CastManager.initialize(this)
        CastManager.setDiscoveryListener(this)
        CastManager.setSessionListener(this)

        AppUpdater.setUpdateListener(this)

        serverButton.setOnClickListener {
            if (serverRunning) {
                stopServer()
            } else {
                startServer()
            }
        }

        castButton.setOnClickListener {
            CastManager.startDiscovery()
            updateStatus("Searching for Chromecast devices...")
        }

        mirrorButton.setOnClickListener {
            MediaProjectionManager.setProjectionCallback(this)
            MediaProjectionManager.startScreenCapture(this)
        }

        updateButton.setOnClickListener {
            updateStatus("Checking for updates...")
            AppUpdater.checkForUpdate(this)
        }

        AppUpdater.checkForUpdate(this)
    }

    private fun startServer() {
        try {
            server = CastServer(5000, object : CastServer.MirrorController {
                override fun requestMirror(url: String?): Boolean {
                    if (MediaProjectionManager.isCapturing()) {
                        return true
                    }
                    runOnUiThread {
                        MediaProjectionManager.setProjectionCallback(this@MainActivity)
                        MediaProjectionManager.startScreenCapture(this@MainActivity)
                    }
                    return true
                }
            })
            server?.start()
            serverRunning = true
            serverButton.text = "Stop Server"
            serverStatusText.text = "Server: Running on port 5000"
            setServerStatus(true)
            updateStatus("Server started - Firefox extension can connect")
        } catch (e: Exception) {
            updateStatus("Server error: ${e.message}")
        }
    }

    private fun stopServer() {
        try {
            server?.stop()
            server = null
            serverRunning = false
            serverButton.text = "Start Server"
            serverStatusText.text = "Server: Stopped"
            setServerStatus(false)
            updateStatus("Server stopped")
        } catch (e: Exception) {
            updateStatus("Server error: ${e.message}")
        }
    }

    private fun setServerStatus(isRunning: Boolean) {
        val drawable = serverStatusIndicator.background as? GradientDrawable
        drawable?.setColor(
            if (isRunning) getColor(R.color.status_online) 
            else getColor(R.color.status_offline)
        )
    }

    private fun updateStatus(message: String) {
        statusText.text = message
    }

    override fun onUpdateAvailable(version: String, downloadUrl: String, releaseNotes: String?) {
        updateButton.text = "Update Available: v$version"
        updateButton.isEnabled = true
        
        AlertDialog.Builder(this)
            .setTitle("Update Available")
            .setMessage("Version $version is available.\n\n${releaseNotes ?: "Tap 'Update' to download and install."}")
            .setPositiveButton("Update") { _, _ ->
                updateStatus("Downloading update...")
                downloadAndInstallApk(downloadUrl)
            }
            .setNegativeButton("Later", null)
            .show()
    }

    override fun onUpdateNotAvailable(currentVersion: String) {
        updateStatus("App is up to date (v$currentVersion)")
    }

    override fun onUpdateError(error: String) {
        updateStatus("Update check failed")
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
    }

    private fun downloadAndInstallApk(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = android.net.Uri.parse(url)
            startActivity(intent)
        } catch (e: Exception) {
            updateStatus("Failed to open download: ${e.message}")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == MediaProjectionManager.REQUEST_CODE_SCREEN_CAPTURE) {
            MediaProjectionManager.onActivityResult(this, requestCode, resultCode, data)
        }
    }

    override fun onDeviceDiscovered(deviceName: String) {
        runOnUiThread {
            updateStatus("Found: $deviceName")
        }
    }

    override fun onDeviceRemoved(deviceName: String) {
        runOnUiThread {
            updateStatus("Device removed: $deviceName")
        }
    }

    override fun onDiscoveryStarted() {
        runOnUiThread {
            updateStatus("Discovery started...")
        }
    }

    override fun onDiscoveryStopped() {
        runOnUiThread {
            updateStatus("Discovery stopped")
        }
    }

    override fun onSessionStarted(session: com.google.android.gms.cast.framework.CastSession) {
        runOnUiThread {
            updateStatus("Connected to Chromecast!")
        }
    }

    override fun onSessionEnded(error: String?) {
        runOnUiThread {
            updateStatus("Disconnected: $error")
        }
    }

    override fun onConnectivityChanged(connected: Boolean) {
        runOnUiThread {
            updateStatus(if (connected) "Connected" else "Disconnected")
        }
    }

    override fun onProjectionStarted() {
        runOnUiThread {
            updateStatus("Screen mirroring started")
        }
    }

    override fun onProjectionStopped() {
        runOnUiThread {
            updateStatus("Screen mirroring stopped")
        }
    }

    override fun onError(error: String) {
        runOnUiThread {
            updateStatus("Error: $error")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serverRunning) {
            stopServer()
        }
        CastManager.release()
        if (MediaProjectionManager.isCapturing()) {
            MediaProjectionManager.stopCapture()
        }
    }
}
