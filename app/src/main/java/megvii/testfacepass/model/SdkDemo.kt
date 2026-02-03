package megvii.testfacepass.model

import androidx.compose.runtime.Composable

data class SdkDemo(
    val name: String,
    val Layout: @Composable () -> Unit
)