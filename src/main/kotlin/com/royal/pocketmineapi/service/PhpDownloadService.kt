package com.royal.pocketmineapi.service

import com.intellij.openapi.diagnostic.thisLogger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.zip.ZipInputStream

internal class PhpDownloadService {

    private val logger = thisLogger()

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    companion object {

        private const val PHP_DOWNLOAD_URL =
            "https://windows.php.net/downloads/releases/php-8.3.19-Win32-vs16-x64.zip"

        private const val PHP_VERSION = "8.3.19"

        fun getDefaultPhpDirectory(baseDir: Path): Path =
            baseDir.resolve("php-$PHP_VERSION")

        fun getDefaultPhpExecutable(baseDir: Path): Path =
            getDefaultPhpDirectory(baseDir).resolve("php.exe")
    }

    /**
     * Vérifie si php.exe est accessible (soit dans le PATH, soit au chemin fourni).
     */
    internal fun isPhpAvailable(phpExecutable: String): Boolean {
        if (phpExecutable.isBlank()) return isPhpInPath()
        val file = java.io.File(phpExecutable)
        return file.exists() && file.canExecute()
    }

    internal fun isPhpInPath(): Boolean {
        return try {
            val process = ProcessBuilder("php", "-r", "echo 1;")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            process.waitFor() == 0 && output == "1"
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Télécharge et extrait PHP dans [destinationDir].
     * Retourne le chemin vers php.exe.
     */
    internal fun downloadPhp(
        destinationDir: Path,
        onProgress: (String) -> Unit = {}
    ): Path {
        Files.createDirectories(destinationDir)

        val zipPath = destinationDir.parent.resolve("php-download.zip")

        onProgress("Téléchargement de PHP $PHP_VERSION...")
        logger.info("Téléchargement PHP depuis $PHP_DOWNLOAD_URL")

        val request = HttpRequest.newBuilder()
            .uri(URI.create(PHP_DOWNLOAD_URL))
            .timeout(Duration.ofMinutes(5))
            .header("User-Agent", "PocketMineAPI-JetBrains-Plugin")
            .GET()
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())

        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("Échec téléchargement PHP (${response.statusCode()})")
        }

        response.body().use { input ->
            Files.copy(input, zipPath, StandardCopyOption.REPLACE_EXISTING)
        }

        onProgress("Extraction de PHP...")
        extractZip(zipPath, destinationDir)

        Files.deleteIfExists(zipPath)

        val phpExe = destinationDir.resolve("php.exe")
        if (!Files.exists(phpExe)) {
            throw IllegalStateException("php.exe introuvable après extraction dans $destinationDir")
        }

        ensurePharEnabled(destinationDir)

        logger.info("PHP installé: $phpExe")
        return phpExe
    }

    private fun extractZip(zipFile: Path, destination: Path) {
        ZipInputStream(Files.newInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val target = destination.resolve(entry.name)
                if (entry.isDirectory) {
                    Files.createDirectories(target)
                } else {
                    Files.createDirectories(target.parent)
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun ensurePharEnabled(phpDir: Path) {
        val iniDev = phpDir.resolve("php.ini-development")
        val ini = phpDir.resolve("php.ini")

        if (!Files.exists(ini)) {
            if (Files.exists(iniDev)) {
                Files.copy(iniDev, ini)
            } else {
                Files.writeString(ini, "[PHP]\nextension_dir = \"ext\"\nphar.readonly = Off\n")
                return
            }
        }

        var content = Files.readString(ini)
        content = content.replace(Regex("(?m)^;?phar\\.readonly\\s*=.*$"), "phar.readonly = Off")
        if (!content.contains("phar.readonly")) {
            content += "\nphar.readonly = Off\n"
        }
        Files.writeString(ini, content)
    }
}
