package com.example.castultimate

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), CastManager.DiscoveryListener, CastManager.SessionListener, MediaProjectionManager.ProjectionCallback {

    private lateinit var castButton: Button
    private lateinit var mirrorButton: Button
    private lateinit var serverButton: Button
    private lateinit var statusText: TextView
    
    private var server: CastServer? = null
    private var serverRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        castButton = findViewById(R.id.castButton)
        mirrorButton = findViewById(R.id.mirrorButton)
        serverButton = findViewById(R.id.serverButton)
        statusText = findViewById(R.id.statusText)

        CastManager.initialize(this)
        CastManager.setDiscoveryListener(this)
        CastManager.setSessionListener(this)

        castButton.setOnClickListener {
            CastManager.startDiscovery()
            showStatus("Starting Chromecast discovery...")
        }

        mirrorButton.setOnClickListener {
            MediaProjectionManager.setProjectionCallback(this)
            MediaProjectionManager.startScreenCapture(this)
        }

        serverButton.setOnClickListener {
            if (serverRunning) {
                stopServer()
            } else {
                startServer()
            }
        }
    }

    private fun startServer() {
        try {
            server = CastServer(5000)
            server?.start()
            serverRunning = true
            serverButton.text = "Stop Server"
            showStatus("Server running on port 5000")
        } catch (e: Exception) {
            showStatus("Server error: ${e.message}")
        }
    }

    private fun stopServer() {
        try {
            server?.stop()
            server = null
            serverRunning = false
            serverButton.text = "Start Server"
            showStatus("Server stopped")
        } catch (e: Exception) {
            showStatus("Server error: ${e.message}")
        }
    }

    private fun showStatus(message: String) {
        statusText.text = message
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == MediaProjectionManager.REQUEST_CODE_SCREEN_CAPTURE) {
            MediaProjectionManager.onActivityResult(this, requestCode, resultCode, data)
        }
    }

    override fun onDeviceDiscovered(deviceName: String) {
        runOnUiThread {
            showStatus("Found: $deviceName")
        }
    }

    override fun onDeviceRemoved(deviceName: String) {
        runOnUiThread {
            showStatus("Device removed: $deviceName")
        }
    }

    override fun onDiscoveryStarted() {
        runOnUiThread {
            showStatus("Discovery started")
        }
    }

    override fun onDiscoveryStopped() {
        runOnUiThread {
            showStatus("Discovery stopped")
        }
    }

    override fun onSessionStarted(session: com.google.android.gms.cast.framework.CastSession) {
        runOnUiThread {
            showStatus("Connected to Chromecast")
        }
    }

    override fun onSessionEnded(error: String?) {
        runOnUiThread {
            showStatus("Disconnected: $error")
        }
    }

    override fun onConnectivityChanged(connected: Boolean) {
        runOnUiThread {
            val message = if (connected) "Connected" else "Disconnected"
            showStatus(message)
        }
    }

    override fun onProjectionStarted() {
        runOnUiThread {
            showStatus("Screen capture started")
        }
    }

    override fun onProjectionStopped() {
        runOnUiThread {
            showStatus("Screen capture stopped")
        }
    }

    override fun onError(error: String) {
        runOnUiThread {
            showStatus("Error: $error")
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
