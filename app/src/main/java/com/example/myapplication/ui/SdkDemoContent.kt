package com.example.myapplication.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.SdkDemo

@Composable
fun SdkDemoContent(demo: SdkDemo) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Text(
                text = "APIs",
                style = MaterialTheme.typography.titleMedium
            )
        }

        items(demo.apis) { api ->
            Text(
                text = "• $api",
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = demo.onTest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Test SDK")
            }
        }
    }
}
