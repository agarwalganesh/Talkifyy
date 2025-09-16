package com.example.talkifyy.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.talkifyy.ChatActivity;
import com.example.talkifyy.MainActivity;
import com.example.talkifyy.R;
import com.example.talkifyy.model.UserModel;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class NotificationUtil {
    
    private static final String TAG = "NotificationUtil";
    private static final String CHANNEL_ID = "chat_notifications";
    private static final String CHANNEL_NAME = "Chat Messages";
    private static final String FCM_URL = "https://fcm.googleapis.com/fcm/send";
    
    // Keep track of message counts per sender for grouping
    private static final Map<String, Integer> messageCountMap = new HashMap<>();
    private static final Map<String, String> lastMessageMap = new HashMap<>();
    private static final String GROUP_KEY = "CHAT_GROUP";
    private static final int SUMMARY_NOTIFICATION_ID = 1000;
    
    // You'll need to replace this with your actual Firebase Server Key
    // Get it from Firebase Console > Project Settings > Cloud Messaging > Server Key
    private static final String SERVER_KEY = "YOUR_FIREBASE_SERVER_KEY";
    
    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Receive notifications for new chat messages");
            channel.enableLights(true);
            channel.enableVibration(true);
            
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
            
            Log.d(TAG, "Notification channel created");
        }
    }
    
    /**
     * Show local notification specifically for group chats
     */
    public static void showGroupLocalNotification(Context context, String groupName, String senderName, String message, String chatroomId) {
        if (context == null) {
            Log.w(TAG, "Context is null, cannot show group notification");
            return;
        }
        
        Log.d(TAG, "Showing group notification - Group: " + groupName + ", Sender: " + senderName + ", Message: " + message);
        
        // Create notification channel
        createNotificationChannel(context);
        
        // Update message count for this group
        int messageCount = messageCountMap.getOrDefault(chatroomId, 0) + 1;
        messageCountMap.put(chatroomId, messageCount);
        lastMessageMap.put(chatroomId, senderName + ": " + message);
        
        Log.d(TAG, "📊 Group message count for " + groupName + ": " + messageCount);
        
        // Create intent to open group chat when notification is tapped
        Intent intent = new Intent(context, ChatActivity.class);
        intent.putExtra("isGroup", true);
        intent.putExtra("chatroomId", chatroomId);
        intent.putExtra("groupName", groupName);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, 
            chatroomId.hashCode(), // Unique pending intent per group
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Create display content for group notification
        String displayTitle = groupName;
        String displayMessage = senderName + ": " + message;
        
        // If multiple messages from group, show count
        if (messageCount > 1) {
            displayMessage = messageCount + " new messages";
        }
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.chat_icon)
                .setContentTitle(displayTitle)
                .setContentText(displayMessage)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(senderName + ": " + message)) // Always show latest message in expanded view
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVibrate(new long[]{0, 250, 250, 250})
                .setLights(Color.BLUE, 1000, 1000)
                .setGroup(GROUP_KEY) // Group all chat notifications
                .setWhen(System.currentTimeMillis())
                .setSortKey(String.valueOf(System.currentTimeMillis())); // Sort by time for proper ordering
        
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        
        try {
            // Show individual notification
            int notificationId = chatroomId.hashCode();
            Log.d(TAG, "📱 Showing group notification - ID: " + notificationId + ", Title: " + displayTitle + ", Message: " + displayMessage);
            notificationManager.notify(notificationId, builder.build());
            
            // Create and show summary notification (WhatsApp style)
            showSummaryNotification(context, notificationManager);
            
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied for showing group notification", e);
        }
    }
    
    public static void showLocalNotification(Context context, String senderName, String message, UserModel otherUser) {
        if (context == null) {
            Log.w(TAG, "Context is null, cannot show notification");
            return;
        }
        
        Log.d(TAG, "Showing enhanced notification from " + senderName + ": " + message);
        
        // Create notification channel
        createNotificationChannel(context);
        
        String senderId = otherUser != null && otherUser.getUserId() != null ? otherUser.getUserId() : "unknown";
        
        // Update message count for this sender
        int messageCount = messageCountMap.getOrDefault(senderId, 0) + 1;
        messageCountMap.put(senderId, messageCount);
        lastMessageMap.put(senderId, message);
        
        Log.d(TAG, "📊 Message count for " + senderName + ": " + messageCount);
        
        // Create intent to open chat when notification is tapped
        Intent intent = new Intent(context, ChatActivity.class);
        if (otherUser != null) {
            AndroidUtil.passUserModelAsIntent(intent, otherUser);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, 
            senderId.hashCode(), // Unique pending intent per sender
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Create display content
        String displayTitle = senderName != null ? senderName : "Someone";
        String displayMessage = message;
        
        // If multiple messages from same sender, show count
        if (messageCount > 1) {
            displayMessage = messageCount + " new messages";
        }
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.chat_icon)
                .setContentTitle(displayTitle)
                .setContentText(displayMessage)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message)) // Always show latest message in expanded view
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVibrate(new long[]{0, 250, 250, 250})
                .setLights(Color.BLUE, 1000, 1000)
                .setGroup(GROUP_KEY) // Group all chat notifications
                .setWhen(System.currentTimeMillis())
                .setSortKey(String.valueOf(System.currentTimeMillis())); // Sort by time for proper ordering
        
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        
        try {
            // Show individual notification
            int notificationId = senderId.hashCode();
            Log.d(TAG, "📱 Showing individual notification - ID: " + notificationId + ", Title: " + displayTitle + ", Message: " + displayMessage);
            notificationManager.notify(notificationId, builder.build());
            
            // Create and show summary notification (WhatsApp style)
            showSummaryNotification(context, notificationManager);
            
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied for showing notification", e);
        }
    }
    
    private static void showSummaryNotification(Context context, NotificationManagerCompat notificationManager) {
        // Calculate total messages and create summary
        int totalMessages = 0;
        int senderCount = messageCountMap.size();
        
        for (int count : messageCountMap.values()) {
            totalMessages += count;
        }
        
        if (totalMessages <= 1) {
            return; // Don't show summary for single message
        }
        
        // Create summary content
        String summaryTitle = totalMessages + " new messages";
        String summaryText = "From " + senderCount + (senderCount == 1 ? " chat" : " chats");
        
        // Create inbox style for summary
        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
        inboxStyle.setBigContentTitle(summaryTitle);
        
        // Add lines for each sender (up to 6 lines)
        int lineCount = 0;
        for (Map.Entry<String, String> entry : lastMessageMap.entrySet()) {
            if (lineCount >= 6) break;
            
            String senderId = entry.getKey();
            String lastMessage = entry.getValue();
            int count = messageCountMap.get(senderId);
            
            // Create display line
            String line = (count > 1 ? count + " messages" : lastMessage);
            inboxStyle.addLine(line);
            lineCount++;
        }
        
        if (senderCount > 6) {
            inboxStyle.addLine("and " + (senderCount - 6) + " more chats...");
        }
        
        // Intent to open main chat list
        Intent summaryIntent = new Intent(context, MainActivity.class);
        summaryIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent summaryPendingIntent = PendingIntent.getActivity(
            context,
            SUMMARY_NOTIFICATION_ID,
            summaryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        NotificationCompat.Builder summaryBuilder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.chat_icon)
                .setContentTitle(summaryTitle)
                .setContentText(summaryText)
                .setStyle(inboxStyle)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(summaryPendingIntent)
                .setGroup(GROUP_KEY)
                .setGroupSummary(true) // This makes it a summary notification
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVibrate(new long[]{0, 250, 250, 250})
                .setLights(Color.BLUE, 1000, 1000);
        
        try {
            Log.d(TAG, "📋 Showing summary notification - Total messages: " + totalMessages + ", Chats: " + senderCount);
            notificationManager.notify(SUMMARY_NOTIFICATION_ID, summaryBuilder.build());
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied for showing summary notification", e);
        }
    }
    
    // Method to clear message count when user opens a specific chat
    public static void clearMessageCount(String senderId) {
        if (senderId != null) {
            messageCountMap.remove(senderId);
            lastMessageMap.remove(senderId);
            Log.d(TAG, "🗑️ Cleared message count for sender: " + senderId);
        }
    }
    
    // Method to clear all notification data
    public static void clearAllNotificationData() {
        messageCountMap.clear();
        lastMessageMap.clear();
        Log.d(TAG, "🗑️ Cleared all notification data");
    }
    
    // Method to get notification count for a specific sender
    public static int getMessageCount(String senderId) {
        return senderId != null ? messageCountMap.getOrDefault(senderId, 0) : 0;
    }
    
    // Method to check if sender has unread messages
    public static boolean hasUnreadMessages(String senderId) {
        return getMessageCount(senderId) > 0;
    }
    
    // Method to update message count without showing notification (for Instagram-style updates)
    public static void updateMessageCount(String senderId, String message) {
        if (senderId != null && message != null) {
            int currentCount = messageCountMap.getOrDefault(senderId, 0);
            messageCountMap.put(senderId, currentCount + 1);
            lastMessageMap.put(senderId, message);
            
            int newCount = currentCount + 1;
            Log.d(TAG, "📊 Updated message count for sender " + senderId + ": " + newCount);
        }
    }
    
    public static void sendFCMNotification(String recipientToken, String senderName, String message, String chatId) {
        if (SERVER_KEY.equals("YOUR_FIREBASE_SERVER_KEY")) {
            Log.w(TAG, "FCM Server key not configured. Using local notifications only.");
            return;
        }
        
        Log.d(TAG, "Sending FCM notification to token: " + recipientToken);
        
        OkHttpClient client = new OkHttpClient();
        
        try {
            JSONObject json = new JSONObject();
            json.put("to", recipientToken);
            
            // Notification payload
            JSONObject notification = new JSONObject();
            notification.put("title", senderName);
            notification.put("body", message);
            notification.put("icon", "chat_icon");
            notification.put("sound", "default");
            json.put("notification", notification);
            
            // Data payload for handling in app
            JSONObject data = new JSONObject();
            data.put("senderName", senderName);
            data.put("message", message);
            data.put("chatId", chatId);
            data.put("type", "chat_message");
            
            // Add group chat detection
            boolean isGroupChat = isGroupChatId(chatId);
            data.put("isGroup", String.valueOf(isGroupChat));
            
            // If it's a group chat, include group name
            if (isGroupChat) {
                String groupName = extractGroupName(senderName);
                data.put("groupName", groupName);
            } else {
                // Explicitly mark as false for individual chats
                data.put("isGroup", "false");
            }
            
            json.put("data", data);
            
            RequestBody body = RequestBody.create(
                json.toString(),
                MediaType.parse("application/json; charset=utf-8")
            );
            
            Request request = new Request.Builder()
                    .url(FCM_URL)
                    .post(body)
                    .addHeader("Authorization", "key=" + SERVER_KEY)
                    .addHeader("Content-Type", "application/json")
                    .build();
            
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Failed to send FCM notification", e);
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body().string();
                    if (response.isSuccessful()) {
                        Log.d(TAG, "FCM notification sent successfully: " + responseBody);
                    } else {
                        Log.e(TAG, "FCM notification failed: " + responseBody);
                    }
                }
            });
            
        } catch (JSONException e) {
            Log.e(TAG, "Error creating FCM notification JSON", e);
        }
    }
    
    public static void sendNotificationToUser(String recipientUserId, String senderName, String message, String chatId) {
        Log.d(TAG, "Getting FCM token for user: " + recipientUserId);
        
        // Get recipient's FCM token from Firestore
        FirebaseUtil.allUserCollectionReference().document(recipientUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        UserModel recipient = documentSnapshot.toObject(UserModel.class);
                        if (recipient != null && recipient.getFcmToken() != null && !recipient.getFcmToken().isEmpty()) {
                            Log.d(TAG, "Found FCM token, sending notification");
                            sendFCMNotification(recipient.getFcmToken(), senderName, message, chatId);
                        } else {
                            Log.w(TAG, "Recipient FCM token not found or empty");
                        }
                    } else {
                        Log.w(TAG, "Recipient user document not found");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get recipient FCM token", e);
                });
    }
    
    /**
     * Send notification specifically for group chats with proper formatting
     */
    public static void sendGroupNotificationToUser(String recipientUserId, String groupName, String senderName, String message, String chatId) {
        Log.d(TAG, "Getting FCM token for group notification - User: " + recipientUserId + ", Group: " + groupName);
        
        // Check if FCM server key is configured
        if (SERVER_KEY.equals("YOUR_FIREBASE_SERVER_KEY")) {
            Log.w(TAG, "FCM Server key not configured. Testing with local notifications only.");
            // For testing without FCM server key, we'll simulate notifications
            Log.d(TAG, "🧪 TESTING: Would send group notification to " + recipientUserId + " for group " + groupName);
            Log.d(TAG, "🧪 Notification content: " + senderName + ": " + message);
            return;
        }
        
        // Get recipient's FCM token from Firestore
        FirebaseUtil.allUserCollectionReference().document(recipientUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        UserModel recipient = documentSnapshot.toObject(UserModel.class);
                        if (recipient != null && recipient.getFcmToken() != null && !recipient.getFcmToken().isEmpty()) {
                            Log.d(TAG, "Found FCM token, sending group notification");
                            sendGroupFCMNotification(recipient.getFcmToken(), groupName, senderName, message, chatId);
                        } else {
                            Log.w(TAG, "Recipient FCM token not found or empty for group notification");
                        }
                    } else {
                        Log.w(TAG, "Recipient user document not found for group notification");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get recipient FCM token for group notification", e);
                });
    }
    
    /**
     * Send FCM notification specifically formatted for group chats
     */
    public static void sendGroupFCMNotification(String recipientToken, String groupName, String senderName, String message, String chatId) {
        if (SERVER_KEY.equals("YOUR_FIREBASE_SERVER_KEY")) {
            Log.w(TAG, "FCM Server key not configured. Using local notifications only.");
            return;
        }
        
        Log.d(TAG, "Sending group FCM notification to token: " + recipientToken);
        
        OkHttpClient client = new OkHttpClient();
        
        try {
            JSONObject json = new JSONObject();
            json.put("to", recipientToken);
            
            // Notification payload for group chat
            JSONObject notification = new JSONObject();
            notification.put("title", groupName);  // Group name as title
            notification.put("body", senderName + ": " + message);  // Sender: Message format
            notification.put("icon", "chat_icon");
            notification.put("sound", "default");
            json.put("notification", notification);
            
            // Data payload for handling in app - with group-specific information
            JSONObject data = new JSONObject();
            data.put("senderName", senderName);
            data.put("message", message);
            data.put("chatId", chatId);
            data.put("type", "chat_message");
            data.put("isGroup", "true");  // Mark as group chat
            data.put("groupName", groupName);  // Include group name
            json.put("data", data);
            
            RequestBody body = RequestBody.create(
                json.toString(),
                MediaType.parse("application/json; charset=utf-8")
            );
            
            Request request = new Request.Builder()
                    .url(FCM_URL)
                    .post(body)
                    .addHeader("Authorization", "key=" + SERVER_KEY)
                    .addHeader("Content-Type", "application/json")
                    .build();
            
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Failed to send group FCM notification", e);
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body().string();
                    if (response.isSuccessful()) {
                        Log.d(TAG, "Group FCM notification sent successfully: " + responseBody);
                    } else {
                        Log.e(TAG, "Group FCM notification failed: " + responseBody);
                    }
                }
            });
            
        } catch (JSONException e) {
            Log.e(TAG, "Error creating group FCM notification JSON", e);
        }
    }
    
    /**
     * Determines if a chat ID represents a group chat
     */
    private static boolean isGroupChatId(String chatId) {
        if (chatId == null) {
            return false;
        }
        
        // Group chat detection logic
        if (chatId.startsWith("group_")) {
            return true;
        }
        
        String[] parts = chatId.split("_");
        if (parts.length > 2) {
            return true;
        }
        
        if (chatId.length() > 50) {
            return true;
        }
        
        for (String part : parts) {
            if (part.length() > 20 && part.matches(".*[0-9].*[a-zA-Z].*")) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Extracts group name from notification sender name
     */
    private static String extractGroupName(String senderName) {
        if (senderName != null && senderName.contains(":")) {
            String[] parts = senderName.split(":", 2);
            if (parts.length > 0) {
                return parts[0].trim();
            }
        }
        return senderName != null ? senderName : "Group Chat";
    }
}
