package com.example.talkifyy.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

/**
 * Utility class for managing local deletions (messages and chats)
 * that only affect the current user's device without syncing to other users.
 * Uses SharedPreferences for reliable, immediate storage operations.
 */
public class LocalDeletionUtil {
    private static final String TAG = "LocalDeletionUtil";
    private static final String PREFS_NAME = "local_deletions";
    private static final String KEY_DELETED_MESSAGES = "deleted_messages_";
    private static final String KEY_DELETED_CHATS = "deleted_chats";
    private static final String KEY_CHAT_DELETION_TIMESTAMPS = "chat_deletion_timestamps";
    
    // Message deletion methods
    
    /**
     * Mark a message as locally deleted for the current user
     * @param context Application context
     * @param chatroomId Chat room ID containing the message
     * @param messageId Message ID to mark as deleted
     */
    public static void markMessageAsLocallyDeleted(Context context, String chatroomId, String messageId) {
        Log.d(TAG, "Marking message as locally deleted: " + messageId + " in chatroom: " + chatroomId);
        
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> deletedMessages = new HashSet<>(prefs.getStringSet(KEY_DELETED_MESSAGES + chatroomId, new HashSet<>()));
        
        deletedMessages.add(messageId);
        
        prefs.edit()
                .putStringSet(KEY_DELETED_MESSAGES + chatroomId, deletedMessages)
                .apply();
        
        Log.d(TAG, "Message marked as deleted locally: " + messageId);
    }
    
    /**
     * Mark multiple messages as locally deleted for the current user
     * @param context Application context
     * @param chatroomId Chat room ID containing the messages
     * @param messageIds Set of message IDs to mark as deleted
     */
    public static void markMessagesAsLocallyDeleted(Context context, String chatroomId, Set<String> messageIds) {
        Log.d(TAG, "Marking " + messageIds.size() + " messages as locally deleted in chatroom: " + chatroomId);
        
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> deletedMessages = new HashSet<>(prefs.getStringSet(KEY_DELETED_MESSAGES + chatroomId, new HashSet<>()));
        
        deletedMessages.addAll(messageIds);
        
        prefs.edit()
                .putStringSet(KEY_DELETED_MESSAGES + chatroomId, deletedMessages)
                .apply();
        
        Log.d(TAG, messageIds.size() + " messages marked as deleted locally");
    }
    
    /**
     * Check if a message is locally deleted for the current user
     * @param context Application context
     * @param chatroomId Chat room ID containing the message
     * @param messageId Message ID to check
     * @return true if message is locally deleted, false otherwise
     */
    public static boolean isMessageLocallyDeleted(Context context, String chatroomId, String messageId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> deletedMessages = prefs.getStringSet(KEY_DELETED_MESSAGES + chatroomId, new HashSet<>());
        
        boolean isDeleted = deletedMessages.contains(messageId);
        if (isDeleted) {
            Log.d(TAG, "Message is locally deleted: " + messageId);
        }
        
        return isDeleted;
    }
    
    /**
     * Get all locally deleted message IDs for a specific chatroom
     * @param context Application context
     * @param chatroomId Chat room ID
     * @return Set of deleted message IDs
     */
    public static Set<String> getLocallyDeletedMessages(Context context, String chatroomId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return new HashSet<>(prefs.getStringSet(KEY_DELETED_MESSAGES + chatroomId, new HashSet<>()));
    }
    
    /**
     * Remove a message from local deletion (if needed for restore functionality)
     * @param context Application context
     * @param chatroomId Chat room ID containing the message
     * @param messageId Message ID to restore
     */
    public static void restoreLocallyDeletedMessage(Context context, String chatroomId, String messageId) {
        Log.d(TAG, "Restoring locally deleted message: " + messageId + " in chatroom: " + chatroomId);
        
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> deletedMessages = new HashSet<>(prefs.getStringSet(KEY_DELETED_MESSAGES + chatroomId, new HashSet<>()));
        
        if (deletedMessages.remove(messageId)) {
            prefs.edit()
                    .putStringSet(KEY_DELETED_MESSAGES + chatroomId, deletedMessages)
                    .apply();
            Log.d(TAG, "Message restored from local deletion: " + messageId);
        }
    }
    
    // Chat deletion methods
    
    /**
     * Mark a chat as locally deleted for the current user
     * @param context Application context
     * @param chatroomId Chat room ID to mark as deleted
     */
    public static void markChatAsLocallyDeleted(Context context, String chatroomId) {
        Log.d(TAG, "Marking chat as locally deleted: " + chatroomId);
        
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> deletedChats = new HashSet<>(prefs.getStringSet(KEY_DELETED_CHATS, new HashSet<>()));
        
        deletedChats.add(chatroomId);
        
        // Store deletion timestamp for restoration logic
        long currentTime = System.currentTimeMillis();
        
        SharedPreferences.Editor editor = prefs.edit();
        editor.putStringSet(KEY_DELETED_CHATS, deletedChats);
        editor.putLong(KEY_CHAT_DELETION_TIMESTAMPS + "_" + chatroomId, currentTime);
        editor.apply();
        
        Log.d(TAG, "Chat marked as deleted locally with timestamp: " + chatroomId + " at " + currentTime);
    }
    
