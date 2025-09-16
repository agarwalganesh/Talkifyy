package com.example.talkifyy;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.graphics.Rect;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.example.talkifyy.adapter.ChatContextMenuListener;
import com.example.talkifyy.adapter.RecentChatRecyclerAdapter;
import com.example.talkifyy.model.ChatroomModel;
import com.example.talkifyy.model.ChatMessageModel;
import com.example.talkifyy.services.ChatRestorationService;
import com.example.talkifyy.utils.AndroidUtil;
import com.example.talkifyy.utils.FirebaseUtil;
import com.example.talkifyy.utils.NotificationUtil;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.ListenerRegistration;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.firebase.firestore.Query;




import com.example.talkifyy.model.UserModel;


public class ChatFragment extends Fragment implements ChatContextMenuListener, ChatRestorationService.ChatRestorationListener {

        private static final String TAG = "ChatFragment";
        RecyclerView recyclerView;
        RecentChatRecyclerAdapter adapter;
        ChatRestorationService restorationService;
        
        // Real-time message listeners
        private ListenerRegistration globalMessageListener;
        private boolean isAppInForeground = true;
        
        // Static mode - prevents chat list refresh when current user sends messages
        private boolean enableStaticMode = true;
        private long lastOwnMessageTime = 0;
        
        // Timing controls to prevent rapid UI updates
        private long lastUIUpdateTime = 0;
        private static final long MIN_UI_UPDATE_INTERVAL = 500; // 500ms minimum between updates


        public ChatFragment() {
        }
        @Override


        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View view =  inflater.inflate(R.layout.fragment_chat, container, false);
            recyclerView = view.findViewById(R.id.recycler_view);
            setupRecyclerView();

