package data.model

import java.io.File

data class TempImage(
    val file: File,
    val expiresAt: Long
)