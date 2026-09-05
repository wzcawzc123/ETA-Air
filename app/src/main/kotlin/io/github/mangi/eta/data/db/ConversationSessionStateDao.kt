package io.github.mangi.eta.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
internal interface ConversationSessionStateDao {
    @Query("SELECT * FROM conversation_session_state WHERE conversation_id = :conversationId")
    suspend fun get(conversationId: String): ConversationSessionStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ConversationSessionStateEntity)

    @Query("DELETE FROM conversation_session_state WHERE conversation_id = :conversationId")
    suspend fun delete(conversationId: String)
}
