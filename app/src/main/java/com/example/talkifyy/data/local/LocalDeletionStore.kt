package com.example.talkifyy.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.deletionDataStore by preferencesDataStore(name = "chat_deletions")

/**
 * Local per-device storage for "Delete for Me" state.
 * - Chat-level deletion timestamp per chatId (used to hide a conversation until a newer message arrives)
 * - Message-level hidden IDs to hide specific messages in a thread (optional)
 */
class LocalDeletionStore(private val appContext: Context) {

    private fun chatKey(chatId: String) = longPreferencesKey("deleted_chat_ts_$chatId")
    private fun msgKey(chatId: String) = stringSetPreferencesKey("hidden_msgs_$chatId")

    // Chat-level
    suspend fun markChatDeleted(chatId: String, atMillis: Long = System.currentTimeMillis()) {
        appContext.deletionDataStore.edit { it[chatKey(chatId)] = atMillis }
    }
    suspend fun clearChatDeletion(chatId: String) {
        appContext.deletionDataStore.edit { it.remove(chatKey(chatId)) }
    }
    suspend fun getChatDeletionTs(chatId: String): Long? =
        appContext.deletionDataStore.data.first()[chatKey(chatId)]

    fun chatDeletionsFlow(): Flow<Map<String, Long>> =
        appContext.deletionDataStore.data.map { prefs ->
            prefs.asMap().mapNotNull { (k, v) ->
                val name = (k as? Preferences.Key<*>)?.name ?: return@mapNotNull null
                if (name.startsWith("deleted_chat_ts_")) {
                    name.removePrefix("deleted_chat_ts_") to (v as Long)
                } else null
            }.toMap()
        }

    // Message-level
    suspend fun hideMessage(chatId: String, messageId: String) {
        appContext.deletionDataStore.edit { prefs ->
            val cur = prefs[msgKey(chatId)] ?: emptySet()
            prefs[msgKey(chatId)] = (cur + messageId)
        }
    }
    suspend fun unhideMessage(chatId: String, messageId: String) {
        appContext.deletionDataStore.edit { prefs ->
            val cur = prefs[msgKey(chatId)] ?: emptySet()
            prefs[msgKey(chatId)] = (cur - messageId)
        }
    }
    fun hiddenMessagesFlow(chatId: String): Flow<Set<String>> =
        appContext.deletionDataStore.data.map { prefs -> prefs[msgKey(chatId)] ?: emptySet() }
}

