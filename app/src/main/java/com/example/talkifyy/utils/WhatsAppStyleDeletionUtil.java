package com.example.talkifyy.utils;

import android.content.Context;
import android.util.Log;

import com.example.talkifyy.model.ChatMessageModel;

/**
 * Utility class for WhatsApp-style message deletion logic
 * Handles business rules for "Delete for Me" and "Delete for Everyone"
 */
public class WhatsAppStyleDeletionUtil {
    
    private static final String TAG = "WhatsAppDeletionUtil";
    private static final long DEFAULT_RECALL_WINDOW_HOURS = 48;
    
    /**
     * Check if "Delete for Everyone" option should be available for a message
     * @param message The message to check
     * @param currentUserId Current user ID
     * @param isGroupAdmin Whether current user is group admin
     * @param isGroupChat Whether this is a group chat
     * @return true if "Delete for Everyone" should be available
     */
    public static boolean canShowDeleteForEveryone(ChatMessageModel message, 
                                                   String currentUserId, 
                                                   boolean isGroupAdmin, 
                                                   boolean isGroupChat) {
        
        if (message == null || currentUserId == null) {
            Log.w(TAG, "Message or currentUserId is null");
            return false;
        }
        
        // Can't delete if already deleted for everyone
        if (message.isDeletedForEveryone()) {
            Log.d(TAG, "Message already deleted for everyone");
            return false;
        }
        
        // Rule 1: Own messages - available if within recall window
        if (message.getSenderId().equals(currentUserId)) {
            boolean withinWindow = message.isWithinRecallWindow();
            Log.d(TAG, "Own message - within recall window: " + withinWindow);
            return withinWindow;
        }
        
        // Rule 2: Other's messages in 1-on-1 chat - NOT available
        if (!isGroupChat) {
            Log.d(TAG, "1-on-1 chat - cannot delete others' messages");
            return false;
        }
        
        // Rule 3: Other's messages in group chat - available only for admins
        Log.d(TAG, "Group chat - admin can delete others' messages: " + isGroupAdmin);
        return isGroupAdmin;
    }
    
    /**
     * Check if "Delete for Me" option should be available for a message
     * This is always available for any message that isn't locally deleted
     * @param messageId Message ID to check
     * @param chatroomId Chatroom ID
     * @param context Application context
     * @return true if "Delete for Me" should be available
     */
    public static boolean canShowDeleteForMe(String messageId, String chatroomId, Context context) {
        if (messageId == null || chatroomId == null || context == null) {
            Log.w(TAG, "Required parameters are null");
            return false;
        }
        
        // Available if not already locally deleted
        boolean isLocallyDeleted = LocalDeletionUtil.isMessageLocallyDeleted(context, chatroomId, messageId);
        Log.d(TAG, "Message locally deleted: " + isLocallyDeleted);
        return !isLocallyDeleted;
    }
    
    /**
     * Get formatted time remaining for recall window
     * @param message The message to check
     * @return Human-readable string of time remaining, or null if expired
     */
    public static String getRecallTimeRemaining(ChatMessageModel message) {
        if (message == null || !message.isWithinRecallWindow()) {
            return null;
        }
        
        long currentTime = System.currentTimeMillis();
        long messageTime = message.getTimestamp().toDate().getTime();
        long timeDifference = currentTime - messageTime;
        long recallWindowMs = message.getRecallWindowHours() * 60 * 60 * 1000;
        long timeRemaining = recallWindowMs - timeDifference;
        
        if (timeRemaining <= 0) {
            return null;
        }
        
        // Convert to human-readable format
        long hours = timeRemaining / (60 * 60 * 1000);
        long minutes = (timeRemaining % (60 * 60 * 1000)) / (60 * 1000);
        
        if (hours > 0) {
            return hours + "h " + minutes + "m remaining";
        } else {
            return minutes + "m remaining";
        }
    }
    
    /**
     * Create deletion confirmation message based on deletion type and count
     * @param deleteForEveryone Whether deleting for everyone
     * @param messageCount Number of messages being deleted
     * @param timeRemaining Time remaining for recall (null if not applicable)
     * @return Confirmation message string
     */
    public static String createDeletionConfirmationMessage(boolean deleteForEveryone, 
                                                          int messageCount, 
                                                          String timeRemaining) {
        StringBuilder message = new StringBuilder();
        
        if (messageCount == 1) {
            message.append("Delete this message");
        } else {
            message.append("Delete ").append(messageCount).append(" messages");
        }
        
        if (deleteForEveryone) {
            message.append(" for everyone?");
            if (timeRemaining != null) {
                message.append("\n\n⏰ ").append(timeRemaining);
            }
            message.append("\n\nDeleted messages will be replaced with \"This message was deleted\" for all participants.");
        } else {
            message.append(" for you?");
            message.append("\n\nThis will only remove the message");
            if (messageCount > 1) {
                message.append("s");
            }
            message.append(" from your device.");
        }
        
        return message.toString();
    }
    
    /**
     * Validate if a deletion operation is allowed
     * @param message The message to delete
     * @param currentUserId Current user ID
     * @param isGroupAdmin Whether current user is group admin
     * @param isGroupChat Whether this is a group chat
     * @param deleteForEveryone Whether this is a "delete for everyone" operation
     * @return true if operation is allowed
     */
    public static boolean validateDeletionOperation(ChatMessageModel message,
                                                   String currentUserId,
                                                   boolean isGroupAdmin,
                                                   boolean isGroupChat,
                                                   boolean deleteForEveryone) {
        
        if (message == null || currentUserId == null) {
            Log.e(TAG, "Invalid parameters for deletion validation");
            return false;
        }
        
        if (deleteForEveryone) {
            return canShowDeleteForEveryone(message, currentUserId, isGroupAdmin, isGroupChat);
        } else {
            // Delete for me is always allowed (handled by LocalDeletionUtil)
            return true;
        }
    }
    
    /**
     * Log deletion action for debugging/analytics
     * @param action Type of deletion action
     * @param messageId Message ID
     * @param chatroomId Chatroom ID
     * @param currentUserId Current user ID
     * @param success Whether the operation was successful
     */
    public static void logDeletionAction(String action, 
                                       String messageId, 
                                       String chatroomId, 
                                       String currentUserId, 
                                       boolean success) {
        Log.i(TAG, String.format("Deletion action: %s | Message: %s | Chatroom: %s | User: %s | Success: %s",
                action, messageId, chatroomId, currentUserId, success));
    }
}
