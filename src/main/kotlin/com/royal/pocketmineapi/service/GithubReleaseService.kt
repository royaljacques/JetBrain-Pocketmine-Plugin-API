package com.royal.pocketmineapi.service

import com.intellij.openapi.diagnostic.thisLogger
import com.royal.pocketmineapi.model.GithubAsset
import com.royal.pocketmineapi.model.GithubRelease
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration

internal class GithubReleaseService {

    private val logger = thisLogger()
    private val json = Json { ignoreUnknownKeys = true }

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    internal fun fetchReleases(owner: String, repository: String, token: String?): List<GithubRelease> {
        val url = "https://api.github.com/repos/$owner/$repository/releases"

        val requestBuilder: HttpRequest.Builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "PocketMineAPI-JetBrains-Plugin")
            .GET()

        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        val response: HttpResponse<String> =
            client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("GitHub API responded with ${response.statusCode()} : ${response.body()}")
        }

        return json.decodeFromString(
            ListSerializer(GithubRelease.serializer()),
            response.body()
        )
    }

    internal fun findPocketMinePharAsset(release: GithubRelease): GithubAsset? {
        return release.pharAssets.firstOrNull { asset: GithubAsset ->
            asset.name.contains("PocketMine-MP", ignoreCase = true)
        } ?: release.pharAssets.firstOrNull()
    }

    internal fun downloadAsset(asset: GithubAsset, destinationDirectory: Path): Path {
        Files.createDirectories(destinationDirectory)

        val target: Path = destinationDirectory.resolve(asset.name)

        val request: HttpRequest = HttpRequest.newBuilder()
            .uri(URI.create(asset.browserDownloadUrl))
            .timeout(Duration.ofMinutes(2))
            .header("Accept", "application/octet-stream")
            .header("User-Agent", "PocketMineAPI-JetBrains-Plugin")
            .GET()
            .build()

        val response: HttpResponse<InputStream> =
            client.send(request, HttpResponse.BodyHandlers.ofInputStream())

        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("Unable to download ${asset.name} (${response.statusCode()})")
        }

        response.body().use { input: InputStream ->
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
        }

        logger.info("Downloaded asset: $target")
        return target
    }
}