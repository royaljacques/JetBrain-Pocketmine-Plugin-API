package com.royal.pocketmineapi.service

import com.intellij.openapi.components.*

@Service(Service.Level.APP)
@State(
    name = "PocketMineWelcomeState",
    storages = [Storage("pocketmine-welcome.xml")]
)
class WelcomeStateService : PersistentStateComponent<WelcomeStateService.State> {

    data class State(var shown: Boolean = false)

    private var state = State()

    override fun getState() = state
    override fun loadState(state: State) { this.state = state }

    fun isShown() = state.shown
    fun markShown() { state.shown = true }

    companion object {
        fun getInstance() = service<WelcomeStateService>()
    }
}