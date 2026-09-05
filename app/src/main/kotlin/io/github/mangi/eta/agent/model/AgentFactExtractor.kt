package io.github.mangi.eta.agent.model

/** 单条"覆盖/更新"：startLine（1-based，指向一条沉淀条目行）用 [content] 整行替换。 */
internal data class FactWriteUpdate(val startLine: Int, val content: String)

/** 一次沉淀的写入计划：additions 追加新事实；updates 覆盖已有行；noop 已存在等价→跳过。 */
internal data class FactWritePlan(
    val additions: List<String>,
    val updates: List<FactWriteUpdate>,
    val noop: List<String>,
) {
    val isEmpty: Boolean get() = additions.isEmpty() && updates.isEmpty()
}

/** 自动事实沉淀（P3）：从一次对话中提取长期稳定事实，并对照现有记忆行判定 新增/覆盖/跳过。
 * 用 LLM 语义判重而非字符启发式，从根上避免"同义改写/部分重叠"反复沉淀成重复条目。 */
internal fun interface AgentFactExtractor {
    suspend fun extractPlan(
        userText: String,
        assistantText: String,
        existingMemoryLines: List<String>,
    ): FactWritePlan
}

/** 事实提取的后处理纯逻辑（可 kotlinc 单测）。 */
internal object AgentFactRules {

    const val MAX_FACTS_PER_RUN = 5
    const val MAX_FACT_CHARS = 400
    const val MAX_INPUT_CHARS = 4_000

    fun buildPrompt(
        userText: String,
        assistantText: String,
        maxFacts: Int = MAX_FACTS_PER_RUN,
    ): String = buildString {
        appendLine(
            "从以下对话中提取 0-$maxFacts 条长期稳定的用户事实" +
                "（名字、身份、偏好、关系、持续项目、环境与工具配置、重要背景）。"
        )
        appendLine("只提取明确陈述的稳定信息，不要推断；不要提取密钥、验证码、临时任务或一次性信息。")
        appendLine("若新事实与已有记忆冲突，用“更正：…”表达，不要保留两者矛盾版本。")
        appendLine("每行输出一条事实，以 \"- \" 开头。")
        appendLine()
        appendLine("用户：${userText.take(MAX_INPUT_CHARS)}")
        if (assistantText.isNotBlank()) {
            appendLine("助手：${assistantText.take(MAX_INPUT_CHARS)}")
        }
    }.trim()

    /** 明显是"未提取到任何事实"的负向结论模板，不应沉淀进记忆库。 */
    private val NEGATIVE_FACTS = listOf(
        "暂无长期稳定事实",
        "无长期稳定",
        "无可用事实",
        "无事实",
        "没有提取到明确陈述的用户事实",
        "没有提取到明确",
        "未提取到",
        "没有明确陈述",
        "没有明确",
        "不存在",
        "无法提取",
        "没有发现",
        "无信息",
        "无长期",
        "无可用",
    )