    /**
     * Check if a chat is locally deleted for the current user
     * @param context Application context
     * @param chatroomId Chat room ID to check
     * @return true if chat is locally deleted, false otherwise
     */
    public static boolean isChatLocallyDeleted(Context context, String chatroomId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> deletedChats = prefs.getStringSet(KEY_DELETED_CHATS, new HashSet<>());
        
        boolean isDeleted = deletedChats.contains(chatroomId);
        if (isDeleted) {
            Log.d(TAG, "Chat is locally deleted: " + chatroomId);
        }
        
        return isDeleted;
    }
    
    /**
     * Get all locally deleted chat IDs
     * @param context Application context
     * @return Set of deleted chat IDs
     */
    public static Set<String> getLocallyDeletedChats(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return new HashSet<>(prefs.getStringSet(KEY_DELETED_CHATS, new HashSet<>()));
    }
    
    /**
     * Remove a chat from local deletion (if needed for restore functionality)
     * @param context Application context
     * @param chatroomId Chat room ID to restore
     */
    public static void restoreLocallyDeletedChat(Context context, String chatroomId) {
        Log.d(TAG, "Restoring locally deleted chat: " + chatroomId);
        
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> deletedChats = new HashSet<>(prefs.getStringSet(KEY_DELETED_CHATS, new HashSet<>()));
        
        if (deletedChats.remove(chatroomId)) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putStringSet(KEY_DELETED_CHATS, deletedChats);
            // Also remove the deletion timestamp
            editor.remove(KEY_CHAT_DELETION_TIMESTAMPS + "_" + chatroomId);
            editor.apply();
            
            Log.d(TAG, "Chat restored from local deletion: " + chatroomId);
        }
    }
    
    // Cleanup methods
    
    /**
     * Clear all locally deleted messages for a specific chatroom
     * (useful when a chat is permanently deleted)
     * @param context Application context
     * @param chatroomId Chat room ID
     */
    public static void clearLocallyDeletedMessages(Context context, String chatroomId) {
        Log.d(TAG, "Clearing all locally deleted messages for chatroom: " + chatroomId);
        
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .remove(KEY_DELETED_MESSAGES + chatroomId)
                .apply();
    }
    
    /**
     * Clear all local deletions (messages and chats) - use with caution
     * @param context Application context
     */
    public static void clearAllLocalDeletions(Context context) {
        Log.d(TAG, "Clearing all local deletions");
        
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }
    
    // Utility methods
    
    /**
     * Get total count of locally deleted messages across all chats
     * @param context Application context
     * @return Total count of locally deleted messages
     */
    public static int getTotalLocallyDeletedMessagesCount(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int totalCount = 0;
        
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith(KEY_DELETED_MESSAGES)) {
                Set<String> messages = prefs.getStringSet(key, new HashSet<>());
                totalCount += messages.size();
            }
        }
        
        return totalCount;
    }
    
    /**
     * Get count of locally deleted messages for a specific chatroom
     * @param context Application context
     * @param chatroomId Chat room ID
     * @return Count of locally deleted messages in the chatroom
     */
    public static int getLocallyDeletedMessagesCount(Context context, String chatroomId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> deletedMessages = prefs.getStringSet(KEY_DELETED_MESSAGES + chatroomId, new HashSet<>());
        return deletedMessages.size();
    }
    
    /**
     * Get total count of locally deleted chats
     * @param context Application context
     * @return Total count of locally deleted chats
     */
    public static int getLocallyDeletedChatsCount(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> deletedChats = prefs.getStringSet(KEY_DELETED_CHATS, new HashSet<>());
        return deletedChats.size();
    }
    
    /**
     * Get the timestamp when a chat was locally deleted
     * @param context Application context
     * @param chatroomId Chat room ID
     * @return Deletion timestamp in milliseconds, or 0 if not found
     */
    public static long getChatDeletionTimestamp(Context context, String chatroomId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(KEY_CHAT_DELETION_TIMESTAMPS + "_" + chatroomId, 0);
    }
    
    /**
     * Clear all deletion timestamps (useful for cleanup)
     * @param context Application context
     */
    public static void clearAllDeletionTimestamps(Context context) {
        Log.d(TAG, "Clearing all deletion timestamps");
        
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        // Remove all timestamp entries
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith(KEY_CHAT_DELETION_TIMESTAMPS)) {
                editor.remove(key);
            }
        }
        
        editor.apply();
    }
}
