package com.example.talkifyy;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.example.talkifyy.model.UserModel;
import com.example.talkifyy.utils.AndroidUtil;
import com.example.talkifyy.utils.FirebaseUtil;
import com.example.talkifyy.utils.NotificationUtil;
import com.example.talkifyy.utils.NotificationDebugConfig;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class FCMNotificationService extends FirebaseMessagingService {

    private static final String TAG = "FCMService";
    private static final String CHANNEL_ID = "chat_notifications";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        
        Log.d(TAG, "📱 FCM Message received from: " + remoteMessage.getFrom());

        // Handle data payload for chat messages
        if (remoteMessage.getData().size() > 0) {
            Log.d(TAG, "📦 Message data payload: " + remoteMessage.getData());
            
            String senderName = remoteMessage.getData().get("senderName");
            String message = remoteMessage.getData().get("message");
            String chatId = remoteMessage.getData().get("chatId");
            String type = remoteMessage.getData().get("type");
            String isGroupStr = remoteMessage.getData().get("isGroup");
            String groupName = remoteMessage.getData().get("groupName");
            
            boolean isGroup = "true".equals(isGroupStr);
            Log.d(TAG, "👥 Is group chat: " + isGroup + ", Group name: " + groupName);
            
            // Debug logging
            NotificationDebugConfig.logNotificationInfo("FCM_RECEIVED", chatId, senderName, isGroup);
            
            if ("chat_message".equals(type) && senderName != null && message != null) {
                Log.d(TAG, "💬 Received chat message from: " + senderName);
                
                // Check if this is a voice message
                String notificationText = message;
                if (message.startsWith("🎤")) {
                    // Voice message - show appropriate notification text
                    notificationText = "Voice message";
                    Log.d(TAG, "🎤 Received voice message notification");
                }
                
                showChatNotification(senderName, notificationText, chatId, isGroup, groupName);
                return;
            }
        }

        // Handle notification payload (fallback)
        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "🔔 Message Notification Body: " + remoteMessage.getNotification().getBody());
            showNotification(
                remoteMessage.getNotification().getTitle(),
                remoteMessage.getNotification().getBody(),
                null
            );
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "Refreshed token: " + token);
        
        // Send token to app server if needed
        sendRegistrationToServer(token);
    }

    private void sendRegistrationToServer(String token) {
        // Update FCM token in Firestore
        FirebaseUtil.updateFCMToken(token);
        Log.d(TAG, "Token updated in Firestore: " + token);
    }

    private void showChatNotification(String senderName, String message, String chatId, boolean isGroup, String groupName) {
        Log.d(TAG, "🔔 Showing chat notification from: " + senderName);
        
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        
        // Create enhanced notification channel
        createNotificationChannel(notificationManager);
        
        // Create intent to open specific chat
        Intent chatIntent = new Intent(this, ChatActivity.class);
        
        // Use the provided group information instead of detecting it
        Log.d(TAG, "📱 Chat ID: " + chatId + ", Is group chat: " + isGroup + ", Group name: " + groupName);
        
        if (isGroup) {
            // This is a group chat - set up intent for group
            chatIntent.putExtra("isGroup", true);
            chatIntent.putExtra("chatroomId", chatId);
            chatIntent.putExtra("groupName", groupName != null ? groupName : "Group Chat");
            
            // Create a dummy UserModel for group chats to prevent crashes
            UserModel groupUser = new UserModel();
            groupUser.setUserId("group_" + chatId);
            groupUser.setUsername(groupName != null ? groupName : "Group Chat");
            AndroidUtil.passUserModelAsIntent(chatIntent, groupUser);
            
            Log.d(TAG, "🏢 Setting up group chat notification intent for: " + groupName);
        } else {
            // This is an individual chat - extract sender ID
            String currentUserId = FirebaseUtil.currentUserId();
            String senderId = null;
            
            // Extract other user ID from chat ID for individual chats
            String[] userIds = chatId.split("_");
            if (userIds.length >= 2) {
                senderId = userIds[0].equals(currentUserId) ? userIds[1] : userIds[0];
            }
            
            // Create UserModel with proper data for individual chat
            UserModel senderUser = new UserModel();
            senderUser.setUsername(senderName);
            senderUser.setUserId(senderId != null ? senderId : chatId);
            
            AndroidUtil.passUserModelAsIntent(chatIntent, senderUser);
            Log.d(TAG, "👤 Setting up individual chat notification intent for user: " + senderId);
        }
        
        chatIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 
            (int) System.currentTimeMillis(), 
            chatIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Get default notification sound
        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        // Create notification with appropriate title and content for group vs individual chats
        String notificationTitle;
        String notificationContent;
        
        if (isGroup) {
            // For group chats: Title = Group Name, Content = Sender: Message
            notificationTitle = groupName != null ? groupName : "Group Chat";
            notificationContent = senderName + ": " + message;
        } else {
            // For individual chats: Title = Sender Name, Content = Message
            notificationTitle = senderName;
            notificationContent = message;
        }
        
        Log.d(TAG, "📝 Notification - Title: " + notificationTitle + ", Content: " + notificationContent);
        
        // Create notification with WhatsApp-style features
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.chat_icon)
                .setContentTitle(notificationTitle)
                .setContentText(notificationContent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setSound(defaultSoundUri)
                .setVibrate(new long[]{0, 500, 1000, 500})
                .setLights(0xFF0000FF, 300, 1000)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setGroup("CHAT_GROUP")
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setLargeIcon((android.graphics.Bitmap) null) // TODO: Add profile picture if available
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(notificationContent)
                        .setBigContentTitle(notificationTitle));

        // Use chat-specific notification ID for proper grouping
        // For group chats, use chatId so all messages from the group show as one notification
        // For individual chats, use sender+chatId to distinguish different conversations
        int notificationId;
        if (isGroup) {
            notificationId = chatId.hashCode();
            Log.d(TAG, "🏢 Using group-based notification ID: " + notificationId + " for chat: " + chatId);
        } else {
            notificationId = (senderName + chatId).hashCode();
            Log.d(TAG, "👤 Using sender-based notification ID: " + notificationId + " for: " + senderName);
        }
        
        notificationManager.notify(notificationId, notificationBuilder.build());
        
        // Create or update summary notification for multiple chats
        createSummaryNotification(notificationManager);
        
        Log.d(TAG, "✅ Chat notification displayed successfully - Title: " + notificationTitle + ", ID: " + notificationId);
    }
    
    // Fallback method for backward compatibility
    private void showChatNotification(String senderName, String message, String chatId) {
        // Detect if it's a group chat using helper method
        boolean isGroup = isGroupChatId(chatId);
        String groupName = isGroup ? extractGroupNameFromNotification(senderName, message) : null;
        
        // Call the main method with detected parameters
        showChatNotification(senderName, message, chatId, isGroup, groupName);
    }
    
    private void showNotification(String title, String body, String chatId) {
        Log.d(TAG, "🔔 Showing general notification: " + title);
        
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        createNotificationChannel(notificationManager);

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            intent, 
            PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );
        
        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.chat_icon)
                .setContentTitle(title != null ? title : "New Message")
                .setContentText(body != null ? body : "You have a new message")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setSound(defaultSoundUri)
                .setVibrate(new long[]{0, 300, 600})
                .setLights(0xFF0000FF, 300, 1000);

        notificationManager.notify(0, notificationBuilder.build());
    }
    
    private void createNotificationChannel(NotificationManager notificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Chat Notifications",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Receive notifications for new chat messages");
            channel.enableLights(true);
            channel.enableVibration(true);
            channel.setLightColor(0xFF0000FF);
            channel.setVibrationPattern(new long[]{0, 500, 1000, 500});
            channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null);
            
            notificationManager.createNotificationChannel(channel);
            Log.d(TAG, "📢 Notification channel created with enhanced features");
        }
    }
    
    private void createSummaryNotification(NotificationManager notificationManager) {
        // Create summary notification for grouped chat notifications
        Intent mainIntent = new Intent(this, MainActivity.class);
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        
        PendingIntent summaryPendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Get notification sound
        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        
        NotificationCompat.Builder summaryNotification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.chat_icon)
                .setContentTitle("New Messages")
                .setContentText("You have new messages")
                .setGroup("CHAT_GROUP")
                .setGroupSummary(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(summaryPendingIntent)
                .setSound(null) // Don't play sound for summary, individual notifications handle sound
                .setVibrate(null) // Don't vibrate for summary
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setStyle(new NotificationCompat.InboxStyle()
                        .setBigContentTitle("New Messages")
                        .setSummaryText("Tap to open Talkifyy"));
        
        // Use a fixed ID for the summary notification
        notificationManager.notify(1000, summaryNotification.build());
        Log.d(TAG, "📦 Summary notification created");
    }
    
    /**
     * Determines if a chat ID represents a group chat or individual chat
     * Group chats typically have different ID patterns than individual chats
     */
    private boolean isGroupChatId(String chatId) {
        if (chatId == null) {
            return false;
        }
        
        // Group chat IDs typically:
        // 1. Are longer than individual chat IDs
        // 2. May contain more than 2 user IDs or have a different format
        // 3. May have a "group_" prefix or similar identifier
        
        // Check if it's explicitly marked as a group
        if (chatId.startsWith("group_")) {
            return true;
        }
        
        // Individual chat IDs are typically in format: userId1_userId2
        // Group chat IDs might be longer or have different patterns
        String[] parts = chatId.split("_");
        
        // If more than 2 parts, it's likely a group or complex ID
        if (parts.length > 2) {
            return true;
        }
        
        // If the chatId is very long (group IDs tend to be longer), assume it's a group
        if (chatId.length() > 50) {
            return true;
        }
        
        // Check if any part looks like a generated group ID (contains numbers and letters in specific pattern)
        for (String part : parts) {
            if (part.length() > 20 && part.matches(".*[0-9].*[a-zA-Z].*")) {
                return true;
            }
        }
        
        // Default to individual chat
        return false;
    }
    
    /**
     * Extracts group name from notification data
     * For group chats, the senderName might be the group title in the notification
     */
    private String extractGroupNameFromNotification(String senderName, String message) {
        // If the message contains a pattern like "GroupName: SenderName: message"
        // we can extract the group name
        
        // For now, we'll use senderName as group name if it looks like a group title
        // This might need adjustment based on your actual notification format
        if (senderName != null && senderName.contains(":")) {
            // Split to get potential group name
            String[] parts = senderName.split(":", 2);
            if (parts.length > 0) {
                return parts[0].trim();
            }
        }
        
        // Fallback to senderName or default
        return senderName != null ? senderName : "Group Chat";
    }
}
