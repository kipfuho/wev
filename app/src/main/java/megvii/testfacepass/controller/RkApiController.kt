package megvii.testfacepass.controller

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ys.rkapi.MyManager
import kotlinx.coroutines.*

class RkApiController(
    private val context: Context
) {

    // Vendor API
    private val rk = MyManager.getInstance(context)

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main
    )

    var logText by mutableStateOf("")
        private set

    private fun log(msg: String) {
        logText += msg + "\n"
    }

    /* ================= DEMO ACTIONS ================= */

    fun screenOn() {
        scope.launch {
            rk.turnOnBackLight()
            log("Screen ON")
        }
    }

    fun screenOff() {
        scope.launch {
            rk.turnOffBackLight()
            log("Screen OFF")
        }
    }


    /* ================= UI ================= */

    @Composable
    fun Layout() {
        val scroll = rememberScrollState()

        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Text("RK API Demo", style = MaterialTheme.typography.titleLarge)

            Divider()

            Text("Screen", style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { screenOn() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Screen ON")
                }

                Button(
                    onClick = { screenOff() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Screen OFF")
                }
            }

            Divider()

            Text("Log", style = MaterialTheme.typography.titleMedium)

            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 2.dp
            ) {
                Text(
                    text = logText,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
