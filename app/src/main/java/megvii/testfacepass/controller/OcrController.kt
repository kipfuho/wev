package megvii.testfacepass.controller

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import megvii.testfacepass.CameraActivity
import com.grg.ocr.OcrDetectResult
import com.grg.ocr.OcrUtil

class OcrResult {
    companion object {
        var no by mutableStateOf("")
        var birthDate by mutableStateOf("")
        var expiryDate by mutableStateOf("")
    }
}

class OcrController(private val context: Context) {

    private val ocrUtil = OcrUtil.getInstance(context)
    private var modelLoaded = false
    private var detectRunning = false

    private fun initOcr(): Boolean {
        if (!modelLoaded) {
            modelLoaded = ocrUtil.onLoadModel()
            if (modelLoaded) {
                ocrUtil.onRunModel()
            }
        }
        return modelLoaded
    }



    fun detectImage(
        bitmap: Bitmap,
        onResult: (String) -> Unit
    ) {
        if (!initOcr()) {
            onResult("Failed to load OCR model")
            return
        }

        ocrUtil.detectSync(bitmap, object : OcrUtil.OrcCallBack {
            override fun getResult(result: OcrDetectResult?) {
                val resultList = result!!.resultList
                val mrz1: String = getMRZ(resultList.get("MRZ1"))
                val mrz2: String = getMRZ(resultList.get("MRZ2"))
                if (mrz1.isEmpty() || mrz2.isEmpty()) {
                    onResult("No text detected")
                    return
                }
                //Vietnamese ID card
                OcrResult.no = mrz1.substring(5, 14)
                OcrResult.birthDate = mrz2.substring(0, 6)
                OcrResult.expiryDate = mrz2.substring(8, 14)
                Log.i(
                    "OCRCONTROLLER",
                    "idCard ===> no:" + OcrResult.no + " birthDate:" + OcrResult.birthDate + " expiryDate:" + OcrResult.expiryDate
                )

            }

            override fun AuthFailed() {
                onResult("OCR authorization failed")
            }
        })
    }

    private fun getMRZ(mrzString: String?): String {
        if (mrzString == null) return ""
        try {
            val split = mrzString.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            return split[0]
        } catch (e: Exception) {
            return ""
        }
    }

    fun uriToBitmap(context: Context, uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    }

    fun loadBitmapFromUri(context: Context, uriString: String): Bitmap? {
        val uri = Uri.parse(uriString)
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        }
    }

    // -------- COMPOSE UI --------

    @Composable
    fun Layout() {
        val context = LocalContext.current
        var ocrText by remember { mutableStateOf("Waiting for scan…") }
        var bitmap by remember { mutableStateOf<Bitmap?>(null) }

        // Camera launcher
        val cameraLauncher =
            rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val uriString = result.data?.getStringExtra("image_path")
                    if (!uriString.isNullOrEmpty()) {
                        bitmap = loadBitmapFromUri(context, uriString)
                    }
                }
            }

        // Gallery launcher
        val galleryLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                uri?.let {
                    bitmap = uriToBitmap(context, it)
                }
            }

        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(16.dp)) {

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !detectRunning,
                    onClick = {
                        val intent = Intent(context, CameraActivity::class.java)
                        cameraLauncher.launch(intent)
                    }
                ) {
                    Text("Open Camera")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !detectRunning,
                    onClick = {
                        galleryLauncher.launch("image/*")
                    }
                ) {
                    Text("Choose from Gallery")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = bitmap != null && !detectRunning,
                    onClick = {
                        detectImage(bitmap!!) { result ->
                            ocrText = result
                        }
                    }
                ) {
                    Text("Run OCR")
                }

                Spacer(modifier = Modifier.height(12.dp))

                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Selected image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        contentScale = ContentScale.Fit
                    )
                }
                if (detectRunning) {
                    Text(text = ocrText)
                } else if (bitmap != null) {
                    Text(text = "Số CCCD: ${OcrResult.no}")
                    Text(text = "Ngày sinh: ${OcrResult.birthDate}")
                    Text(text = "Hạn SD: ${OcrResult.expiryDate}")
                }
            }
        }
    }
}
