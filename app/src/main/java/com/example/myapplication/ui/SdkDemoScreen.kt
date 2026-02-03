package com.example.myapplication.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.myapplication.model.SdkDemo
import com.example.myapplication.ui.SdkDemoContent

@Composable
fun SdkDemoScreen(demos: List<SdkDemo>, modifier: Modifier = Modifier) {
    var selectedTab by remember { mutableStateOf(0) }

    Column(modifier = modifier) {
        TabRow(selectedTabIndex = selectedTab) {
            demos.forEachIndexed { index, demo ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(demo.name) }
                )
            }
        }

        SdkDemoContent(demo = demos[selectedTab])
    }
}
