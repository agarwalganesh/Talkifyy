package com.example.talkifyy.services;

import android.content.Context;
import android.util.Log;

import com.example.talkifyy.model.ChatroomModel;
import com.example.talkifyy.utils.FirebaseUtil;
import com.example.talkifyy.utils.LocalDeletionUtil;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.HashSet;
import java.util.Set;

/**
 * Service to monitor locally deleted chats for new messages and trigger restoration
 * This ensures WhatsApp-like behavior where deleted chats reappear when new messages arrive
 */
public class ChatRestorationService {
    private static final String TAG = "ChatRestorationService";
    
    private final Context context;
    private final Set<ListenerRegistration> activeListeners;
    private final ChatRestorationListener restorationListener;
    
    public interface ChatRestorationListener {
        void onChatRestored(String chatroomId, ChatroomModel chatroom);
    }
    
    public ChatRestorationService(Context context, ChatRestorationListener listener) {
        this.context = context;
        this.activeListeners = new HashSet<>();
        this.restorationListener = listener;
    }
    
    /**
     * Start monitoring all locally deleted chats for new messages
     */
    public void startMonitoring() {
        Log.d(TAG, "Starting chat restoration monitoring");
        
        // Get all locally deleted chats
        Set<String> deletedChats = LocalDeletionUtil.getLocallyDeletedChats(context);
        
        if (deletedChats.isEmpty()) {
            Log.d(TAG, "No locally deleted chats to monitor");
            return;
        }
        
        Log.d(TAG, "Monitoring " + deletedChats.size() + " deleted chats");
        
        for (String chatroomId : deletedChats) {
            monitorChatForRestoration(chatroomId);
        }
    }
    
    /**
     * Monitor a specific chat for new messages that should trigger restoration
     */
    private void monitorChatForRestoration(String chatroomId) {
        Log.d(TAG, "Setting up monitoring for deleted chat: " + chatroomId);
        
        // Get the deletion timestamp
        long deletionTimestamp = LocalDeletionUtil.getChatDeletionTimestamp(context, chatroomId);
        
        if (deletionTimestamp == 0) {
            Log.w(TAG, "No deletion timestamp found for chat: " + chatroomId);
            return;
        }
        
        // Listen to the specific chatroom document
        ListenerRegistration registration = FirebaseUtil.getChatroomReference(chatroomId)
                .addSnapshotListener((documentSnapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error monitoring chat: " + chatroomId, error);
                        return;
                    }
                    
                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        ChatroomModel chatroom = documentSnapshot.toObject(ChatroomModel.class);
                        if (chatroom != null) {
                            handleChatroomUpdate(chatroomId, chatroom, deletionTimestamp);
                        }
                    }
                });
        
        activeListeners.add(registration);
    }
    
    /**
     * Handle updates to a monitored chatroom
     */
    private void handleChatroomUpdate(String chatroomId, ChatroomModel chatroom, long deletionTimestamp) {
        if (chatroom.getLastMessageTimestamp() == null) {
            return;
        }
        
        long lastMessageTime = chatroom.getLastMessageTimestamp().toDate().getTime();
        
        // Check if there's a new message since deletion
        if (lastMessageTime > deletionTimestamp) {
            String currentUserId = FirebaseUtil.currentUserId();
            boolean messageFromOtherUser = !chatroom.getLastMessageSenderId().equals(currentUserId);
            
            Log.d(TAG, "New message detected in deleted chat: " + chatroomId + 
                  ", from other user: " + messageFromOtherUser);
            
            // Restore the chat if there's a new message (especially from other user)
            if (messageFromOtherUser) {
                restoreChat(chatroomId, chatroom);
            }
        }
    }
    
    /**
     * Restore a locally deleted chat
     */
    private void restoreChat(String chatroomId, ChatroomModel chatroom) {
        Log.d(TAG, "Restoring chat: " + chatroomId);
        
        // Remove from local deletion
        LocalDeletionUtil.restoreLocallyDeletedChat(context, chatroomId);
        
        // Stop monitoring this chat (it's no longer deleted)
        stopMonitoringChat(chatroomId);
        
        // Notify listener
        if (restorationListener != null) {
            restorationListener.onChatRestored(chatroomId, chatroom);
        }
    }
    
    /**
     * Add monitoring for a newly deleted chat
     */
    public void addChatToMonitoring(String chatroomId) {
        Log.d(TAG, "Adding chat to monitoring: " + chatroomId);
        monitorChatForRestoration(chatroomId);
    }
    
    /**
     * Stop monitoring a specific chat
     */
    public void stopMonitoringChat(String chatroomId) {
        // Note: In a more complex implementation, we'd track which registration
        // corresponds to which chat. For now, we'll rely on the general cleanup.
        Log.d(TAG, "Chat restoration completed for: " + chatroomId);
    }
    
    /**
     * Stop all monitoring and cleanup resources
     */
    public void stopMonitoring() {
        Log.d(TAG, "Stopping chat restoration monitoring");
        
        for (ListenerRegistration registration : activeListeners) {
            if (registration != null) {
                registration.remove();
            }
        }
        
        activeListeners.clear();
    }
    
    /**
     * Refresh monitoring (useful when app resumes)
     */
    public void refreshMonitoring() {
        stopMonitoring();
        startMonitoring();
    }
}
