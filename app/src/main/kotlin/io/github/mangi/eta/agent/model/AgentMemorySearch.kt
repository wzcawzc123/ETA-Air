package io.github.mangi.eta.agent.model

/** 会话记忆检索（P4）：在会话摘要与 MEMORY.md 标题中做关键词检索（纯逻辑，可单测）。 */
internal object AgentMemorySearch {

    const val MAX_QUERY_CHARS = 120
    const val MAX_HITS = 8
    const val MAX_SNIPPET_CHARS = 160

    /** 对外展示的来源名：不暴露内部会话 ID。 */
    const val SOURCE_SUMMARY = "历史会话摘要"
    const val SOURCE_MEMORY = "核心记忆"

    data class Hit(
        val source: String,
        val snippet: String,
        val score: Int,
        /** 该命中来自哪个会话（摘要类命中才有），让 agent 区分"当前会话"与"过去会话"，避免误归因。 */
        val conversationLabel: String? = null,
    )

    /** 中文长词补字符 n-gram，降低整句成词导致的召回为 0。 */
    private fun cjkNgrams(token: String): List<String> {
        if (token.length < 3) return emptyList()
        val chars = token.toCharArray()
        val grams = mutableListOf<String>()
        for (i in 0 until chars.size - 1) grams += chars[i].toString() + chars[i + 1]
        for (i in 0 until chars.size - 2) grams += chars[i].toString() + chars[i + 1] + chars[i + 2]
        return grams
    }

    /** 查询词拆分：按空白/标点切分，忽略 1 字符词；对中文长词补充字符 n-gram 提升召回。 */
    fun queryTerms(query: String): List<String> {
        val tokens = query.trim()
            .take(MAX_QUERY_CHARS)
            .split(Regex("[\\s，。！？；：、,.!?;:]+"))
            .map { it.trim() }
            .filter { it.length >= 2 }
        return buildList {
            addAll(tokens)
            addAll(tokens.flatMap(::cjkNgrams))
        }.distinct()
    }

    /** 在候选文本上打分：命中词数 × 词长。 */
    fun scoreText(text: String, terms: List<String>): Int {
        if (terms.isEmpty()) return 0
        val lowered = text.lowercase()
        return terms.sumOf { term ->
            if (term.lowercase() in lowered) term.length else 0
        }
    }

    /** 在摘要列表与 MEMORY.md 全文中检索，返回按分数降序的去重结果。 */
    fun search(
        query: String,
        summaries: List<Pair<String, String>>,
        memoryContent: String,
    ): List<Hit> {
        val terms = queryTerms(query)
        if (terms.isEmpty()) return emptyList()

        val hits = mutableListOf<Hit>()
        summaries.forEach { (conversationId, summary) ->
            val score = scoreText(summary, terms)
            if (score > 0) {
                hits += Hit(
                    source = SOURCE_SUMMARY,
                    snippet = summary.trim().take(MAX_SNIPPET_CHARS),
                    score = score,
                    conversationLabel = conversationId,
                )
            }
        }
        memoryContent.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                val score = scoreText(line, terms)
                if (score > 0) {
                    hits += Hit(
                        source = SOURCE_MEMORY,
                        snippet = line.take(MAX_SNIPPET_CHARS),
                        score = score,
                    )
                }
            }
        return hits
            .distinctBy { it.source + it.conversationLabel + it.snippet }
            .sortedByDescending { it.score }
            .take(MAX_HITS)
    }
}
