package megvii.testfacepass.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import megvii.testfacepass.model.SdkDemo

@Composable
fun SdkDemoContent(demo: SdkDemo) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "APIs",
            style = MaterialTheme.typography.titleMedium
        )
        
        // We let the demo's Layout handle its own inner structure (and scrolling if needed)
        demo.Layout()
    }
}
