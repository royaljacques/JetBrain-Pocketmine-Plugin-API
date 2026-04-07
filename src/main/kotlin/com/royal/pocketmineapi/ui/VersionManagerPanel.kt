package com.royal.pocketmineapi.ui

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.royal.pocketmineapi.model.GithubAsset
import com.royal.pocketmineapi.model.GithubRelease
import com.royal.pocketmineapi.service.GithubReleaseService
import com.royal.pocketmineapi.service.PharExtractionService
import com.royal.pocketmineapi.service.PhpDownloadService
import com.royal.pocketmineapi.service.PhpIncludePathService
import com.royal.pocketmineapi.service.VersionSettingsService
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.*

class VersionManagerPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val logger = thisLogger()
    private val settings = VersionSettingsService.getInstance()
    private val githubService = GithubReleaseService()
    private val pharService = PharExtractionService()
    private val phpDownloadService = PhpDownloadService()
    private val includePathService = PhpIncludePathService()

    private val releaseListModel = DefaultListModel<GithubRelease>()
    private val releaseList = JBList(releaseListModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = ReleaseListCellRenderer()
    }

    private val assetListModel = DefaultListModel<GithubAsset>()
    private val assetList = JBList(assetListModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = AssetListCellRenderer()
    }

    private val statusLabel = JBLabel("No active version.").apply {
        border = BorderFactory.createEmptyBorder(4, 8, 4, 8)
    }

    private val phpStatusLabel = JBLabel("").apply {
        border = BorderFactory.createEmptyBorder(2, 8, 2, 8)
        font = font.deriveFont(11f)
    }

    private val fetchButton = JButton("Charger les versions")
    private val downloadButton = JButton("Telecharger et Extraire").apply { isEnabled = false }
    private val settingsButton = JButton("Parametres")

    init {
        buildUi()
        refreshStatusLabel()
        refreshPhpStatus()
        wireListeners()
    }

    private fun buildUi() {
        val topPanel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4))
        topPanel.add(fetchButton)
        topPanel.add(settingsButton)
        add(topPanel, BorderLayout.NORTH)

        val releasesPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("Versions disponibles")
            add(JBScrollPane(releaseList), BorderLayout.CENTER)
        }
        val assetsPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("Artefacts (.phar)")
            add(JBScrollPane(assetList), BorderLayout.CENTER)
        }
        val split = JSplitPane(JSplitPane.VERTICAL_SPLIT, releasesPanel, assetsPanel).apply {
            resizeWeight = 0.6
            isContinuousLayout = true
        }
        add(split, BorderLayout.CENTER)

        val bottomPanel = JPanel(BorderLayout())
        val labelsPanel = JPanel(BorderLayout())
        labelsPanel.add(statusLabel, BorderLayout.CENTER)
        labelsPanel.add(phpStatusLabel, BorderLayout.SOUTH)
        bottomPanel.add(labelsPanel, BorderLayout.CENTER)
        bottomPanel.add(downloadButton, BorderLayout.EAST)
        bottomPanel.border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
        add(bottomPanel, BorderLayout.SOUTH)
    }

    private fun wireListeners() {
        fetchButton.addActionListener { fetchReleases() }
        settingsButton.addActionListener { openSettings() }
        downloadButton.addActionListener { downloadAndExtract() }

        releaseList.addListSelectionListener {
            if (!it.valueIsAdjusting) populateAssets(releaseList.selectedValue)
        }
        assetList.addListSelectionListener {
            if (!it.valueIsAdjusting) downloadButton.isEnabled = assetList.selectedValue != null
        }
    }

    private fun fetchReleases() {
        val s = settings.getState()
        fetchButton.isEnabled = false
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Loading GitHub releases...", false) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                try {
                    val releases = githubService.fetchReleases(
                        s.githubOwner,
                        s.githubRepository,
                        s.githubToken.ifBlank { null }
                    )
                    ApplicationManager.getApplication().invokeLater {
                        releaseListModel.clear()
                        assetListModel.clear()
                        releases.forEach { releaseListModel.addElement(it) }
                        fetchButton.isEnabled = true
                        statusLabel.text = if (releases.isEmpty()) "No release found."
                        else "${releases.size} release(s) chargee(s)."
                    }
                } catch (ex: Exception) {
                    logger.warn("Release fetch error", ex)
                    ApplicationManager.getApplication().invokeLater {
                        fetchButton.isEnabled = true
                        notify("Loading error: ${ex.message}", NotificationType.ERROR)
                    }
                }
            }
        })
    }

    private fun populateAssets(release: GithubRelease?) {
        assetListModel.clear()
        downloadButton.isEnabled = false
        if (release == null) return
        val assets = release.pharAssets
        if (assets.isEmpty()) {
            assetListModel.addElement(
                GithubAsset(id = -1, name = "Aucun .phar disponible", size = 0, browserDownloadUrl = "")
            )
        } else {
            assets.forEach { assetListModel.addElement(it) }
            assetList.selectedIndex = 0
        }
    }

    private fun downloadAndExtract() {
        val release = releaseList.selectedValue ?: return
        val asset = assetList.selectedValue ?: return
        if (asset.browserDownloadUrl.isBlank()) return

        downloadButton.isEnabled = false

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Installation de ${asset.name}...", false) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                try {
                    val s = settings.getState()
                    val downloadDir: Path = Paths.get(s.downloadDirectory)
                    val extractDir: Path = Paths.get(s.extractDirectory).resolve(release.tagName)


                    val phpExe = resolvePhpExecutable(indicator, downloadDir)


                    indicator.text = "Downloading ${asset.name}..."
                    val pharFile: Path = githubService.downloadAsset(asset, downloadDir)


                    indicator.text = "Extraction du PHAR..."
                    val extracted: Path = pharService.extractPhar(pharFile, extractDir, phpExe)


                    settings.setActiveArtifact(
                        releaseTag = release.tagName,
                        pharPath = pharFile.toAbsolutePath().toString(),
                        extractedPath = extracted.toAbsolutePath().toString(),
                        artifactName = asset.name,
                    )


                    indicator.text = "Enregistrement include path PhpStorm..."
                    includePathService.registerIncludePath(project, extracted.toAbsolutePath().toString())

                    ApplicationManager.getApplication().invokeLater {
                        refreshStatusLabel()
                        refreshPhpStatus()
                        downloadButton.isEnabled = true
                        notify(
                            "${asset.name} installe (${release.tagName}) — autocompletion activee !",
                            NotificationType.INFORMATION
                        )
                    }
                } catch (ex: Exception) {
                    logger.warn("Download/extract error", ex)
                    ApplicationManager.getApplication().invokeLater {
                        downloadButton.isEnabled = true
                        notify("Error: ${ex.message}", NotificationType.ERROR)
                    }
                }
            }
        })
    }

    private fun resolvePhpExecutable(indicator: ProgressIndicator, baseDir: Path): String {
        val s = settings.getState()
        val configured = s.phpExecutable.trim()

        if (configured.isNotBlank() && phpDownloadService.isPhpAvailable(configured)) {
            return configured
        }
        if (phpDownloadService.isPhpInPath()) {
            return "php"
        }
        val bundledPhp = PhpDownloadService.getDefaultPhpExecutable(baseDir)
        if (phpDownloadService.isPhpAvailable(bundledPhp.toString())) {
            settings.update(
                owner = s.githubOwner, repository = s.githubRepository, token = s.githubToken,
                phpExecutable = bundledPhp.toString(),
                downloadDirectory = s.downloadDirectory, extractDirectory = s.extractDirectory,
            )
            return bundledPhp.toString()
        }

        indicator.text = "PHP not found - automatic download..."
        val phpDir = PhpDownloadService.getDefaultPhpDirectory(baseDir)
        val phpExe = phpDownloadService.downloadPhp(phpDir) { msg -> indicator.text = msg }

        settings.update(
            owner = s.githubOwner, repository = s.githubRepository, token = s.githubToken,
            phpExecutable = phpExe.toString(),
            downloadDirectory = s.downloadDirectory, extractDirectory = s.extractDirectory,
        )
        ApplicationManager.getApplication().invokeLater { refreshPhpStatus() }
        return phpExe.toString()
    }

    private fun openSettings() {
        val dialog = SettingsDialog(settings)
        if (dialog.showAndGet()) {
            refreshStatusLabel()
            refreshPhpStatus()
        }
    }

    private fun refreshStatusLabel() {
        val s = settings.getState()
        statusLabel.text = if (s.activeReleaseTag.isBlank()) "No active version."
        else "Active version: ${s.activeReleaseTag}  —  ${s.activeArtifactName}"
    }

    private fun refreshPhpStatus() {
        val s = settings.getState()
        val php = s.phpExecutable.trim()
        phpStatusLabel.text = when {
            php.isNotBlank() && phpDownloadService.isPhpAvailable(php) -> "PHP : $php"
            phpDownloadService.isPhpInPath() -> "PHP: detected in PATH"
            else -> "PHP: will be downloaded automatically on first use"
        }
    }

    private fun notify(message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("PocketMine Notifications")
            .createNotification(message, type)
            .notify(project)
    }

    private inner class ReleaseListCellRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
        ): java.awt.Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            if (value is GithubRelease) {
                text = value.displayName()
                toolTipText = value.publishedAt?.take(10) ?: ""
            }
            return this
        }
    }

    private inner class AssetListCellRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
        ): java.awt.Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            if (value is GithubAsset) text = value.displayName()
            return this
        }
    }
}

