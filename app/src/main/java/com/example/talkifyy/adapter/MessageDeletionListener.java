package com.example.talkifyy.adapter;

import java.util.List;

/**
 * Interface for handling WhatsApp-style message deletion operations
 * with both "Delete for Me" and "Delete for Everyone" functionality
 */
public interface MessageDeletionListener {
    
    /**
     * Delete a single message for the current user only (local deletion)
     * @param messageId Message ID to delete locally
     * @param chatroomId Chatroom ID containing the message
     */
    void onDeleteForMe(String messageId, String chatroomId);
    
    /**
     * Delete a single message for everyone in the chat
     * @param messageId Message ID to delete for everyone
     * @param chatroomId Chatroom ID containing the message
     */
    void onDeleteForEveryone(String messageId, String chatroomId);
    
    /**
     * Delete multiple messages for the current user only (local deletion)
     * @param messageIds List of message IDs to delete locally
     * @param chatroomId Chatroom ID containing the messages
     */
    void onDeleteMultipleForMe(List<String> messageIds, String chatroomId);
    
    /**
     * Delete multiple messages for everyone in the chat
     * @param messageIds List of message IDs to delete for everyone
     * @param chatroomId Chatroom ID containing the messages
     */
    void onDeleteMultipleForEveryone(List<String> messageIds, String chatroomId);
    
    /**
     * Called when selection mode is toggled on/off
     * @param isSelectionMode Whether selection mode is active
     * @param selectedCount Number of currently selected messages
     */
    void onSelectionModeChanged(boolean isSelectionMode, int selectedCount);
    
    /**
     * Called to check if current user is admin in group chats
     * @param chatroomId Chatroom ID to check
     * @return true if current user is admin (for groups), always true for 1-on-1 chats
     */
    boolean isUserAdmin(String chatroomId);
    
    /**
     * Get the recall window in hours for "Delete for Everyone"
     * @return recall window in hours (default 48 hours)
     */
    long getRecallWindowHours();
}