    fun parseFacts(text: String, maxFacts: Int = MAX_FACTS_PER_RUN): List<String> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("- ") || it.startsWith("• ") }
            .map { it.removePrefix("- ").removePrefix("• ").trim() }
            .filter { it.length >= 3 }
            // 负向结论模板在 LLM 输出中是"独立整行"（如"暂无长期稳定事实"），
            // 只用行首匹配，避免把"用户没有明确偏好"这类真实事实当作负向结论误删。
            .filter { fact -> NEGATIVE_FACTS.none { fact.startsWith(it) } }
            .distinct()
            .take(maxFacts)
            .toList()

    /** 提取稳定事实 + 对照已有记忆行做 语义判重 的一体化 prompt。
     * 一次 LLM 调用同时完成"抽取 + 分类(add/update/noop)"，避免字符启发式漏掉同义改写/部分重叠。 */
    fun buildPlanPrompt(
        userText: String,
        assistantText: String,
        existingMemoryLines: List<String>,
        maxFacts: Int = MAX_FACTS_PER_RUN,
    ): String = buildString {
        appendLine(
            "从以下对话中提取 0-$maxFacts 条长期稳定的用户事实" +
                "（名字、身份、偏好、关系、持续项目、环境与工具配置、重要背景）。"
        )
        appendLine("只提取明确陈述的稳定、跨会话仍有价值的信息；不要推断；不要提取密钥/验证码/临时任务/一次性信息。")
        appendLine("每条事实必须对照下面【已存在记忆行】判定，不能只输出事实而忽略判重：")
        appendLine("  - ADD：与所有已存在记忆行都不是同义/重叠/被覆盖，是真正新增")
        appendLine("  - UPDATE <行号>：与某一行同义、部分重叠或更全面，用你的规范文本替换那一行（行号见【已存在记忆行】）")
        appendLine("  - SKIP：某行已等价涵盖 / 不是稳定事实 / 细节更少")
        appendLine("输出格式（每行一条）：")
        appendLine("ADD: 事实")
        appendLine("UPDATE <行号>: 规范文本")
        appendLine("SKIP")
        appendLine("明确要求：同义或重叠的事实必须用 UPDATE 或 SKIP，禁止再加入一条重复的 ADD；UPDATE 只针对【沉淀】条目行，不要改动标题/分隔行。")
        if (existingMemoryLines.isNotEmpty()) {
            appendLine()
            appendLine("【已存在记忆行】")
            existingMemoryLines.forEachIndexed { idx, line ->
                val t = line.trim().removePrefix("- ").removePrefix("• ").replace("[沉淀]", "").trim()
                appendLine("${idx + 1}: $t")
            }
        }
        appendLine()
        appendLine("用户：${userText.take(MAX_INPUT_CHARS)}")
        if (assistantText.isNotBlank()) {
            appendLine("助手：${assistantText.take(MAX_INPUT_CHARS)}")
        }
    }.trim()

    /** 解析 LLM 的语义判重计划。行号需在 [1, existingLineCount] 内，越界/格式错直接丢弃（不求完美，只求安全）。 */
    fun parsePlan(text: String, existingLineCount: Int, maxFacts: Int = MAX_FACTS_PER_RUN): FactWritePlan {
        val additions = mutableListOf<String>()
        val updates = mutableListOf<FactWriteUpdate>()
        val noop = mutableListOf<String>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("ADD:", true) || line.startsWith("ADD ", true) -> {
                    // 去掉 "ADD" 前缀及其后的 ':'/' '，稳健处理 "ADD: x" / "ADD x" / "add:x"
                    val fact = line.substring(3).trimStart().removePrefix(":").trim()
                        .removePrefix("- ").removePrefix("• ")
                    if (fact.length >= 3) additions += fact
                }
                line.startsWith("UPDATE", true) -> {
                    val rest = line.substring("UPDATE".length)
                    val colon = rest.indexOf(':')
                    if (colon > 0) {
                        val no = rest.substring(0, colon).trim().toIntOrNull()
                        val content = rest.substring(colon + 1).trim()
                        if (no != null && no in 1..existingLineCount && content.length >= 3) {
                            updates += FactWriteUpdate(no, content)
                        }
                    }
                }
                line.startsWith("SKIP", true) -> noop += line
            }
        }
        return FactWritePlan(
            additions = additions.take(maxFacts),
            updates = updates,
            noop = noop,
        )
    }

    /** 与 MEMORY.md 已有内容做归一化 + 语义近似去重 + 长度/数量预算。 */
    fun dedupeAndClamp(
        facts: List<String>,
        existingMemory: String,
        maxFacts: Int = MAX_FACTS_PER_RUN,
    ): List<String> {
        val memoryNorms = existingMemory.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map(::normalize)
            .filter { it.isNotEmpty() }
            .toList()
        val result = mutableListOf<String>()
        for (fact in facts) {
            val trimmed = fact.trim().take(MAX_FACT_CHARS)
            if (trimmed.length < 3) continue
            if (NEGATIVE_FACTS.any { trimmed.startsWith(it) }) continue
            val norm = normalize(trimmed)
            if (memoryNorms.any { similar(norm, it) }) continue
            if (result.any { similar(norm, normalize(it)) }) continue
            result += trimmed
            if (result.size >= maxFacts) break
        }
        return result
    }

    /** 归一化用于比较：去空白与常见中英文标点、统一小写。 */
    private fun normalize(text: String): String {
        val punct = " \t，。！？；：、,.!?;:()【】[]\"'“”"
        return text.lowercase().filter { it !in punct }
    }

    private fun charBigrams(s: String): Set<String> =
        if (s.length < 2) setOf(s) else (0 until s.length - 1).map { s.substring(it, it + 2) }.toSet()

    /** 语义近似：完全/包含相等，或字符二元组 Jaccard 高且长度接近。 */
    private fun similar(a: String, b: String): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        if (a.length >= 4 && b.length >= 4 && (a.contains(b) || b.contains(a))) return true
        val ba = charBigrams(a)
        val bb = charBigrams(b)
        if (ba.isEmpty() || bb.isEmpty()) return false
        val jaccard = ba.intersect(bb).size.toDouble() / ba.union(bb).size
        val lenRatio = minOf(a.length, b.length).toDouble() / maxOf(a.length, b.length)
        return jaccard >= 0.6 && lenRatio >= 0.5
    }
}

internal class LlmAgentFactExtractor(
    private val config: AgentModelClient.ModelConfig,
    private val provider: AgentProviderClient,
) : AgentFactExtractor {

    override suspend fun extractPlan(
        userText: String,
        assistantText: String,
        existingMemoryLines: List<String>,
    ): FactWritePlan {
        val result = LlmTextCompletion.complete(
            config = config,
            provider = provider,
            systemPrompt = "你是记忆提取与去重助手，只做稳定的记忆沉淀。",
            userText = AgentFactRules.buildPlanPrompt(userText, assistantText, existingMemoryLines),
            maxResultChars = 3_000,
        )
        return AgentFactRules.parsePlan(result, existingMemoryLines.size)
    }
}
