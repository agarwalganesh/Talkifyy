package com.example.talkifyy.utils;

import android.util.Log;

/**
 * Debug configuration for notification system
 * Use this to test and verify notification functionality
 */
public class NotificationDebugConfig {
    
    private static final String TAG = "NotificationDebug";
    
    // Enable/disable debug logging
    public static final boolean DEBUG_NOTIFICATIONS = true;
    
    // Test configuration
    public static final boolean ENABLE_TEST_LOGGING = true;
    
    /**
     * Log notification debug information
     */
    public static void logNotificationInfo(String event, String chatId, String senderName, boolean isGroup) {
        if (!DEBUG_NOTIFICATIONS) return;
        
        Log.d(TAG, "=== NOTIFICATION DEBUG ===");
        Log.d(TAG, "Event: " + event);
        Log.d(TAG, "Chat ID: " + chatId);
        Log.d(TAG, "Sender: " + senderName);
        Log.d(TAG, "Is Group: " + isGroup);
        Log.d(TAG, "Chat ID Length: " + (chatId != null ? chatId.length() : "null"));
        Log.d(TAG, "Chat ID Parts: " + (chatId != null ? chatId.split("_").length : "null"));
        Log.d(TAG, "=== END DEBUG ===");
    }
    
    /**
     * Test group chat ID detection
     */
    public static void testGroupChatIdDetection() {
        if (!ENABLE_TEST_LOGGING) return;
        
        Log.d(TAG, "Testing Group Chat ID Detection:");
        
        // Test cases
        String[] testIds = {
            "user1_user2",  // Individual chat
            "group_12345",  // Group with prefix
            "very_long_group_chat_id_with_multiple_parts_12345abcdef",  // Long group ID
            "user1_user2_user3",  // Multiple users (likely group)
            "short_id",  // Short individual
        };
        
        for (String testId : testIds) {
            boolean isGroup = isGroupChatId(testId);
            Log.d(TAG, "ID: " + testId + " -> Group: " + isGroup);
        }
    }
    
    /**
     * Simplified group chat detection for testing
     */
    private static boolean isGroupChatId(String chatId) {
        if (chatId == null) return false;
        
        if (chatId.startsWith("group_")) return true;
        
        String[] parts = chatId.split("_");
        if (parts.length > 2) return true;
        
        if (chatId.length() > 50) return true;
        
        for (String part : parts) {
            if (part.length() > 20 && part.matches(".*[0-9].*[a-zA-Z].*")) {
                return true;
            }
        }
        
        return false;
    }
}