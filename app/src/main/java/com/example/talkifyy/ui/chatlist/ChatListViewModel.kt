package com.example.talkifyy.ui.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.talkifyy.data.remote.ChatRemoteDataSource
import com.example.talkifyy.data.repo.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatListUiState(
    val chats: List<ChatRepository.ChatSummary> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null
)

class ChatListViewModel(
    private val repo: ChatRepository,
    private val remote: ChatRemoteDataSource,
    private val me: String
) : ViewModel() {

    val uiState: StateFlow<ChatListUiState> =
        repo.chatSummaries
            .map { ChatListUiState(chats = it, loading = false) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatListUiState())

    fun deleteChatForMe(chatId: String) {
        viewModelScope.launch(Dispatchers.IO) { repo.deleteForMe(chatId) }
    }

    // Optional redundancy: clear local flags once new messages arrive
    init {
        viewModelScope.launch(Dispatchers.IO) {
            remote.observeChatroomsFor(me).collectLatest { rooms ->
                repo.clearAutoRestoresIfNeeded(rooms)
            }
        }
    }
}

