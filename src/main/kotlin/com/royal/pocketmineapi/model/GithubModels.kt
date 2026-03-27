package com.royal.pocketmineapi.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GithubRelease(
    val id: Long,
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<GithubAsset> = emptyList(),
) {
    val pharAssets: List<GithubAsset>
        get() = assets.filter { asset ->
            asset.name.endsWith(".phar", ignoreCase = true)
        }

    fun displayName(): String {
        val title = name?.takeIf { it.isNotBlank() } ?: tagName
        val suffix = buildString {
            if (prerelease) append(" • prerelease")
            if (draft) append(" • draft")
        }
        return title + suffix
    }
}

@Serializable
data class GithubAsset(
    val id: Long,
    val name: String,
    val size: Long,
    @SerialName("download_count") val downloadCount: Int = 0,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    @SerialName("content_type") val contentType: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    fun displayName(): String = "$name (${formatBytes(size)})"

    private fun formatBytes(value: Long): String {
        if (value < 1024) return "$value B"

        val units = arrayOf("KB", "MB", "GB", "TB")
        var current = value.toDouble()
        var index = -1

        while (current >= 1024 && index < units.lastIndex) {
            current /= 1024
            index++
        }

        return "%.1f %s".format(current, units[index])
    }
}