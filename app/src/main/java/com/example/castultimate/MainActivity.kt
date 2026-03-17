package com.example.castultimate

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.cast.framework.CastDevice

class MainActivity : AppCompatActivity(), CastManager.DiscoveryListener, CastManager.SessionListener, MediaProjectionManager.ProjectionCallback {

    private lateinit var castButton: Button
    private lateinit var mirrorButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        castButton = findViewById(R.id.castButton)
        mirrorButton = findViewById(R.id.mirrorButton)

        CastManager.initialize(this)
        CastManager.setDiscoveryListener(this)
        CastManager.setSessionListener(this)

        castButton.setOnClickListener {
            CastManager.startDiscovery(this)
            Toast.makeText(this, "Starting Chromecast discovery...", Toast.LENGTH_SHORT).show()
        }

        mirrorButton.setOnClickListener {
            MediaProjectionManager.setProjectionCallback(this)
            MediaProjectionManager.startScreenCapture(this)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == MediaProjectionManager.REQUEST_CODE_SCREEN_CAPTURE) {
            MediaProjectionManager.onActivityResult(this, requestCode, resultCode, data)
        }
    }

    override fun onDeviceDiscovered(device: CastDevice) {
        runOnUiThread {
            Toast.makeText(this, "Found: ${device.friendlyName}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDeviceRemoved(deviceName: String) {
        runOnUiThread {
            Toast.makeText(this, "Device removed: $deviceName", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDiscoveryStarted() {
        runOnUiThread {
            Toast.makeText(this, "Discovery started", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDiscoveryStopped() {
        runOnUiThread {
            Toast.makeText(this, "Discovery stopped", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSessionStarted(session: com.google.android.gms.cast.framework.CastSession) {
        runOnUiThread {
            Toast.makeText(this, "Connected to Chromecast", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSessionEnded(error: String?) {
        runOnUiThread {
            Toast.makeText(this, "Disconnected: $error", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onConnectivityChanged(connected: Boolean) {
        runOnUiThread {
            val message = if (connected) "Connected" else "Disconnected"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onProjectionStarted() {
        runOnUiThread {
            Toast.makeText(this, "Screen capture started", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onProjectionStopped() {
        runOnUiThread {
            Toast.makeText(this, "Screen capture stopped", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onError(error: String) {
        runOnUiThread {
            Toast.makeText(this, "Error: $error", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        CastManager.release()
        if (MediaProjectionManager.isCapturing()) {
            MediaProjectionManager.stopCapture()
        }
    }
}
