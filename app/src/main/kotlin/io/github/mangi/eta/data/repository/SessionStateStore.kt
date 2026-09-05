package io.github.mangi.eta.data.repository

import android.content.Context
import io.github.mangi.eta.agent.model.SessionState
import io.github.mangi.eta.agent.model.SessionStateCodec
import io.github.mangi.eta.data.db.ConversationSessionStateEntity
import io.github.mangi.eta.data.db.EtaDatabase

/** 当前会话结构状态（SessionState）的确定持久化存取：会话状态工具与注入共用。 */
internal object SessionStateStore {

    @Volatile
    private var applicationContext: Context? = null

    fun init(context: Context) {
        if (applicationContext == null) {
            applicationContext = context.applicationContext
        }
    }

    private fun context(): Context = checkNotNull(applicationContext) {
        "SessionStateStore.init(context) must be called in Application.onCreate()"
    }

    private fun dao() = EtaDatabase.get(context()).conversationSessionStateDao()

    suspend fun get(conversationId: String): SessionState? =
        dao().get(conversationId)?.sessionState?.let(SessionStateCodec::parse)

    suspend fun set(conversationId: String, state: SessionState) {
        if (state.isEmpty) {
            dao().delete(conversationId)
            return
        }
        dao().upsert(
            ConversationSessionStateEntity(
                conversationId = conversationId,
                sessionState = SessionStateCodec.render(state),
                updatedAt = System.currentTimeMillis(),
            )
        )
    }
}
