package com.example.talkifyy.data.remote

import com.example.talkifyy.data.remote.models.Chatroom
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

interface ChatRemoteDataSource {
    fun observeChatroomsFor(userId: String): Flow<List<Chatroom>>
}

class FirestoreChatRemoteDataSource(
    private val db: FirebaseFirestore
) : ChatRemoteDataSource {

    override fun observeChatroomsFor(userId: String): Flow<List<Chatroom>> = callbackFlow {
        val ref = db.collection("chatrooms")
            .whereArrayContains("userIds", userId)
            .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)

        val reg = ref.addSnapshotListener { snap, _ ->
            val rooms = snap?.documents?.map { d ->
                val ts = d.getTimestamp("lastMessageTimestamp") ?: Timestamp(0, 0)
                Chatroom(
                    id = d.id,
                    userIds = d.get("userIds") as? List<String> ?: emptyList(),
                    lastMessage = d.getString("lastMessage") ?: "",
                    lastMessageSenderId = d.getString("lastMessageSenderId") ?: "",
                    lastMessageTimestampMs = ts.toDate().time
                )
            } ?: emptyList()
            trySend(rooms)
        }
        awaitClose { reg.remove() }
    }
}

