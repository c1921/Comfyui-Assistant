package io.github.c1921.comfyui_assistant.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface ConfigDraftStore {
    val draft: StateFlow<WorkflowConfig>

    fun update(transform: (WorkflowConfig) -> WorkflowConfig)
}

class InMemoryConfigDraftStore(
    initialConfig: WorkflowConfig = WorkflowConfig(),
) : ConfigDraftStore {
    private val mutableDraft = MutableStateFlow(initialConfig)

    override val draft: StateFlow<WorkflowConfig> = mutableDraft.asStateFlow()

    override fun update(transform: (WorkflowConfig) -> WorkflowConfig) {
        mutableDraft.update(transform)
    }
}
