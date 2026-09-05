package io.github.mangi.eta.agent.model

/**
 * 确定性"会话锚点"（纯逻辑，可 kotlinc 单测）。
 *
 * 长会话把最早轮次裁进滚动摘要后，agent 无法可靠 recall"真正第一条提问"（常把中段当最早）。
 * 这里**从对话历史确定性地取出第一条 user 消息**，作为不可变锚点稳定注入，让 agent 始终知道
 * 本会话最初问了什么；它字节不变 → 缓存友好。只依赖对话本身，不依赖 agent 或压缩，杜绝误归因。
 */
internal object AgentConversationAnchor {

    const val MAX_ANCHOR_CHARS = 600

    /** 第一条 user 消息的有界文本；无 user 消息或为空返回 null。 */
    fun firstUserMessage(history: List<AgentModelClient.ConversationMessage>): String? =
        history
            .firstOrNull { it.role == "user" }
            ?.content
            ?.trim()
            ?.take(MAX_ANCHOR_CHARS)
            ?.takeIf { it.isNotEmpty() }
}
