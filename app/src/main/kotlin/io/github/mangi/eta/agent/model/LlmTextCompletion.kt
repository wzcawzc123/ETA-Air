package io.github.mangi.eta.agent.model

import io.github.mangi.eta.agent.runtime.AgentRunController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** 一次性的纯文本补全：摘要/事实提取/记忆合并共用，避免各自重复 provider 调用装配。 */
internal object LlmTextCompletion {

    suspend fun complete(
        config: AgentModelClient.ModelConfig,
        provider: AgentProviderClient,
        systemPrompt: String,
        userText: String,
        maxResultChars: Int,
    ): String = withContext(Dispatchers.IO) {
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", systemPrompt))
            .put(AgentConversationCodec.userMessage(userText, emptyList()))
        val request = ProviderRequest(config = config, messages = messages, tools = JSONArray())
        val text = StringBuilder()
        provider.complete(request, AgentRunController()) { event ->
            if (event is ProviderEvent.BlockDelta && event.kind == AssistantBlockKind.TEXT) {
                text.append(event.delta)
            }
        }
        text.toString().trim().take(maxResultChars)
    }
}
