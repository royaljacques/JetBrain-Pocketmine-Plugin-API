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

            logger.info("Include path PhpStorm mis a jour : $extractedPath")

        } catch (e: Exception) {
            logger.warn("Impossible de mettre a jour l'include path PhpStorm", e)
            throw IllegalStateException("Erreur enregistrement include path : ${e.message}", e)
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
            logger.warn("Impossible de retirer les include paths", e)
        }
    }
}
