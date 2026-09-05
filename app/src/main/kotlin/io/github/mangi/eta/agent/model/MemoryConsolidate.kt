package io.github.mangi.eta.agent.model

/**
 * 记忆合并（consolidation）的纯逻辑（可 kotlinc 单测）。
 *
 * 记忆里同义/重叠的条目会堆积（字符启发式去重抓不到"同义改写/部分重叠"）。
 * 这里让 LLM 产出"合并计划"（哪些行合并成一条规范文本），本对象负责**安全地把计划应用到现有行**：
 * 删除被合并的源行，并在每组**首个源行位置**插入规范文本；越界/非法组跳过。
 */
internal data class ConsolidateMerge(
    /** 要被合并/删除的源行号（1-based）。 */
    val sourceLines: List<Int>,
    /** 合并后替代这些行的规范文本。 */
    val canonical: String,
)

internal data class ConsolidatePlan(
    val merges: List<ConsolidateMerge>,
)

internal object MemoryConsolidate {

    /** 应用合并计划：返回新的行列表。按组首行位置降序处理，避免插入导致索引错位。 */
    fun apply(plan: ConsolidatePlan, existingLines: List<String>): List<String> {
        val result = existingLines.toMutableList()
        plan.merges
            .sortedByDescending { (it.sourceLines.minOrNull() ?: 0) }
            .forEach { merge ->
                val start = (merge.sourceLines.minOrNull() ?: return@forEach) - 1
                val end = (merge.sourceLines.maxOrNull() ?: return@forEach) - 1
                if (start < 0 || end >= result.size || start > end) return@forEach
                if (merge.canonical.isBlank()) {
                    result.subList(start, end + 1).clear()
                } else {
                    result.subList(start, end + 1).clear()
                    result.add(start, merge.canonical)
                }
            }
        return result
    }

    /** 把每行按 [key] 归一化后分组（供 LLM 前/或纯逻辑做重叠检测的辅助）。 */
    fun normalizeLine(line: String): String {
        val t = line.trim().removePrefix("- ").removePrefix("• ").replace("[沉淀]", "")
        val punct = " \t，。！？；：、,.!?;:()【】[]\"'“”"
        return t.lowercase().filter { it !in punct }
    }
}
