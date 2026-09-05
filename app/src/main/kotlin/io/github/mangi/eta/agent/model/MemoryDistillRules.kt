package io.github.mangi.eta.agent.model

/**
 * Mem0 式记忆蒸馏决策（纯逻辑，可 kotlinc 单测）。
 *
 * 提取出的"事实"不该无脑追加进 MEMORY.md（会污染/堆重复）；这里对每条事实对比已有记忆行，
 * 判定为 新增(additions) / 覆盖已有行(updates) / 已存在跳过(noop)。这从根上防止"记忆混乱、只增不更新"。
 */
internal object MemoryDistillRules {

    /** 覆盖已有记忆的某一行：startLine（1-based）用 [content] 整行替换。 */
    data class Update(val startLine: Int, val content: String)

    data class Plan(
        val additions: List<String>,
        val updates: List<Update>,
        val noop: List<String>,
    )

    /**
     * 对每条提取事实 [extracted] 结合已有记忆行 [existingLines] 产出计划。
     * - 已存在等价内容 -> noop（跳过，避免重复堆积）
     * - 新事实更完整/覆盖旧事实 -> updates（替换该行，实现"更新/合并"，而非追加副本）
     * - 其它 -> additions（追加）
     */
    fun plan(
        extracted: List<String>,
        existingLines: List<String>,
    ): Plan {
        val additions = mutableListOf<String>()
        val updates = mutableListOf<Update>()
        val noop = mutableListOf<String>()
        val usedLines = mutableSetOf<Int>()
        for (fact in extracted) {
            val f = fact.trim()
            if (f.length < 3) continue
            val fn = normalize(f)
            // 1) 与某行完全等价（归一化后相同）→ 已存在，跳过（防重复堆积）
            val eq = existingLines.indices.firstOrNull { i ->
                (i + 1) !in usedLines && fn == normalize(existingLines[i])
            }
            if (eq != null) {
                noop += f
                usedLines += eq + 1
                continue
            }
            // 2) 新内容更长且包含旧内容 → 视为"更完整的更新"，覆盖旧的那行
            val superseded = existingLines.indices.firstOrNull { i ->
                (i + 1) !in usedLines && supersedes(fn, normalize(existingLines[i]))
            }
            if (superseded != null) {
                updates += Update(startLine = superseded + 1, content = f)
                usedLines += superseded + 1
                continue
            }
            // 3) 否则新增
            additions += f
        }
        return Plan(additions = additions, updates = updates, noop = noop)
    }

    /** 覆盖/更新：新内容（归一化后）更长且包含旧内容，视为"更完整的更新"，用新内容覆盖旧的那行。 */
    private fun supersedes(newNorm: String, oldNorm: String): Boolean =
        newNorm.length > oldNorm.length && oldNorm.isNotEmpty() && newNorm.contains(oldNorm)

    private fun normalize(text: String): String {
        var t = text.trim().removePrefix("- ").removePrefix("• ")
        // 剥掉沉淀标记与高亮符号，比较基于内容本身，避免 "[沉淀]" 干扰相似度判定。
        t = t.replace("[沉淀]", "")
        val punct = " \t，。！？；：、,.!?;:()【】[]\"'“”"
        return t.lowercase().filter { it !in punct }
    }
}
