package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface KmtMailDao {

    // --- Messages ---
    @Query("SELECT * FROM messages WHERE emailAddress = :emailAddress ORDER BY timestamp DESC")
    fun getMessagesForEmail(emailAddress: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessageById(id: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("UPDATE messages SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: String)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: String)

    @Query("DELETE FROM messages WHERE id NOT LIKE '1secmail_%' AND id NOT LIKE 'mailtm_%'")
    suspend fun purgeNonApiMessages()

    @Query("DELETE FROM messages WHERE emailAddress = :emailAddress")
    suspend fun clearMessagesForEmail(emailAddress: String)

    // --- Email History ---
    @Query("SELECT * FROM email_history ORDER BY createdAt DESC")
    fun getAllHistory(): Flow<List<EmailHistoryEntity>>

    @Query("SELECT * FROM email_history WHERE isCurrent = 1 LIMIT 1")
    suspend fun getCurrentEmail(): EmailHistoryEntity?

    @Query("SELECT * FROM email_history WHERE isCurrent = 1 LIMIT 1")
    fun getCurrentEmailFlow(): Flow<EmailHistoryEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmailHistory(email: EmailHistoryEntity)

    @Query("UPDATE email_history SET isCurrent = 0")
    suspend fun resetCurrentFlags()

    @Query("UPDATE email_history SET isCurrent = 1 WHERE address = :address")
    suspend fun setCurrentEmail(address: String)

    @Query("DELETE FROM email_history WHERE address = :address")
    suspend fun deleteHistoryAddress(address: String)
}
