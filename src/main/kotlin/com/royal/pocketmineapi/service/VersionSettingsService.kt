package com.royal.pocketmineapi.service

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.APP)
@State(
    name = "PocketMineVersionSettings",
    storages = [Storage("pocketmine-version-settings.xml")]
)
class VersionSettingsService : PersistentStateComponent<VersionSettingsService.State> {

    data class State(
        var githubOwner: String = "pmmp",
        var githubRepository: String = "PocketMine-MP",
        var githubToken: String = "",
        var phpExecutable: String = "php",
        var downloadDirectory: String = defaultDownloadDirectory(),
        var extractDirectory: String = defaultExtractDirectory(),
        var activeReleaseTag: String = "",
        var activePharPath: String = "",
        var activeArtifactPath: String = "",
        var activeArtifactName: String = "",
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
    }

    fun update(
        owner: String,
        repository: String,
        token: String,
        phpExecutable: String,
        downloadDirectory: String,
        extractDirectory: String,
    ) {
        myState.githubOwner = owner.trim()
        myState.githubRepository = repository.trim()
        myState.githubToken = token.trim()
        myState.phpExecutable = phpExecutable.trim()
        myState.downloadDirectory = downloadDirectory.trim()
        myState.extractDirectory = extractDirectory.trim()
    }

    fun setActiveArtifact(
        releaseTag: String,
        pharPath: String,
        extractedPath: String,
        artifactName: String,
    ) {
        myState.activeReleaseTag = releaseTag
        myState.activePharPath = pharPath
        myState.activeArtifactPath = extractedPath
        myState.activeArtifactName = artifactName
    }

    companion object {
        fun getInstance(): VersionSettingsService = service()

        private fun defaultDownloadDirectory(): String =
            PathManager.getSystemPath() + "/pocketmine-artifacts/downloads"

        private fun defaultExtractDirectory(): String =
            PathManager.getSystemPath() + "/pocketmine-artifacts/extracted"
    }
}
