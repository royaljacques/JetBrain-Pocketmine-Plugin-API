package com.royal.pocketmineapi.service

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.jetbrains.php.config.library.PhpIncludePathManager

internal class PhpIncludePathService {

    private val logger = thisLogger()

    internal fun registerIncludePath(project: Project, extractedPath: String) {
        if (extractedPath.isBlank()) return

        try {
            val manager = PhpIncludePathManager.getInstance(project)

            val current: List<String> = manager.includePath

            val cleaned = current.filter { path ->
                !path.contains("pocketmine-artifacts", ignoreCase = true)
            }.toMutableList()

            if (!cleaned.contains(extractedPath)) {
                cleaned.add(extractedPath)
            }

            manager.includePath = cleaned

            LocalFileSystem.getInstance().refreshAndFindFileByPath(extractedPath)

            logger.info("PhpStorm include path updated: $extractedPath")

        } catch (e: Exception) {
            logger.warn("Unable to update PhpStorm include path", e)
            throw IllegalStateException("Include path registration error: ${e.message}", e)
        }
    }

    internal fun unregisterIncludePaths(project: Project) {
        try {
            val manager = PhpIncludePathManager.getInstance(project)
            val cleaned = manager.includePath.filter { path ->
                !path.contains("pocketmine-artifacts", ignoreCase = true)
            }
            manager.includePath = cleaned
            logger.info("Include paths PocketMine retires")
        } catch (e: Exception) {
            logger.warn("Unable to remove include paths", e)
        }
    }
}
