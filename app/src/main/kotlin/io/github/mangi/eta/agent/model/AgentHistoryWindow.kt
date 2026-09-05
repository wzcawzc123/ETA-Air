package io.github.mangi.eta.agent.model

/**
 * 长对话历史上下文预算（纯逻辑、可 kotlinc 单测）。
 *
 * 此前 history 纯追加、注入时全量塞给模型，长对话会无限增长并撑爆
 * 模型 context window。这里按"最近 N 个用户轮"做滑动窗口裁剪，防止超窗，
 * 同时保证裁剪后的窗口以 user 消息开头、不产生孤儿 tool/assistant 消息。
 * 只影响喂给模型的注入副本，UI 与持久化保持全量。
 *
 * 轮数不再用"每轮固定 token"硬估：工具密集/长消息的轮次实际体积大，若按固定
 * 估算会误以为能装下 80 轮、实际只装得下 25 轮，导致模型静默截断而失忆。这里
 * 以"历史占据 context window 的比例"换算成字符预算，再从末尾向前累计整条消息的
 * 实际序列化体积，得到能装下的用户轮数；同时保留轮数粗估作为总上限，避免轻内容时
 * 无限塞入大量轮次。
 */
internal object AgentHistoryWindow {

    private const val DEFAULT_MAX_USER_ROUNDS = 24
    private const val DEFAULT_MIN_USER_ROUNDS = 3
    private const val TOKENS_PER_ROUND_ESTIMATE = 1_600

    /** 历史最多占据 context window 的比例；其余留给 system prompt、记忆、当前输入与工具轮。 */
    private const val HISTORY_CHAR_FRACTION = 0.40

    /** 单条消息的 JSON 序列化固定开销（role 键、引号、括号等）。 */
    private const val JSON_MESSAGE_OVERHEAD = 32L

    fun trim(
        history: List<AgentModelClient.ConversationMessage>,
        contextWindow: Int?,
        defaultMaxUserRounds: Int = DEFAULT_MAX_USER_ROUNDS,
        tokensPerRoundEstimate: Int? = null,
    ): List<AgentModelClient.ConversationMessage> {
        val turns = if (contextWindow != null && contextWindow > 0) {
            // 实测预算与轮数粗估取小：重内容被体积预算收紧，轻内容受轮数上限约束。
            // tokensPerRoundEstimate 为真实观测的每轮 token（由 AgentTokenBudget 反馈），无则用默认估值。
            val est = tokensPerRoundEstimate ?: TOKENS_PER_ROUND_ESTIMATE
            val roundEstimate = (contextWindow / est)
                .coerceIn(DEFAULT_MIN_USER_ROUNDS, 60)
            maxUserRoundsByBudget(history, contextWindow, roundEstimate)
                .coerceAtMost(roundEstimate)
                .coerceAtLeast(DEFAULT_MIN_USER_ROUNDS)
        } else {
            defaultMaxUserRounds
        }
        return trimByUserRounds(history, turns)
    }

    /**
     * 按实测体积估算能装下的用户轮数，封顶为 [cap]。
     *
     * 从末尾向前累计整条消息的序列化字符开销，直到超出预算或达到 [cap]。
     * 以 user 消息作为轮边界；预算按 1 token ≈ 1 字符估算（对中文最保守，宁可少留也不超窗）。
     */
    fun maxUserRoundsByBudget(
        history: List<AgentModelClient.ConversationMessage>,
        contextWindow: Int,
        cap: Int = 60,
    ): Int {
        if (contextWindow <= 0) return 0
        val budgetChars = (contextWindow * HISTORY_CHAR_FRACTION).toInt()
        if (budgetChars <= 0) return 0
        var used = 0L
        var rounds = 0
        for (index in history.indices.reversed()) {
            val cost = serializedChars(history[index])
            if (used + cost > budgetChars.toLong()) break
            used += cost
            if (history[index].role == "user") {
                rounds += 1
                if (rounds >= cap) break
            }
        }
        return rounds
    }

    /**
     * 单条消息的序列化字符开销代理：近似 provider 实际发送的 JSON 体积。
     *
     * 只统计文本字段与固定开销，不依赖具体 tokenizer，可 kotlinc 单测。
     */
    fun serializedChars(message: AgentModelClient.ConversationMessage): Long =
        message.role.length.toLong() +
            message.content.length +
            message.contentJson.length +
            message.reasoningContent.length +
            message.toolCallsJson.length +
            message.toolCallId.length +
            JSON_MESSAGE_OVERHEAD

    fun trimByUserRounds(
        history: List<AgentModelClient.ConversationMessage>,
        maxUserRounds: Int,
    ): List<AgentModelClient.ConversationMessage> {
        if (maxUserRounds <= 0 || history.isEmpty()) return history
        val userIndices = history.indices.filter { history[it].role == "user" }
        if (userIndices.size <= maxUserRounds || userIndices.isEmpty()) return history
        val cutIndex = userIndices[userIndices.size - maxUserRounds]
        return history.subList(cutIndex, history.size).toList()
    }
}
