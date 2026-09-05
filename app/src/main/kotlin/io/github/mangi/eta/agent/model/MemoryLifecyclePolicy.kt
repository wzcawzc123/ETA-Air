package io.github.mangi.eta.agent.model

/**
 * 记忆生命周期策略（纯逻辑，可 kotlinc 单测）。
 *
 * 记忆不该无限堆积：长期未被使用/检索的视为噪音，按使用相关性衰减；超过 TTL 兜底清理。
 * 这样记忆"会更新、会过期"，而非只增不减。实际的删除/更新由存储层按时间/权重执行，此处只判定。
 */
internal object MemoryLifecyclePolicy {

    /** 原子记忆默认保留 180 天。 */
    const val DEFAULT_ATOM_TTL_MS = 180L * 24 * 60 * 60 * 1000

    /** 场景记忆默认保留 365 天。 */
    const val DEFAULT_SCENARIO_TTL_MS = 365L * 24 * 60 * 60 * 1000

    /** 被检索到并被采用时的权重提升。 */
    const val USAGE_BOOST = 1.0

    /** 未使用时的每日衰减量。 */
    const val DECAY_PER_DAY = 0.05

    /** 是否已过期：[updatedAt] 距今超过 [ttlMs]。 */
    fun isExpired(updatedAt: Long, now: Long, ttlMs: Long = DEFAULT_ATOM_TTL_MS): Boolean =
        (now - updatedAt) > ttlMs

    /** 被采用后的新权重（夹到 [0,1]）。 */
    fun weightAfterUse(weight: Double): Double = (weight + USAGE_BOOST).coerceIn(0.0, 1.0)

    /** 经过 [elapsedMs] 未使用后的衰减权重（夹到 [0,1]）。 */
    fun weightAfterDecay(weight: Double, elapsedMs: Long): Double {
        val days = elapsedMs / (24.0 * 60 * 60 * 1000)
        return (weight - DECAY_PER_DAY * days).coerceIn(0.0, 1.0)
    }

    /** 权重低于该值视为噪音（可淘汰）。 */
    fun isNoise(weight: Double): Boolean = weight < 0.05
}
