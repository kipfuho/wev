package megvii.testfacepass

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.*
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

data class ScanRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

class CameraActivity : ComponentActivity() {

    private lateinit var cameraManager: CameraManager
    private var currentLens = CameraCharacteristics.LENS_FACING_EXTERNAL
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private lateinit var previewSize: Size

    private val cameraThread = HandlerThread("CameraThread")
    private lateinit var cameraHandler: Handler

    private var textureView: TextureView? = null

    lateinit var scanRect: ScanRect

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        cameraThread.start()
        cameraHandler = Handler(cameraThread.looper)

        setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                CameraPreview()
                ScanningOverlay {
                    scanRect = it
                }

                BackHandler {
                    finish()
                }

                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Button(onClick = {
                        val view = textureView
                        val bmp = view?.bitmap
                        if (view != null && bmp != null) {
                            val cropped = fixLeftRight(cropBitmapToScanRect(
                                bitmap = bmp,
                                previewWidth = view.width,
                                previewHeight = view.height ,
                                scanRect = scanRect
                            ))
                            saveBitmapToGallery(fixLeftRight(bmp), "ocr_test.jpg")
                            var imageUriString  = saveBitmapToGallery(cropped, "ocr_test_cropped.jpg")
                            val resultIntent = Intent().apply {
                                putExtra("image_path", imageUriString)
                            }

                            setResult(RESULT_OK, resultIntent)
                        }

                        finish()
                    }) {
                        Text("Capture")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { finish() }) {
                        Text("Back")
                    }
                }
            }
        }
    }

    @Composable
    fun ScanningOverlay(
        onRectCalculated: (ScanRect) -> Unit
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val rectWidth = size.width * 0.65f
            val rectHeight = rectWidth * 0.18f

            val left = (size.width - rectWidth) / 2f
            val top = (size.height - rectHeight) / 2f + 60

            onRectCalculated(
                ScanRect(
                    x = left.toInt(),
                    y = top.toInt(),
                    width = rectWidth.toInt(),
                    height = rectHeight.toInt()
                )
            )

            drawRect(
                color = Color.Green,
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(rectWidth, rectHeight),
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        closeCamera()
        cameraThread.quitSafely()
    }

    private fun getCameraId(lensFacing: Int): String? {
        return try {
            cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.LENS_FACING) == lensFacing
            } ?: cameraManager.cameraIdList.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private fun chooseBestSize(sizes: Array<Size>): Size {
        return sizes.maxByOrNull { it.width * it.height } ?: sizes[0]
    }

    @Composable
    fun CameraPreview() {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                TextureView(context).apply {
                    this@CameraActivity.textureView = this
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            openCamera(this@apply)
                        }
                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture) = true
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                    }
                }
            }
        )
    }

    private fun openCamera(textureView: TextureView) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 0)
            return
        }

        val cameraId = getCameraId(currentLens) ?: return
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        previewSize = chooseBestSize(map.getOutputSizes(SurfaceTexture::class.java))

        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    cameraDevice = device
                    startPreview(textureView)
                }
                override fun onDisconnected(device: CameraDevice) {
                    device.close()
                    cameraDevice = null
                }
                override fun onError(device: CameraDevice, error: Int) {
                    device.close()
                    cameraDevice = null
                }
            }, cameraHandler)
        } catch (e: Exception) {
            Log.e("CameraActivity", "Error opening camera", e)
        }
    }

    private fun startPreview(textureView: TextureView) {
        val texture = textureView.surfaceTexture ?: return
        texture.setDefaultBufferSize(previewSize.width, previewSize.height)
        val surface = Surface(texture)
        val device = cameraDevice ?: return

        val requestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surface)
        }

        device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                captureSession = session
                try {
                    session.setRepeatingRequest(requestBuilder.build(), null, cameraHandler)
                } catch (e: Exception) {
                    Log.e("CameraActivity", "Error setting repeating request", e)
                }
            }
            override fun onConfigureFailed(session: CameraCaptureSession) {}
        }, cameraHandler)
    }

    fun fixLeftRight(bitmap: Bitmap): Bitmap {
        val matrix = Matrix().apply {
            postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
        }

        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    }

    private fun cropBitmapToScanRect(
        bitmap: Bitmap,
        previewWidth: Int,
        previewHeight: Int,
        scanRect: ScanRect
    ): Bitmap {
        val scaleX = bitmap.width.toFloat() / previewWidth
        val scaleY = bitmap.height.toFloat() / previewHeight

        val x = (scanRect.x * scaleX).toInt()
        val y = (scanRect.y * scaleY).toInt()
        val w = (scanRect.width * scaleX).toInt()
        val h = (scanRect.height * scaleY).toInt()

        return Bitmap.createBitmap(
            bitmap,
            x.coerceAtLeast(0),
            y.coerceAtLeast(0),
            w.coerceAtMost(bitmap.width - x),
            h.coerceAtMost(bitmap.height - y)
        )
    }

    private fun saveBitmapToGallery(bitmap: Bitmap, filename: String): String {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/MyCameraApp"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = contentResolver
        val uri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ) ?: return ""

        resolver.openOutputStream(uri)?.use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
        }

        var uriString = uri.toString()
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uriString
    }

    private fun closeCamera() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
    }
}
