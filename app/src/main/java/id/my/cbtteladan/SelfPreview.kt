package id.my.cbtteladan

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import android.view.View

/**
 * Cermin diri untuk siswa: kamera depan ditampilkan di layar sendiri, seperti
 * preview webcam di aplikasi Windows. Gambar HANYA ditampilkan; tidak direkam,
 * tidak disimpan, dan tidak dikirim ke server mana pun.
 *
 * Memakai Camera2 langsung agar aplikasi tidak perlu menambah pustaka AndroidX.
 * Semua kegagalan (tanpa kamera, izin ditolak, kamera dipakai aplikasi lain)
 * ditangani diam-diam: ujian tetap berjalan tanpa preview.
 */
class SelfPreview(
    private val activity: Activity,
    private val textureView: TextureView,
    /** Dipanggil di thread utama: true saat gambar sudah tampil, false saat berhenti. */
    private val onActive: (Boolean) -> Unit,
) {
    private val manager: CameraManager? = activity.getSystemService(CameraManager::class.java)
    private val lock = Any()

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var surface: Surface? = null
    private var previewSize: Pair<Int, Int>? = null
    private var sensorOrientation = 0

    @Volatile
    private var wanted = false

    private val surfaceListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            if (wanted) open()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = fitToView()

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            close()
            return true
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }

    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> fitToView() }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            synchronized(lock) {
                if (!wanted) {
                    camera.close()
                    return
                }
                device = camera
            }
            createSession(camera)
        }

        override fun onDisconnected(camera: CameraDevice) = close()

        override fun onError(camera: CameraDevice, error: Int) = close()
    }

    fun hasFrontCamera(): Boolean = frontCameraId() != null

    fun hasPermission(): Boolean =
        activity.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    /** Mulai menampilkan preview bila ada kamera depan dan izin sudah diberikan. */
    fun start() {
        if (wanted || !hasPermission() || !hasFrontCamera()) return
        wanted = true
        textureView.surfaceTextureListener = surfaceListener
        textureView.addOnLayoutChangeListener(layoutListener)
        if (textureView.isAvailable) open()
    }

    fun stop() {
        wanted = false
        textureView.removeOnLayoutChangeListener(layoutListener)
        close()
    }

    private fun frontCameraId(): String? {
        val cameraManager = manager ?: return null
        return try {
            cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_FRONT
            }
        } catch (e: CameraAccessException) {
            null
        } catch (e: RuntimeException) {
            null
        }
    }

    @SuppressLint("MissingPermission") // izin diperiksa di start()
    private fun open() {
        val cameraManager = manager ?: return
        val id = frontCameraId() ?: return
        try {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            val sizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(SurfaceTexture::class.java)
                ?.map { it.width to it.height }
                .orEmpty()
            previewSize = PreviewMath.chooseSize(sizes) ?: return

            val cameraHandler = synchronized(lock) {
                handler ?: HandlerThread("cbt-self-preview").let {
                    it.start()
                    thread = it
                    Handler(it.looper).also { created -> handler = created }
                }
            }
            cameraManager.openCamera(id, stateCallback, cameraHandler)
        } catch (e: SecurityException) {
            close()
        } catch (e: CameraAccessException) {
            close()
        } catch (e: RuntimeException) {
            close()
        }
    }

    @Suppress("DEPRECATION")
    private fun createSession(camera: CameraDevice) {
        try {
            val size = previewSize ?: return close()
            val texture = textureView.surfaceTexture ?: return close()
            texture.setDefaultBufferSize(size.first, size.second)
            val target = Surface(texture)
            synchronized(lock) { surface = target }

            val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(target)
                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            }

            camera.createCaptureSession(
                listOf(target),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(configured: CameraCaptureSession) {
                        synchronized(lock) {
                            if (!wanted || device !== camera) {
                                configured.close()
                                return
                            }
                            session = configured
                        }
                        try {
                            configured.setRepeatingRequest(request.build(), null, handler)
                            activity.runOnUiThread {
                                if (wanted) {
                                    fitToView()
                                    onActive(true)
                                }
                            }
                        } catch (e: CameraAccessException) {
                            close()
                        } catch (e: IllegalStateException) {
                            close()
                        }
                    }

                    override fun onConfigureFailed(configured: CameraCaptureSession) = close()
                },
                handler,
            )
        } catch (e: CameraAccessException) {
            close()
        } catch (e: RuntimeException) {
            close()
        }
    }

    private fun close() {
        val hadCamera: Boolean
        synchronized(lock) {
            hadCamera = device != null || session != null
            try {
                session?.close()
            } catch (e: RuntimeException) {
                // Sudah tertutup
            }
            try {
                device?.close()
            } catch (e: RuntimeException) {
                // Sudah tertutup
            }
            surface?.release()
            surface = null
            session = null
            device = null
            thread?.quitSafely()
            thread = null
            handler = null
        }
        if (hadCamera || wanted) {
            activity.runOnUiThread { onActive(false) }
        }
    }

    /** Putar dan potong gambar agar tegak, memenuhi kotak, dan tidak gepeng. */
    private fun fitToView() {
        val size = previewSize ?: return
        val viewWidth = textureView.width
        val viewHeight = textureView.height
        if (viewWidth == 0 || viewHeight == 0) return

        val portrait = PreviewMath.isPortrait(sensorOrientation, displayDegrees(), front = true)
        val contentWidth = if (portrait) size.second else size.first
        val contentHeight = if (portrait) size.first else size.second
        val (scaleX, scaleY) = PreviewMath.cropScale(contentWidth, contentHeight, viewWidth, viewHeight)

        textureView.setTransform(
            Matrix().apply { postScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f) },
        )
        // Cermin: seperti bercermin, kiri-kanan dibalik
        textureView.scaleX = -1f
    }

    @Suppress("DEPRECATION")
    private fun displayDegrees(): Int {
        val rotation = activity.windowManager.defaultDisplay.rotation
        return when (rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }
}