private class SettingsDialog(private val settings: VersionSettingsService) :
    com.intellij.openapi.ui.DialogWrapper(true) {

    private val ownerField = JTextField(settings.getState().githubOwner, 28)
    private val repoField = JTextField(settings.getState().githubRepository, 28)
    private val tokenField = JPasswordField(settings.getState().githubToken, 28)
    private val phpField = JTextField(settings.getState().phpExecutable, 36)
    private val downloadDirField = JTextField(settings.getState().downloadDirectory, 36)
    private val extractDirField = JTextField(settings.getState().extractDirectory, 36)

    init {
        title = "Parametres PocketMine"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gc = GridBagConstraints().apply {
            insets = Insets(4, 6, 4, 6)
            anchor = GridBagConstraints.WEST
        }

        fun row(label: String, field: JComponent, y: Int, hint: String? = null) {
            gc.gridx = 0; gc.gridy = y * 2; gc.fill = GridBagConstraints.NONE; gc.gridwidth = 1
            panel.add(JBLabel(label), gc)
            gc.gridx = 1; gc.fill = GridBagConstraints.HORIZONTAL; gc.weightx = 1.0
            panel.add(field, gc)
            gc.weightx = 0.0
            if (hint != null) {
                gc.gridx = 1; gc.gridy = y * 2 + 1; gc.fill = GridBagConstraints.NONE
                val hintLabel = JBLabel(hint)
                hintLabel.font = hintLabel.font.deriveFont(10f)
                panel.add(hintLabel, gc)
            }
        }

        row("Owner GitHub :", ownerField, 0)
        row("Depot GitHub :", repoField, 1)
        row("Token GitHub (optionnel) :", tokenField, 2)
        row("PHP executable:", phpField, 3, "Leave empty for automatic download")
        row("Download directory:", downloadDirField, 4)
        row("Extraction directory:", extractDirField, 5)

        return panel
    }

    override fun doOKAction() {
        settings.update(
            owner = ownerField.text,
            repository = repoField.text,
            token = String(tokenField.password),
            phpExecutable = phpField.text,
            downloadDirectory = downloadDirField.text,
            extractDirectory = extractDirField.text,
        )
        super.doOKAction()
    }
}
