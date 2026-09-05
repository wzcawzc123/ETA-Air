package io.github.mangi.eta.agent.model

import kotlin.math.sqrt

/** 纯向量数学（可单测）：余弦相似度 + L2 归一化。 */
internal object VectorMath {

    fun normalize(v: FloatArray): FloatArray {
        var norm = 0f
        for (x in v) norm += x * x
        norm = sqrt(norm)
        if (norm <= 1e-8f) return FloatArray(v.size)
        val out = FloatArray(v.size)
        for (i in v.indices) out[i] = v[i] / norm
        return out
    }

    /** 余弦相似度：要求两个向量都已 L2 归一化或长度一致；未归一化也会自动按点积/模长算。 */
    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || a.size != b.size) return 0f
        var dot = 0f
        var na = 0f
        var nb = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        if (na <= 1e-8f || nb <= 1e-8f) return 0f
        return dot / (sqrt(na) * sqrt(nb))
    }

    /** 求数组中与目标向量最相似的 topK 个下标（降序返回）。tie 按原索引。 */
    fun topK(target: FloatArray, candidates: List<FloatArray>, k: Int): List<Int> {
        if (candidates.isEmpty() || k <= 0) return emptyList()
        return candidates.indices
            .map { it to cosine(target, candidates[it]) }
            .sortedWith(compareByDescending<Pair<Int, Float>> { it.second }.thenBy { it.first })
            .take(k)
            .map { it.first }
    }
}
