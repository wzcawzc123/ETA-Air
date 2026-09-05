package io.github.mangi.eta.agent.model

/** 长对话滚动摘要生成器（P2）。真正的实现走 LLM，测试可注入假实现。 */
internal fun interface ConversationSummarizer {
    suspend fun summarize(
        existingSummary: String?,
        turns: List<AgentModelClient.ConversationMessage>,
    ): String
}

/** 用纯文本补全生成滚动摘要（复用 ETA-Air 已有的 [LlmTextCompletion.complete]）。 */
internal class LlmConversationSummarizer(
    private val config: AgentModelClient.ModelConfig,
    private val provider: AgentProviderClient,
) : ConversationSummarizer {

    override suspend fun summarize(
        existingSummary: String?,
        turns: List<AgentModelClient.ConversationMessage>,
    ): String = LlmTextCompletion.complete(
        config = config,
        provider = provider,
        systemPrompt = "你是对话摘要助手。",
        userText = AgentHistorySummary.buildPrompt(existingSummary, turns),
        maxResultChars = AgentHistorySummary.MAX_SUMMARY_CHARS,
    )
}
