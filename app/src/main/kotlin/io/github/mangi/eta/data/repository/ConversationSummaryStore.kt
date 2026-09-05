package io.github.mangi.eta.data.repository

import android.content.Context
import io.github.mangi.eta.data.db.ConversationSummaryEntity
import io.github.mangi.eta.data.db.EtaDatabase

/** 长对话滚动摘要持久化（P2）：按会话存取被裁剪轮次的压缩摘要。 */
internal object ConversationSummaryStore {

    /** memory_search 最多扫描的摘要条数（按更新时间倒序取最近）。 */
    private const val MAX_SEARCHED_SUMMARIES = 100

    @Volatile
    private var applicationContext: Context? = null

    fun init(context: Context) {
        if (applicationContext == null) {
            applicationContext = context.applicationContext
        }
    }

    private fun context(): Context = checkNotNull(applicationContext) {
        "ConversationSummaryStore.init(context) must be called in Application.onCreate()"
    }

    private fun dao() = EtaDatabase.get(context()).conversationSummaryDao()

    suspend fun summary(conversationId: String): String? =
        dao().summary(conversationId)?.summary

    suspend fun summarizedTurns(conversationId: String): Int =
        dao().summary(conversationId)?.summarizedTurns ?: 0

    /** 单次查询同时取回摘要与已覆盖轮数，避免两趟 DB 读。 */
    suspend fun summaryEntry(conversationId: String): ConversationSummaryEntity? =
        dao().summary(conversationId)

    suspend fun upsert(
        conversationId: String,
        summary: String,
        summarizedTurns: Int,
    ) {
        dao().upsert(
            ConversationSummaryEntity(
                conversationId = conversationId,
                summary = summary,
                summarizedTurns = summarizedTurns,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun all(limit: Int = MAX_SEARCHED_SUMMARIES): List<Pair<String, String>> =
        dao().all(limit).map { it.conversationId to it.summary }
}
