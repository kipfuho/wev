package com.example.myapplication
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// crt

// ocr
import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import com.crt.Crt900x
import com.crt.c900xplatform.PermissionHelper
import com.example.myapplication.model.SdkDemo
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.ui.theme.SdkDemoScreen
import com.grg.ocr.OcrDetectResult
import com.grg.ocr.OcrUtil
import com.grg.ocr.OcrUtil.OrcCallBack
import com.ys.rkapi.MyManager
import java.io.IOException
import java.io.InputStream


class MainActivity : ComponentActivity() {

    private lateinit var manager: MyManager
    private var mOcrUtil: OcrUtil? = null
    private var passportReader: Crt900x? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        manager = MyManager.getInstance(this)

        if (!PermissionHelper.checkStoragePermissions(this)) {
            requestAllPermissions()
        }
        if (!PermissionHelper.checkCameraPermissions(this)) {
            requestAllPermissions()
        }

        mOcrUtil = OcrUtil.getInstance(baseContext)
        passportReader = Crt900x(this)

        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {

                val demos = listOf(
                    SdkDemo(
                        name = "CRT900x",
                        apis = listOf(
                            "open()",
                            "readCard()",
                            "close()"
                        ),
                        onTest = {
                            Log.d("SDK", "Testing CRT900x")
                            // call CRT test method
                        }
                    ),
                    SdkDemo(
                        name = "OCR",
                        apis = listOf(
                            "init()",
                            "detect()"
                        ),
                        onTest = {
                            Log.d("SDK", "Testing OCR")
                        }
                    ),
                    SdkDemo(
                        name = "RK API",
                        apis = listOf(
                            "connect()",
                            "getStatus()"
                        ),
                        onTest = {
                            Log.d("SDK", "Testing RK API")
                        }
                    ),
                    SdkDemo(
                        name = "SDK 4",
                        apis = listOf(
                            "start()",
                            "stop()"
                        ),
                        onTest = {
                            Log.d("SDK", "Testing SDK 4")
                        }
                    )
                )

                SdkDemoScreen(demos, modifier = Modifier.padding(top = 32.dp))
            }
        }
    }

    private fun requestAllPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA
            ),
            PermissionHelper.REQUEST_PERMISSIONS_REQUEST_CODE
        )
    }
}
