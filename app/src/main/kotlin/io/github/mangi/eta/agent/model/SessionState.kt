package io.github.mangi.eta.agent.model

/**
 * 当前会话的结构化状态（目标/已完成/待办/决定）——"记住上下文/决定过什么"的正主。
 * agent 在关键节点维护它并稳定注入，避免"长任务执行中忘了做到哪、决定过什么"。纯逻辑可 kotlinc 单测。
 */
internal data class SessionState(
    val objective: String = "",
    val completed: List<String> = emptyList(),
    val pending: List<String> = emptyList(),
    val decisions: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = objective.isBlank() && completed.isEmpty() && pending.isEmpty() && decisions.isEmpty()
}

/** SessionState 的有界渲染与解析（供注入与工具使用），纯逻辑可单测。 */
internal object SessionStateCodec {

    const val MAX_ITEM_CHARS = 200
    const val MAX_ITEMS = 12
    const val MAX_TOTAL_CHARS = 1_200

    /** 渲染为紧凑文本；有界（单条、条数、总量都有上限）。 */
    fun render(state: SessionState): String = buildString {
        if (state.objective.isNotBlank()) {
            appendLine("目标：${state.objective.take(MAX_ITEM_CHARS)}")
        }
        appendBlock("已完成", state.completed)
        appendBlock("待办", state.pending)
        appendBlock("决定", state.decisions)
    }.trim().take(MAX_TOTAL_CHARS)

    private fun StringBuilder.appendBlock(label: String, items: List<String>) {
        if (items.isEmpty()) return
        append(label).append("：")
        append(items.take(MAX_ITEMS).joinToString("；") { it.take(MAX_ITEM_CHARS) })
        appendLine()
    }

    /** 从渲染文本粗略还原（供展示/审计）；目标是"尽量不丢信息"。 */
    fun parse(text: String): SessionState {
        var objective = ""
        val completed = mutableListOf<String>()
        val pending = mutableListOf<String>()
        val decisions = mutableListOf<String>()
        for (line in text.lineSequence()) {
            val l = line.trim()
            when {
                l.startsWith("目标：") -> objective = l.removePrefix("目标：").trim()
                l.startsWith("已完成：") -> completed += l.removePrefix("已完成：").split("；")
                    .map { it.trim() }.filter { it.isNotEmpty() }
                l.startsWith("待办：") -> pending += l.removePrefix("待办：").split("；")
                    .map { it.trim() }.filter { it.isNotEmpty() }
                l.startsWith("决定：") -> decisions += l.removePrefix("决定：").split("；")
                    .map { it.trim() }.filter { it.isNotEmpty() }
            }
        }
        return SessionState(objective, completed, pending, decisions)
    }
}
