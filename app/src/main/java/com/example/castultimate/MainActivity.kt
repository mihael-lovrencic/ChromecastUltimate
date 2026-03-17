package com.example.castultimate

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Build
import android.location.LocationManager
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.material.slider.Slider
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
    private lateinit var discoveryLabel: TextView
    private lateinit var discoverySlider: Slider
    private lateinit var deviceListText: TextView
    private val discoveredDevices = linkedSetOf<String>()
    private lateinit var prefs: SharedPreferences

    private val PREFS_NAME = "cast_ultimate"
    private val KEY_DISCOVERY_SECONDS = "discovery_seconds"
    private val REQUEST_DISCOVERY_PERMS = 1001

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
        discoveryLabel = findViewById(R.id.discoveryLabel)
        discoverySlider = findViewById(R.id.discoverySlider)
        deviceListText = findViewById(R.id.deviceListText)
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        val installedVersion = AppUpdater.getInstalledVersionDisplay(this)
        versionText.text = "v$installedVersion"

        CastManager.initialize(this)

        AppUpdater.setUpdateListener(this)

        serverButton.setOnClickListener {
            if (serverRunning) {
                stopServer()
            } else {
                startServer()
            }
        }

        castButton.setOnClickListener {
            if (!ensureDiscoveryPermissions()) {
                updateStatus("Grant Nearby Devices permission to discover Cast devices")
                return@setOnClickListener
            }
            if (!isLocationEnabled()) {
                updateStatus("Enable Location Services to discover Cast devices")
                return@setOnClickListener
            }
            val playServicesStatus = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this)
            if (playServicesStatus != ConnectionResult.SUCCESS) {
                updateStatus("Google Play services missing or outdated (code: $playServicesStatus)")
                return@setOnClickListener
            }

            val started = CastManager.startDiscovery(this)
            if (started) {
                updateStatus("Searching for Chromecast devices...")
            } else {
                val error = CastManager.getLastInitError()
                updateStatus(error ?: "Cast framework not available. Update Google Play Services.")
            }
        }

        mirrorButton.setOnClickListener {
            MediaProjectionManager.setProjectionCallback(this)
            MediaProjectionManager.startScreenCapture(this)
        }

        updateButton.setOnClickListener {
            updateStatus("Checking for updates...")
            AppUpdater.checkForUpdate(this)
        }

        val savedSeconds = prefs.getInt(KEY_DISCOVERY_SECONDS, 8)
        CastManager.setDiscoveryWindowMs(savedSeconds * 1000L)
        discoverySlider.value = savedSeconds.toFloat()
        discoveryLabel.text = "Discovery duration: ${savedSeconds}s"
        discoverySlider.addOnChangeListener { _, value, _ ->
            val windowMs = (value.toLong() * 1000L)
            CastManager.setDiscoveryWindowMs(windowMs)
            discoveryLabel.text = "Discovery duration: ${value.toInt()}s"
            prefs.edit().putInt(KEY_DISCOVERY_SECONDS, value.toInt()).apply()
        }

        AppUpdater.checkForUpdate(this)

        // Keep the server available for the Firefox extension.
        if (!CastServerService.isRunning()) {
            startServer()
        } else {
            serverRunning = true
            serverButton.text = "Stop Server"
            serverStatusText.text = "Server: Running on port 5000"
            setServerStatus(true)
        }

        ensureDiscoveryPermissions()
    }

    private fun startServer() {
        try {
            CastServerService.start(this)
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
            CastServerService.stop(this)
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

    override fun onStart() {
        super.onStart()
        CastManager.setDiscoveryListener(this)
        CastManager.setSessionListener(this)
        CastServerService.setMirrorHandler {
            if (MediaProjectionManager.isCapturing()) {
                true
            } else {
                runOnUiThread {
                    MediaProjectionManager.setProjectionCallback(this@MainActivity)
                    MediaProjectionManager.startScreenCapture(this@MainActivity)
                }
                true
            }
        }
    }

    override fun onStop() {
        super.onStop()
        CastServerService.setMirrorHandler(null)
        CastManager.setDiscoveryListener(null)
        CastManager.setSessionListener(null)
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_DISCOVERY_PERMS) {
            if (hasDiscoveryPermission()) {
                updateStatus("Permissions granted. Tap Discover & Cast.")
            } else {
                updateStatus("Permissions denied. Discovery may not work.")
            }
        }
    }

    private fun ensureDiscoveryPermissions(): Boolean {
        if (hasDiscoveryPermission()) return true

        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(android.Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }

        ActivityCompat.requestPermissions(this, perms, REQUEST_DISCOVERY_PERMS)
        return false
    }

    private fun hasDiscoveryPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.NEARBY_WIFI_DEVICES
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    private fun isLocationEnabled(): Boolean {
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        return manager.isLocationEnabled
    }

    override fun onDeviceDiscovered(deviceName: String) {
        runOnUiThread {
            discoveredDevices.add(deviceName)
            updateDeviceList()
            updateStatus("Found: $deviceName")
        }
    }

    override fun onDeviceRemoved(deviceName: String) {
        runOnUiThread {
            discoveredDevices.remove(deviceName)
            updateDeviceList()
            updateStatus("Device removed: $deviceName")
        }
    }

    override fun onDiscoveryStarted() {
        runOnUiThread {
            discoveredDevices.clear()
            updateDeviceList()
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

    private fun updateDeviceList() {
        if (discoveredDevices.isEmpty()) {
            deviceListText.text = "No devices yet"
        } else {
            deviceListText.text = discoveredDevices.joinToString(separator = "\n")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!CastServerService.isRunning()) {
            CastManager.release()
        }
        if (MediaProjectionManager.isCapturing()) {
            MediaProjectionManager.stopCapture()
        }
    }
}
