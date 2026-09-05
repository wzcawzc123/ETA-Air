package io.github.mangi.eta.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
internal interface ConversationSummaryDao {
    @Query("SELECT * FROM conversation_summaries WHERE conversation_id = :conversationId")
    suspend fun summary(conversationId: String): ConversationSummaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ConversationSummaryEntity)

    @Query("DELETE FROM conversation_summaries WHERE conversation_id = :conversationId")
    suspend fun delete(conversationId: String)

    @Query("SELECT * FROM conversation_summaries ORDER BY updated_at DESC LIMIT :limit")
    suspend fun all(limit: Int): List<ConversationSummaryEntity>
}
