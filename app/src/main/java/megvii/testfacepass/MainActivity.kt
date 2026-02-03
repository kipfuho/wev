package megvii.testfacepass

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.crt.c900xplatform.PermissionHelper
import megvii.testfacepass.controller.Crt900xController
import megvii.testfacepass.controller.FacePassController
import megvii.testfacepass.controller.OcrController
import megvii.testfacepass.controller.RkApiController
import megvii.testfacepass.model.SdkDemo
import megvii.testfacepass.ui.theme.MyApplicationTheme
import megvii.testfacepass.ui.theme.SdkDemoScreen

class MainActivity : ComponentActivity() {
    private lateinit var crtController: Crt900xController
    private lateinit var rkController: RkApiController
    private lateinit var ocrController: OcrController
    private lateinit var facePassController: FacePassController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Controllers
        crtController = Crt900xController(this)
        rkController = RkApiController(this)
        ocrController = OcrController(this)
        facePassController = FacePassController(this)

        // Permissions
        if (!PermissionHelper.checkStoragePermissions(this) || !PermissionHelper.checkCameraPermissions(this)) {
            requestAllPermissions()
        }

        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val demos = listOf(
                    SdkDemo(
                        name = "OCR",
                        Layout = { ocrController.Layout() }
                    ),
                    SdkDemo(
                        name = "CRT900x",
                        Layout = { crtController.Layout() }
                    ),
                    SdkDemo(
                        name = "YSAPI",
                        Layout = { rkController.Layout() }
                    ),
//                    SdkDemo(
//                        name = "FacePass",
//                        Layout = { facePassController.Layout() }
//                    )
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