            return view;
        }

        void setupRecyclerView(){
            // Add safety check for fragment state
            if(!isAdded()) {
                return;
            }
            
            // Instagram-style query - order by timestamp descending (latest first)
            Query query = FirebaseUtil.allChatroomCollectionReference()
                    .whereArrayContains("userIds", FirebaseUtil.currentUserId())
                    .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING);
            
            Log.d(TAG, "📱 Setting up chat list with STATIC MODE - won't refresh on own messages");
            Log.d(TAG, "🔒 STATIC MODE ENABLED: Chat list will remain stable when you send messages");

            FirestoreRecyclerOptions<ChatroomModel> options = new FirestoreRecyclerOptions.Builder<ChatroomModel>()
                    .setQuery(query,ChatroomModel.class).build();

            adapter = new RecentChatRecyclerAdapter(options,getContext());
            adapter.setChatContextMenuListener(this);
            recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            
            // Add custom spacing between items
            recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
                @Override
                public void getItemOffsets(Rect outRect, android.view.View view, RecyclerView parent, RecyclerView.State state) {
                    super.getItemOffsets(outRect, view, parent, state);
                    
                    int position = parent.getChildAdapterPosition(view);
                    
                    // Add spacing between items
                    if (position > 0) {
                        outRect.top = 4; // 4dp spacing between items
                    }
                    
                    // Add spacing at the bottom of the last item
                    if (position == state.getItemCount() - 1) {
                        outRect.bottom = 16; // 16dp spacing at bottom
                    }
                }
            });
            
            recyclerView.setAdapter(adapter);
            adapter.startListening();
        }

        @Override
        public void onStart() {
            super.onStart();
            if(adapter!=null)
                adapter.startListening();
            
            // Initialize and start chat restoration monitoring
            if (restorationService == null) {
                restorationService = new ChatRestorationService(getContext(), this);
            }
            
            // Connect restoration service to adapter
            if (adapter != null) {
                adapter.setChatRestorationService(restorationService);
            }
            
            restorationService.startMonitoring();
            
            // Setup global message listener for notifications
            setupGlobalMessageListener();
        }

        @Override
        public void onStop() {
            super.onStop();
            if(adapter!=null)
                adapter.stopListening();
            
            // Stop chat restoration monitoring
            if (restorationService != null) {
                restorationService.stopMonitoring();
            }
            
            // Stop global message listener
            if (globalMessageListener != null) {
                globalMessageListener.remove();
                globalMessageListener = null;
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            isAppInForeground = true;
            Log.d(TAG, "🔄 STATIC MODE: Chat list resumed - static mode is " + (enableStaticMode ? "ENABLED" : "DISABLED"));
            if(adapter!=null)
                adapter.notifyDataSetChanged();
        }
        
        @Override
        public void onPause() {
            super.onPause();
            isAppInForeground = false;
        }
        
        @Override
        public void onDestroy() {
            super.onDestroy();
            if(adapter!=null) {
                adapter.cleanup();
            }
            
            // Cleanup restoration service
            if (restorationService != null) {
                restorationService.stopMonitoring();
                restorationService = null;
            }
        }
        
        // ChatContextMenuListener implementation
        @Override
        public void onOpenChat(UserModel otherUser) {
            Intent intent = new Intent(getActivity(), ChatActivity.class);
            AndroidUtil.passUserModelAsIntent(intent, otherUser);
            startActivity(intent);
        }
        
        @Override
        public void onDeleteChat(String chatroomId, UserModel otherUser) {
            String displayName = (otherUser != null && otherUser.getUsername() != null) 
                    ? otherUser.getUsername() 
                    : "Unknown User";
            Log.d(TAG, "Deleting chat: " + chatroomId + " with user: " + displayName);
            
            FirebaseUtil.deleteChatConversation(chatroomId,
                    aVoid -> {
                        if (isAdded()) {
                            Toast.makeText(getContext(), "Chat with " + displayName + " deleted", Toast.LENGTH_SHORT).show();
                            Log.d(TAG, "Chat deleted successfully: " + chatroomId);
                        }
                    },
                    e -> {
                        if (isAdded()) {
                            Toast.makeText(getContext(), "Failed to delete chat", Toast.LENGTH_SHORT).show();
                            Log.e(TAG, "Failed to delete chat: " + chatroomId, e);
                        }
                    });
        }
        
        // ChatRestorationService.ChatRestorationListener implementation
        @Override
        public void onChatRestored(String chatroomId, ChatroomModel chatroom) {
            Log.d(TAG, "Chat restored: " + chatroomId);
            
            // Refresh the adapter to show the restored chat
            if (adapter != null && isAdded()) {
                getActivity().runOnUiThread(() -> {
                    adapter.notifyDataSetChanged();
                });
            }
        }
        
        private void setupGlobalMessageListener() {
            Log.d(TAG, "🔍 Setting up global message listener for notifications");
            
            // Listen to all chatrooms the user is part of
            Query query = FirebaseUtil.allChatroomCollectionReference()
                    .whereArrayContains("userIds", FirebaseUtil.currentUserId());
            
            globalMessageListener = query.addSnapshotListener((snapshots, error) -> {
                if (error != null) {
                    Log.w(TAG, "Listen failed for global messages", error);
                    return;
                }
                
                if (snapshots != null && !snapshots.isEmpty()) {
                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        switch (dc.getType()) {
                            case MODIFIED:
                                // Check if this is a new message in an existing chat
                                ChatroomModel chatroom = dc.getDocument().toObject(ChatroomModel.class);
                                checkForNewMessage(dc.getDocument().getId(), chatroom);
                                break;
                        }
                    }
                }
            });
        }
        
        private void checkForNewMessage(String chatroomId, ChatroomModel chatroom) {
            // Handle new messages for both foreground and background scenarios
            if (chatroom != null && chatroom.getLastMessageSenderId() != null && chatroom.getLastMessage() != null) {
                
                // STATIC MODE: Don't process messages sent by current user - KEEP CHAT LIST STABLE
                if (enableStaticMode && chatroom.getLastMessageSenderId().equals(FirebaseUtil.currentUserId())) {
                    Log.d(TAG, "✋ STATIC MODE: Blocking chat list refresh for own message");
                    Log.d(TAG, "Message from current user: " + chatroom.getLastMessage());
                    Log.d(TAG, "Chatroom ID: " + chatroomId);
                    
                    // Update timestamp to track own messages
                    if (chatroom.getLastMessageTimestamp() != null) {
                        lastOwnMessageTime = chatroom.getLastMessageTimestamp().toDate().getTime();
                    }
                    
                    Log.d(TAG, "🚫 PREVENTED: Chat list refresh blocked to keep it static");
                    return;
                }
                
                boolean isGroupChat = chatroom.isGroup();
                Log.d(TAG, "🔔 New message detected in chatroom: " + chatroomId + " (Group: " + isGroupChat + ")");
                Log.d(TAG, "Last message: " + chatroom.getLastMessage());
                Log.d(TAG, "Sender ID: " + chatroom.getLastMessageSenderId());
                
                // Get sender information
                String senderId = chatroom.getLastMessageSenderId();
                FirebaseUtil.allUserCollectionReference().document(senderId)
                        .get()
                        .addOnSuccessListener(documentSnapshot -> {
                            if (documentSnapshot.exists() && isAdded()) {
                                UserModel sender = documentSnapshot.toObject(UserModel.class);
                                if (sender != null) {
                                    String senderName = sender.getUsername() != null ? sender.getUsername() : "Someone";
                                    String message = chatroom.getLastMessage();
                                    
                                    if (senderName != null && message != null && !message.trim().isEmpty()) {
                                        
                                        // Handle notification count differently for groups vs individual chats
                                        String notificationKey = isGroupChat ? chatroomId : senderId;
                                        NotificationUtil.updateMessageCount(notificationKey, message);
                                        
                                        Log.d(TAG, "📊 Updated message count for key: " + notificationKey + " (Group: " + isGroupChat + ")");
                                        
                                        if (isAppInForeground) {
                                            // App is in foreground - Update chat list for messages from others
                                            Log.d(TAG, "📱 App in foreground - processing message from OTHER user: " + senderName);
                                            Log.d(TAG, "📱 Message content: " + message);
                                            Log.d(TAG, "📱 Is group chat: " + isGroupChat);
                                            Log.d(TAG, "📱 Chatroom ID: " + chatroomId);
                                            
                                            // Notify adapter to update and highlight the chat
                                            if (adapter != null) {
                                                // Throttle UI updates to prevent overwhelming
                                                long currentTime = System.currentTimeMillis();
                                                if (currentTime - lastUIUpdateTime >= MIN_UI_UPDATE_INTERVAL) {
                                                    lastUIUpdateTime = currentTime;
                                                    
                                                    getActivity().runOnUiThread(() -> {
                                                        try {
                                                            Log.d(TAG, "🔄 Updating adapter on UI thread (throttled)");
                                                            
                                                            if (isGroupChat) {
                                                                // For group chats, highlight using chatroom ID
                                                                Log.d(TAG, "🎆 Highlighting group chat: " + chatroomId);
                                                                adapter.highlightChatWithNewMessage(chatroomId, chatroomId);
                                                            } else {
                                                                // For individual chats, highlight using sender ID
                                                                Log.d(TAG, "🎆 Highlighting individual chat: " + senderId);
                                                                adapter.highlightChatWithNewMessage(senderId, chatroomId);
                                                            }
                                                            
                                                            // Force adapter refresh
                                                            adapter.notifyDataSetChanged();
                                                            Log.d(TAG, "✅ Adapter updated successfully (throttled)");
                                                            
                                                        } catch (Exception e) {
                                                            Log.e(TAG, "❌ Error updating adapter", e);
                                                        }
                                                    });
                                                } else {
                                                    Log.d(TAG, "⏱️ Skipping UI update - too soon since last update (" + (currentTime - lastUIUpdateTime) + "ms ago)");
                                                }
                                            } else {
                                                Log.w(TAG, "⚠️ Adapter is null, cannot update chat list");
                                            }
                                        } else {
                                            // App is in background - show notification
                                            Log.d(TAG, "💬 Creating notification: " + senderName + " - " + message);
                                            
                                            if (isGroupChat) {
                                                // For group chats, show group notification
                                                String groupName = chatroom.getGroupName() != null ? chatroom.getGroupName() : "Group Chat";
                                                NotificationUtil.showGroupLocalNotification(getContext(), groupName, senderName, message, chatroomId);
                                            } else {
                                                // For individual chats, show regular notification
                                                NotificationUtil.showLocalNotification(getContext(), senderName, message, sender);
                                            }
                                        }
                                    } else {
                                        Log.w(TAG, "Invalid message data - senderName: " + senderName + ", message: " + message);
                                    }
                                } else {
                                    Log.w(TAG, "Could not parse sender UserModel from Firestore");
                                }
                            } else {
                                Log.w(TAG, "Sender document does not exist or fragment not added");
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "❌ Failed to get sender info", e);
                            // Still update count even if we can't get sender details
                            String notificationKey = isGroupChat ? chatroomId : senderId;
                            NotificationUtil.updateMessageCount(notificationKey, chatroom.getLastMessage());
                            
                            Log.d(TAG, "📊 Updated message count (fallback) for key: " + notificationKey);
                            
                            if (isAppInForeground && adapter != null) {
                                Log.d(TAG, "🔄 Updating adapter (fallback) on UI thread");
                                getActivity().runOnUiThread(() -> {
                                    try {
                                        adapter.notifyDataSetChanged();
                                        Log.d(TAG, "✅ Adapter updated (fallback) successfully");
                                    } catch (Exception ex) {
                                        Log.e(TAG, "❌ Error updating adapter (fallback)", ex);
                                    }
                                });
                            }
                        });
            } else {
                Log.d(TAG, "Invalid chatroom data for new message processing");
            }
        }
        
        /**
         * Enable or disable static mode for chat list
         * When enabled, chat list won't refresh when current user sends messages
         */
        public void setStaticMode(boolean enabled) {
            this.enableStaticMode = enabled;
            Log.d(TAG, "🔧 STATIC MODE " + (enabled ? "ENABLED" : "DISABLED") + " - Chat list will " + (enabled ? "remain stable" : "refresh normally") + " for own messages");
        }
        
        /**
         * Check if static mode is enabled
         */
        public boolean isStaticModeEnabled() {
            return enableStaticMode;
        }
        
        /**
         * Force refresh the chat list (useful for debugging)
         */
        public void forceRefreshChatList() {
            if (adapter != null && isAdded()) {
                getActivity().runOnUiThread(() -> {
                    try {
                        Log.d(TAG, "🔄 FORCE: Refreshing chat list");
                        adapter.notifyDataSetChanged();
                        Log.d(TAG, "✅ FORCE: Chat list refreshed successfully");
                    } catch (Exception e) {
                        Log.e(TAG, "❌ FORCE: Error refreshing chat list", e);
                    }
                });
            } else {
                Log.w(TAG, "⚠️ FORCE: Cannot refresh - adapter is null or fragment not added");
            }
        }
}
