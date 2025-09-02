package com.example.talkifyy.adapter;

import java.util.List;

public interface UnsendMessageListener {
    void onUnsendMessage(String messageId, String chatroomId);
    void onUnsendMultipleMessages(List<String> messageIds, String chatroomId);
    void onSelectionModeChanged(boolean isSelectionMode, int selectedCount);
}
