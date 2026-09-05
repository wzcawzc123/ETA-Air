package io.github.mangi.eta.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** 长对话滚动摘要：按会话保存被裁剪早期轮次的压缩摘要（P2）。 */
@Entity(
    tableName = "conversation_summaries",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class ConversationSummaryEntity(
    @PrimaryKey
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    val summary: String,
    @ColumnInfo(name = "summarized_turns") val summarizedTurns: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
