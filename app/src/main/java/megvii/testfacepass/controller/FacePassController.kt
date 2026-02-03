package megvii.testfacepass.controller

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mcv.facepass.FacePassHandler
import mcv.facepass.auth.FacePassAuthCode
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class FacePassController(private val context: Context) {
    val FACE_ALGOMALL_CERT_PATH: String = "/Download/FacePassSDK.cert"

    private var facePassHandler: FacePassHandler? = null
    var testResult by mutableStateOf("")
        private set

    private fun log(msg: String) {
        testResult += msg + "\n"
    }

    fun initAuth(): Boolean {
        return try {
            val cert = readExternal(FACE_ALGOMALL_CERT_PATH).trim()
            Log.i("FacePassController", "Cert: $cert")

            val ret = FacePassHandler.auth_algomall(cert)
            if (ret == FacePassAuthCode.FP_AUTH_OK) {
                log("Face algo mall success.")
                true
            } else {
                log("Face algo mall failed: $ret")
                false
            }
        } catch (e: Exception) {
            log("Auth exception: ${e.message}")
            false
        }
    }

    fun initSDK() {
        if (facePassHandler != null) {
            log("SDK already initialized")
            return
        }

        try {
            // FacePassHandler.init is usually called in a background thread
            Thread {
                FacePassHandler.initSDK(context, "") // Second param is often data directory
                facePassHandler = FacePassHandler.getVersion()?.let {
                    log("SDK Initialized. Version: $it")
                    // Real initialization usually requires passing a configuration
                    null
                }
            }.start()
        } catch (e: Exception) {
            log("SDK Init Error: ${e.message}")
        }
    }

    fun readExternal(filename: String): String {
        val sb = StringBuilder()
        var fileNameVar = filename
        if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            fileNameVar = Environment.getExternalStorageDirectory().absolutePath +
                    File.separator + fileNameVar
            val file = File(fileNameVar)
            if (!file.exists()) {
                return ""
            }
            var inputStream: FileInputStream? = null
            try {
                inputStream = FileInputStream(fileNameVar)
                val buffer = ByteArray(1024)
                var len = inputStream.read(buffer)
                while (len > 0) {
                    sb.append(String(buffer, 0, len))
                    len = inputStream.read(buffer)
                }
                inputStream.close()
                return sb.toString()
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                if (inputStream == null) {
                    try {
                        inputStream?.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }
        return sb.toString()
    }

    @Composable
    fun Layout() {
        Column(modifier = Modifier.padding(16.dp)) {
            Button(onClick = { initSDK() }) {
                Text("Initialize SDK")
            }

            Button(onClick = { initAuth() }) {
                Text("Initialize Auth")
            }

            Text(
                text = testResult,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}
