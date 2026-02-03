package com.example.myapplication.model

data class SdkDemo(
    val name: String,
    val apis: List<String>,
    val onTest: () -> Unit
)