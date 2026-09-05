package io.github.mangi.eta.agent.model

/**
 * BERT WordPiece 分词器（纯 Kotlin，可单测）。用于 bge-small-zh-v1.5。
 * 从 vocab.txt 加载词表，按 BERT 规则：中文逐字切分、按空白/标点拆词、WordPiece 子词（##）。
 * tokenizer_config: do_lower_case=false、tokenize_chinese_chars=true、max_length=512。
 */
internal data class TokenizedInput(
    val inputIds: IntArray,
    val attentionMask: IntArray,
    val tokenTypeIds: IntArray,
) {
    val length: Int get() = inputIds.size
}

internal class BertWordPieceTokenizer(
    vocab: List<String>,
    private val maxLength: Int = 512,
) {
    private val tokenToId: Map<String, Int>
    val padId: Int
    val clsId: Int
    val sepId: Int
    val maskId: Int
    val unkId: Int

    init {
        require(vocab.isNotEmpty()) { "vocab 不能为空" }
        tokenToId = vocab.mapIndexed { i, t -> t to i }.toMap()
        padId = tokenToId["[PAD]"] ?: 0
        clsId = tokenToId["[CLS]"] ?: 101
        sepId = tokenToId["[SEP]"] ?: 102
        maskId = tokenToId["[MASK]"] ?: 103
        unkId = tokenToId["[UNK]"] ?: 100
    }

    fun encode(text: String): TokenizedInput {
        val ids = ArrayList<Int>(maxLength)
        ids += clsId
        for (word in basicTokens(text)) {
            val pieces = wordPiece(word)
            for (p in pieces) {
                if (ids.size >= maxLength - 1) break
                ids += tokenToId[p] ?: unkId
            }
            if (ids.size >= maxLength - 1) break
        }
        ids += sepId
        val len = ids.size
        val inputIds = IntArray(maxLength) { if (it < len) ids[it] else padId }
        val mask = IntArray(maxLength) { if (it < len) 1 else 0 }
        val typeIds = IntArray(maxLength) { 0 }
        return TokenizedInput(inputIds, mask, typeIds)
    }

    /** 基础切分：空白分隔；中文逐字；其它连续字符（含英文/数字/标点）聚成一个词。 */
    private fun basicTokens(text: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        fun flush() {
            if (cur.isNotEmpty()) { out += cur.toString(); cur.clear() }
        }
        for (ch in text) {
            when {
                ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r' -> flush()
                isCjk(ch) -> { flush(); out += ch.toString() }
                ch.isLetterOrDigit() || ch == '\'' || ch == '-' -> cur.append(ch)
                else -> { flush(); out += ch.toString() } // 其它标点/符号作为独立 token
            }
        }
        flush()
        return out
    }

    private fun isCjk(ch: Char): Boolean =
        (ch.code in 0x4E00..0x9FFF) || (ch.code in 0x3400..0x4DBF) ||
            (ch.code in 0xF900..0xFAFF) || (ch.code in 0x3000..0x303F && ch != ' ')

    /** WordPiece：从前往后贪心找最长子词；首段无 ##、后续段 ## 前缀。失败回退 [UNK]。 */
    private fun wordPiece(word: String): List<String> {
        if (word.isEmpty()) return emptyList()
        val out = mutableListOf<String>()
        var start = 0
        while (start < word.length) {
            var end = word.length
            var best: String? = null
            while (end > start) {
                val sub = word.substring(start, end)
                val cand = if (start == 0) sub else "##$sub"
                if (tokenToId.containsKey(cand)) { best = cand; break }
                end--
            }
            if (best == null) {
                out += "[UNK]"
                start++
            } else {
                out += best!!
                start = end
            }
        }
        return out
    }
}
