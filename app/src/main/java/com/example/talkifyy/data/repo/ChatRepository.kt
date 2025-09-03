package com.example.talkifyy.data.repo

import com.example.talkifyy.data.local.LocalDeletionStore
import com.example.talkifyy.data.remote.ChatRemoteDataSource
import com.example.talkifyy.data.remote.models.Chatroom
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Merges Firestore chatrooms with local Delete-for-Me state.
 * - Hides chats whose lastMessageTimestampMs <= local deletion timestamp
 * - Automatically clears the local deletion when a newer message arrives (auto-restore)
 */
class ChatRepository(
    private val me: String,
    private val remote: ChatRemoteDataSource,
    private val local: LocalDeletionStore,
    private val io: CoroutineDispatcher
) {
    data class ChatSummary(
        val id: String,
        val title: String,
        val lastMessage: String,
        val lastMessageTimestampMs: Long
    )

    val chatSummaries: Flow<List<ChatSummary>> =
        combine(remote.observeChatroomsFor(me), local.chatDeletionsFlow()) { rooms, deletions ->
            rooms.mapNotNull { room ->
                val deletedAt = deletions[room.id]
                val shouldHide = deletedAt != null && room.lastMessageTimestampMs <= deletedAt
                if (!shouldHide) {
                    ChatSummary(
                        id = room.id,
                        title = otherUserTitle(room, me),
                        lastMessage = room.lastMessage,
                        lastMessageTimestampMs = room.lastMessageTimestampMs
                    )
                } else null
            }
        }

    private fun otherUserTitle(room: Chatroom, me: String): String =
        room.userIds.firstOrNull { it != me } ?: "Chat"

    suspend fun deleteForMe(chatId: String) = withContext(io) {
        local.markChatDeleted(chatId)
    }

    suspend fun clearAutoRestoresIfNeeded(latestRooms: List<Chatroom>) = withContext(io) {
        val deletions = local.chatDeletionsFlow().first()
        latestRooms.forEach { room ->
            val ts = deletions[room.id]
            if (ts != null && room.lastMessageTimestampMs > ts) {
                local.clearChatDeletion(room.id)
            }
        }
    }
}

