package com.royal.pocketmineapi.service

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

class PhpDownloadService {

    enum class PhpBranch(val version: String, val toolset: String) {
        PHP_83("8.3", "vs16"),
        PHP_84("8.4", "vs17")
    }

    private val httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    fun downloadPhp(
        destinationDir: Path,
        branch: PhpBranch = PhpBranch.PHP_84,
        nts: Boolean = false,
        x64: Boolean = true,
        progress: ((String) -> Unit)? = null
    ): Path {
        if (!isWindows()) {
            throw IllegalStateException(
                "Automatic PHP download is only supported on Windows. Please configure the PHP executable manually."
            )
        }

        val downloadUrl = buildPhpDownloadUrl(branch, nts, x64)
        Files.createDirectories(destinationDir)
        val zipPath = destinationDir.resolveSibling("php-download.zip")

        try {
            progress?.invoke("Downloading PHP...")
            val request = HttpRequest.newBuilder()
                .uri(URI.create(downloadUrl))
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())

            if (response.statusCode() !in 200..299) {
                throw IllegalStateException("PHP download failed (${response.statusCode()}) from $downloadUrl")
            }

            Files.copy(response.body(), zipPath, StandardCopyOption.REPLACE_EXISTING)

            progress?.invoke("Extracting PHP...")
            unzip(zipPath, destinationDir)

            val phpExecutable = getPhpExecutable(destinationDir)
            if (!Files.exists(phpExecutable)) {
                throw IllegalStateException("php.exe was not found after extraction.")
            }

            progress?.invoke("PHP installed.")
            return phpExecutable
        } finally {
            try {
                Files.deleteIfExists(zipPath)
            } catch (_: Exception) {
            }
        }
    }

    fun isPhpAvailable(executable: String): Boolean {
        if (executable.isBlank()) return false

        val candidate = executable.trim()
        if (candidate.equals("php", ignoreCase = true)) {
            return isPhpInPath()
        }

        val path = Paths.get(candidate)
        if (!Files.exists(path) || !Files.isRegularFile(path)) return false

        return try {
            val process = ProcessBuilder(candidate, "-v")
                .redirectErrorStream(true)
                .start()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (_: Exception) {
            false
        }
    }

    fun isPhpInPath(): Boolean {
        return try {
            val command = if (isWindows()) listOf("where", "php") else listOf("which", "php")
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (_: Exception) {
            false
        }
    }

    private fun buildPhpDownloadUrl(
        branch: PhpBranch,
        nts: Boolean,
        x64: Boolean
    ): String {
        val ntsPart = if (nts) "-nts" else ""
        val archPart = if (x64) "x64" else "x86"
        val fileName = "php-${branch.version}$ntsPart-Win32-${branch.toolset}-$archPart-latest.zip"
        return "https://downloads.php.net/~windows/releases/latest/$fileName"
    }

    private fun unzip(zipFile: Path, targetDir: Path) {
        ZipInputStream(Files.newInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val newPath = zipSlipProtect(entry, targetDir)
                if (entry.isDirectory) {
                    Files.createDirectories(newPath)
                } else {
                    Files.createDirectories(newPath.parent)
                    Files.copy(zis, newPath, StandardCopyOption.REPLACE_EXISTING)
                }
                entry = zis.nextEntry
            }
        }
    }

    private fun zipSlipProtect(entry: java.util.zip.ZipEntry, targetDir: Path): Path {
        val resolvedPath = targetDir.resolve(entry.name).normalize()
        if (!resolvedPath.startsWith(targetDir.normalize())) {
            throw IOException("Invalid ZIP entry: ${entry.name}")
        }
        return resolvedPath
    }

    private fun isWindows(): Boolean {
        return System.getProperty("os.name").lowercase().contains("win")
    }

    companion object {
        fun getDefaultPhpDirectory(baseDir: Path): Path = baseDir.resolve("php")

        fun getDefaultPhpExecutable(baseDir: Path): Path =
            getDefaultPhpDirectory(baseDir).resolve("php.exe")
    }

    fun getPhpExecutable(baseDir: Path): Path = baseDir.resolve("php.exe")
}
