package io.github.mangi.eta.agent.model

/**
 * 长对话滚动摘要的纯逻辑（P2，可 kotlinc 单测）。
 *
 * 决策、格式化与预算都集中在这里；真正的模型调用由 [LlmConversationSummarizer] 完成。
 */
internal object AgentHistorySummary {

    const val DEFAULT_REGENERATE_THRESHOLD_TURNS = 8
    const val MAX_SUMMARY_CHARS = 2_000
    const val MAX_SUMMARY_TURNS_TEXT_CHARS = 12_000
    const val MAX_TURN_CONTENT_CHARS = 800

    /** 未总结内容格式化后达到该字符数时，即使轮数未到阈值也触发摘要（避免"短但密集"的对话丢信息）。 */
    const val SUMMARY_DENSE_CHARS_TRIGGER = 6_000

    /** 距离上次摘要后又新增被裁剪的用户轮数达到阈值，或未总结内容体积已超阈值时才重新生成（增量，避免每轮触发）。 */
    fun needsRegeneration(
        trimmedTurnsSinceLastSummary: Int,
        thresholdTurns: Int = DEFAULT_REGENERATE_THRESHOLD_TURNS,
        pendingChars: Int = 0,
    ): Boolean =
        trimmedTurnsSinceLastSummary >= thresholdTurns ||
            pendingChars >= SUMMARY_DENSE_CHARS_TRIGGER

    /** 待总结轮次序列化后的字符体积（供密度触发判定，可 kotlinc 单测）。 */
    fun serializedCharCount(turns: List<AgentModelClient.ConversationMessage>): Int =
        serializeTurns(turns).length

    /**
     * 统计完整历史相对窗口历史被裁剪掉的用户轮数。
     *
     * 窗口始终是完整历史的后缀（AgentHistoryWindow.trim 返回 subList 切片），因此被裁掉
     * 的部分 = fullHistory.take(fullHistory.size - windowedHistory.size)。直接用**索引差**
     * 计算，而非按内容 indexOfFirst——否则当两条 user 消息内容完全相同时可能匹配到更早的
     * 那条，导致被裁轮数统计错误。
     */
    fun trimmedUserTurnCount(
        fullHistory: List<AgentModelClient.ConversationMessage>,
        windowedHistory: List<AgentModelClient.ConversationMessage>,
    ): Int {
        if (fullHistory.isEmpty()) return 0
        val cutIndex = fullHistory.size - windowedHistory.size
        if (cutIndex <= 0) return 0
        return fullHistory.take(cutIndex).count { it.role == "user" }
    }

    /**
     * 从被裁剪的历史中切出"上次已总结 existingUserTurns 个用户轮之后"的增量部分。
     * 用于增量摘要（P1-2）：只把新增的被裁轮次交给模型，避免重复总结旧内容。
     * existingUserTurns <= 0 时返回全部（首次生成）。
     */
    fun incrementalTurnsSince(
        trimmedTurns: List<AgentModelClient.ConversationMessage>,
        existingUserTurns: Int,
    ): List<AgentModelClient.ConversationMessage> {
        if (existingUserTurns <= 0 || trimmedTurns.isEmpty()) return trimmedTurns
        var seen = 0
        val start = trimmedTurns.indexOfFirst { msg ->
            if (msg.role == "user") {
                seen += 1
                seen == existingUserTurns + 1
            } else {
                false
            }
        }
        if (start < 0) return emptyList()
        return trimmedTurns.subList(start, trimmedTurns.size).toList()
    }

    /** 把早期轮次格式化为模型可读文本（有界）。 */
    fun serializeTurns(
        turns: List<AgentModelClient.ConversationMessage>,
        maxChars: Int = MAX_SUMMARY_TURNS_TEXT_CHARS,
    ): String {
        val sb = StringBuilder()
        for (turn in turns) {
            if (sb.length >= maxChars) break
            val role = when (turn.role) {
                "user" -> "用户"
                "assistant" -> "助手"
                else -> turn.role
            }
            val content = turn.content.ifBlank { turn.contentJson }
                .trim()
                .take(MAX_TURN_CONTENT_CHARS)
            if (content.isBlank()) continue
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(role).append(": ").append(content)
        }
        return sb.toString().take(maxChars).trim()
    }

    /** 摘要生成的模型提示词（确定性，可精确断言）。 */
    fun buildPrompt(
        existingSummary: String?,
        turns: List<AgentModelClient.ConversationMessage>,
    ): String = buildString {
        appendLine("请把以下早期对话内容压缩为一段精炼的中文摘要，保留关键事实、用户指示、约束和任务进展。")
        if (!existingSummary.isNullOrBlank()) {
            appendLine()
            appendLine("已有摘要（请在其基础上合并新增内容，不要重复）：")
            appendLine(existingSummary.take(MAX_SUMMARY_CHARS))
        }
        appendLine()
        appendLine("早期对话：")
        appendLine(serializeTurns(turns))
        appendLine()
        appendLine("输出要求：只输出摘要本身，不要解释；保留重要数字、名字、路径和明确约束。")
    }.trim()

    fun clampSummary(text: String, maxChars: Int = MAX_SUMMARY_CHARS): String =
        text.trim().take(maxChars)
}
