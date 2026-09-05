package io.github.mangi.eta.agent.model

import io.github.mangi.eta.data.repository.AgentMemoryRepository

/**
 * 记忆自动合并（第 2 层）：当 MEMORY.md 沉淀条目行超过阈值时，后台用 LLM 生成"合并计划"（哪些行合并成一条规范文本），
 * 再由 [MemoryConsolidate.apply] 安全落地。不依赖 agent 主动调用 memory_consolidate，避免重复条目长期堆积。
 *
 * 仅处理"至少 2 行同义/重叠"的条目，避免把单条无关行误重构。
 */
internal object MemoryAutoConsolidator {

    /** 触发合并的记忆总行数阈值（超过即尝试合并一次）。 */
    const val TRIGGER_LINE_THRESHOLD = 40

    /** 单次最多合并组数，避免一次 LLM 输出过长。 */
    const val MAX_MERGES = 10

    fun buildPrompt(existingLines: List<String>): String = buildString {
        appendLine("记忆 MEMORY.md 里出现了重复、重叠或同义的【沉淀】条目。请找出所有表达同一事实的重复行并给出合并计划。")
        appendLine("规则：")
        appendLine("  - 只有真正的重复/重叠/同义条目才合并（同一事实写了多遍）")
        appendLine("  - 每行一条：MERGE <行号1,行号2,...>: 合并后的规范文本（至少 2 行、行号来自下面的列表、逗号分隔、按升序）")
        appendLine("  - 至少 2 行才合并；不确定就不要合并；不要合并标题、分隔行或彼此无关的行")
        appendLine("  - 没有重复就不要输出任何 MERGE 行")
        appendLine()
        appendLine("已知行（行号从 1 开始）：")
        existingLines.forEachIndexed { idx, line ->
            val t = line.trim().removePrefix("- ").removePrefix("• ").replace("[沉淀]", "").trim()
            if (t.isNotEmpty()) appendLine("${idx + 1}: $t")
        }
    }.trim()

    /** 解析合并计划，行号需在 [1, existingLineCount] 且组内 ≥2 行才采纳；越界组丢弃。 */
    fun parsePlan(text: String, existingLineCount: Int): ConsolidatePlan {
        val merges = mutableListOf<ConsolidateMerge>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (!line.startsWith("MERGE", true)) continue
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val linesStr = line.substring("MERGE".length, colon).trim()
            val canonical = line.substring(colon + 1).trim()
            if (canonical.length < 3) continue
            val nos = linesStr.split(',').mapNotNull { it.trim().toIntOrNull() }
                .filter { it in 1..existingLineCount }
                .distinct()
                .sorted()
            if (nos.size < 2) continue
            merges += ConsolidateMerge(nos, canonical)
            if (merges.size >= MAX_MERGES) break
        }
        return ConsolidatePlan(merges)
    }

    /** 生成并应用合并计划：有改动返回 true。 */
    suspend fun consolidate(
        config: AgentModelClient.ModelConfig,
        provider: AgentProviderClient,
    ): Boolean {
        val snapshot = AgentMemoryRepository.snapshot()
        val lines = snapshot.content.split('\n')
        // 只统计"沉淀条目行"（- 开头）作为触发依据；空记忆直接跳过。
        val entryCount = lines.count { it.trim().startsWith("-") || it.trim().startsWith("•") }
        if (entryCount < TRIGGER_LINE_THRESHOLD) return false

        val result = LlmTextCompletion.complete(
            config = config,
            provider = provider,
            systemPrompt = "你是记忆清理助手，只输出合并计划。",
            userText = buildPrompt(lines),
            maxResultChars = 2_500,
        )
        val plan = parsePlan(result, lines.size)
        if (plan.merges.isEmpty()) return false

        val merged = MemoryConsolidate.apply(plan, lines)
        val newContent = merged.joinToString("\n")
        if (newContent == snapshot.content) return false
        AgentMemoryRepository.replaceAll(newContent)
        return true
    }
}
