package com.royal.pocketmineapi.service

import com.intellij.openapi.diagnostic.thisLogger
import java.nio.file.Files
import java.nio.file.Path

internal class PharExtractionService {

    private val logger = thisLogger()

    internal fun extractPhar(
        pharFile: Path,
        destinationDir: Path,
        phpExecutable: String
    ): Path {
        if (!Files.exists(pharFile)) {
            throw IllegalStateException("Fichier PHAR introuvable: $pharFile")
        }

        if (phpExecutable.isBlank()) {
            throw IllegalStateException("Le chemin vers PHP CLI est vide.")
        }

        Files.createDirectories(destinationDir)

        val pharPath = escapePhpString(pharFile.toAbsolutePath().toString())
        val destinationPath = escapePhpString(destinationDir.toAbsolutePath().toString())

        val script = """
            ${'$'}phar = new Phar('$pharPath');
            ${'$'}phar->extractTo('$destinationPath', null, true);
            echo 'OK';
        """.trimIndent()

        val process = ProcessBuilder(
            phpExecutable,
            "-d", "phar.readonly=0",
            "-r", script
        )
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()

        if (exitCode != 0 || !output.contains("OK")) {
            throw IllegalStateException("Erreur extraction PHAR:\n$output")
        }

        logger.info("PHAR extrait vers: $destinationDir")
        return destinationDir
    }

    private fun escapePhpString(value: String): String {
        return value.replace("\\", "\\\\").replace("'", "\\'")
    }
}
