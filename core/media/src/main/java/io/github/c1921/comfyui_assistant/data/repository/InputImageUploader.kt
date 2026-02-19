package io.github.c1921.comfyui_assistant.data.repository

import android.net.Uri

interface InputImageUploader {
    suspend fun uploadInputImage(
        apiKey: String,
        imageUri: Uri,
    ): Result<String>
}
