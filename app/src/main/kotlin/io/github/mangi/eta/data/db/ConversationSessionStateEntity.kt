package io.github.mangi.eta.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** 当前会话的结构化状态（SessionState 渲染文本）：确定性记住"做到哪/决定过什么"。 */
@Entity(
    tableName = "conversation_session_state",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class ConversationSessionStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    /** SessionStateCodec.render() 的结果（目标/已完成/待办/决定）。 */
    @ColumnInfo(name = "session_state") val sessionState: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
