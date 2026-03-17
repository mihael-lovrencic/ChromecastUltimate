package com.example.castultimate

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import java.util.concurrent.atomic.AtomicBoolean

object MediaProjectionManager {

    private const val TAG = "MediaProjectionManager"
    private const val VIDEO_MIME_TYPE = "video/avc"
    private const val AUDIO_MIME_TYPE = "audio/mp4a-latm"
    private const val VIDEO_WIDTH = 1280
    private const val VIDEO_HEIGHT = 720
    private const val VIDEO_BITRATE = 4000000
    private const val VIDEO_FRAME_RATE = 30
    private const val AUDIO_SAMPLE_RATE = 44100
    private const val AUDIO_CHANNEL_COUNT = 2

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var videoEncoder: MediaCodec? = null
    private var audioEncoder: MediaCodec? = null
    private var mediaMuxer: MediaMuxer? = null
    private var audioRecord: AudioRecord? = null
    private var encoderSurface: Surface? = null
    private var videoThread: HandlerThread? = null
    private var audioThread: HandlerThread? = null
    private var videoHandler: Handler? = null
    private var audioHandler: Handler? = null
    private var isCapturing = AtomicBoolean(false)
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var muxerStarted = false

    private var projectionCallback: ProjectionCallback? = null

    interface ProjectionCallback {
        fun onProjectionStarted()
        fun onProjectionStopped()
        fun onError(error: String)
    }

    fun setProjectionCallback(callback: ProjectionCallback?) {
        this.projectionCallback = callback
    }

    fun startScreenCapture(activity: Activity) {
        val mediaProjectionManager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        activity.startActivityForResult(captureIntent, REQUEST_CODE_SCREEN_CAPTURE)
    }

    fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                startProjection(activity, resultCode, data)
            } else {
                Log.w(TAG, "Screen capture permission denied")
                projectionCallback?.onError("Permission denied")
            }
        }
    }

    private fun startProjection(context: Context, resultCode: Int, data: Intent) {
        try {
            val mProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mProjectionManager.getMediaProjection(resultCode, data)

            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d(TAG, "MediaProjection stopped")
                    stopCapture()
                    projectionCallback?.onProjectionStopped()
                }
            }, null)

            startVideoEncoder(context)
            startCapture(context)

            isCapturing.set(true)
            projectionCallback?.onProjectionStarted()
            Log.d(TAG, "Screen capture started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start projection", e)
            projectionCallback?.onError(e.message ?: "Unknown error")
        }
    }

    private fun startVideoEncoder(context: Context) {
        try {
            val format = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, VIDEO_WIDTH, VIDEO_HEIGHT).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FRAME_RATE)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            videoEncoder = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                encoderSurface = createInputSurface()
                start()
            }

            videoThread = HandlerThread("VideoEncoderThread").apply { start() }
            videoHandler = Handler(videoThread!!.looper)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start video encoder", e)
        }
    }

    private fun startCapture(context: Context) {
        val metrics = DisplayMetrics()
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)

        val density = metrics.densityDpi

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            VIDEO_WIDTH,
            VIDEO_HEIGHT,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            encoderSurface,
            null,
            null
        )

        Log.d(TAG, "VirtualDisplay created: ${VIDEO_WIDTH}x${VIDEO_HEIGHT}@$density")
    }

    fun startAudioCapture(context: Context) {
        try {
            val audioFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(AUDIO_SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build()

            val bufferSize = AudioRecord.getMinBufferSize(
                AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            audioRecord = AudioRecord(
                android.media.MediaRecorder.AudioSource.MIC,
                AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            val audioFormat2 = MediaFormat.createAudioFormat(AUDIO_MIME_TYPE, AUDIO_SAMPLE_RATE, AUDIO_CHANNEL_COUNT).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 128000)
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            }

            audioEncoder = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE).apply {
                configure(audioFormat2, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }

            audioThread = HandlerThread("AudioEncoderThread").apply { start() }
            audioHandler = Handler(audioThread!!.looper)

            audioRecord?.startRecording()

            audioHandler?.post(object : Runnable {
                override fun run() {
                    encodeAudio()
                    if (isCapturing.get()) {
                        audioHandler?.post(this)
                    }
                }
            })

            Log.d(TAG, "Audio capture started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio capture", e)
        }
    }

    private fun encodeAudio() {
        audioRecord?.let { record ->
            audioEncoder?.let { encoder ->
                val bufferIndex = encoder.dequeueInputBuffer(10000)
                if (bufferIndex >= 0) {
                    val inputBuffer = encoder.getInputBuffer(bufferIndex)
                    val read = record.read(inputBuffer!!, inputBuffer.remaining())
                    if (read > 0) {
                        encoder.queueInputBuffer(bufferIndex, 0, read, System.nanoTime() / 1000, 0)
                    }
                }
                drainEncoder(encoder, false)
            }
        }
    }

    private fun drainEncoder(encoder: MediaCodec, endOfStream: Boolean) {
        if (endOfStream) {
            encoder.signalEndOfInputStream()
        }

        val bufferInfo = MediaCodec.BufferInfo()
        while (true) {
            val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
            when {
                outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val newFormat = encoder.outputFormat
                    if (!muxerStarted) {
                        mediaMuxer = MediaMuxer("/sdcard/screen_capture.mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                        videoTrackIndex = mediaMuxer!!.addTrack(newFormat)
                        audioTrackIndex = mediaMuxer!!.addTrack(newFormat)
                        mediaMuxer!!.start()
                        muxerStarted = true
                    }
                }
                outputBufferIndex >= 0 -> {
                    val encodedData = encoder.getOutputBuffer(outputBufferIndex)
                    if (encodedData != null && bufferInfo.size > 0 && muxerStarted) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        mediaMuxer?.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(outputBufferIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        }
    }

    fun stopCapture() {
        isCapturing.set(false)

        try {
            virtualDisplay?.release()
            virtualDisplay = null

            encoderSurface?.release()
            encoderSurface = null

            videoEncoder?.stop()
            videoEncoder?.release()
            videoEncoder = null

            audioEncoder?.stop()
            audioEncoder?.release()
            audioEncoder = null

            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            mediaMuxer?.stop()
            mediaMuxer?.release()
            mediaMuxer = null

            mediaProjection?.stop()

            videoThread?.quitSafely()
            audioThread?.quitSafely()

            Log.d(TAG, "Screen capture stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping capture", e)
        }
    }

    fun isCapturing(): Boolean = isCapturing.get()

    fun getStreamingUrl(): String {
        return "rtsp://localhost:8554/screen"
    }

    fun startRtspStream(serverAddress: String, port: Int = 8554) {
        val streamUrl = "rtsp://$serverAddress:$port/screen"
        Log.d(TAG, "Starting RTSP stream to: $streamUrl")
    }

    fun startWebRtcStream(signalingUrl: String) {
        Log.d(TAG, "Starting WebRTC stream to: $signalingUrl")
    }

    fun captureFrame(): ByteArray? {
        return null
    }

    const val REQUEST_CODE_SCREEN_CAPTURE = 1000
}
