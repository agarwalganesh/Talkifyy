package com.example.talkifyy.adapter;


import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.talkifyy.ChatActivity;
import com.example.talkifyy.R;
import com.example.talkifyy.model.ChatroomModel;
import com.example.talkifyy.model.UserModel;
import com.example.talkifyy.services.ChatRestorationService;
import com.example.talkifyy.utils.AndroidUtil;
import com.example.talkifyy.utils.FirebaseUtil;
import com.example.talkifyy.utils.LocalDeletionUtil;
import com.example.talkifyy.utils.NotificationUtil;
import com.firebase.ui.firestore.FirestoreRecyclerAdapter;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;


public class RecentChatRecyclerAdapter extends FirestoreRecyclerAdapter<ChatroomModel,
        RecentChatRecyclerAdapter.ChatroomModelViewHolder> {

        private static final String TAG = "RecentChatAdapter";
        Context context;
        ChatContextMenuListener contextMenuListener;
        ChatRestorationService restorationService;
        
        // Instagram-style highlighting
        private String highlightedChatroomId = null;
        private String highlightedSenderId = null;

        public RecentChatRecyclerAdapter(@NonNull FirestoreRecyclerOptions<ChatroomModel> options, Context context) {
            super(options);
            this.context = context;
        }
        
        public void setChatContextMenuListener(ChatContextMenuListener listener) {
            this.contextMenuListener = listener;
        }
        
        public void setChatRestorationService(ChatRestorationService service) {
            this.restorationService = service;
        }
        
        // Cleanup method to prevent memory leaks
        public void cleanup() {
            this.contextMenuListener = null;
            this.restorationService = null;
            this.highlightedChatroomId = null;
            this.highlightedSenderId = null;
        }
        
        // Instagram-style highlighting for new messages
        public void highlightChatWithNewMessage(String senderId, String chatroomId) {
            this.highlightedSenderId = senderId;
            this.highlightedChatroomId = chatroomId;
            Log.d(TAG, "🎆 ADAPTER: Highlighting chat for new message - Sender: " + senderId + ", Chat: " + chatroomId);
            
            // Immediate update to show highlighting
            try {
                notifyDataSetChanged();
                Log.d(TAG, "✅ ADAPTER: Highlighting applied successfully");
            } catch (Exception e) {
                Log.e(TAG, "❌ ADAPTER: Error applying highlighting", e);
            }
            
            // Clear highlight after Instagram-style delay
            new android.os.Handler().postDelayed(() -> {
                try {
                    Log.d(TAG, "🔄 ADAPTER: Clearing highlight after delay");
                    this.highlightedSenderId = null;
                    this.highlightedChatroomId = null;
                    notifyDataSetChanged();
                    Log.d(TAG, "✅ ADAPTER: Highlight cleared successfully");
                } catch (Exception e) {
                    Log.e(TAG, "❌ ADAPTER: Error clearing highlight", e);
                }
            }, 2500); // Slightly longer highlight for better visibility
        }

        @Override
        protected void onBindViewHolder(@NonNull ChatroomModelViewHolder holder, int position, @NonNull ChatroomModel model) {
            // Get the chatroom ID from document snapshot (more reliable)
            String chatroomId = getSnapshots().getSnapshot(position).getId();
            
            // Check if chat is locally deleted - if so, check if it should be restored
            if (LocalDeletionUtil.isChatLocallyDeleted(context, chatroomId)) {
                // Check if there are new messages since the local deletion
                // If the last message timestamp is newer, restore the chat
                if (shouldRestoreChat(chatroomId, model)) {
                    Log.d(TAG, "Restoring locally deleted chat due to new message: " + chatroomId);
                    LocalDeletionUtil.restoreLocallyDeletedChat(context, chatroomId);
                } else {
                    Log.d(TAG, "Chat is locally deleted, hiding: " + chatroomId);
                    // Hide the entire item by setting zero height and invisible
                    holder.itemView.setVisibility(View.GONE);
                    holder.itemView.setLayoutParams(new RecyclerView.LayoutParams(0, 0));
                    return;
                }
            }
            
            // Make sure the item is visible (in case it was previously hidden)
            holder.itemView.setVisibility(View.VISIBLE);
            holder.itemView.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            
            // Check if this is a group chat or individual chat
            if (model.isGroup()) {
                // Handle group chat display
                handleGroupChatDisplay(holder, model, chatroomId);
            } else {
                // Handle individual chat display
                handleIndividualChatDisplay(holder, model, chatroomId);
            }
        }
        
        private void handleGroupChatDisplay(ChatroomModelViewHolder holder, ChatroomModel model, String chatroomId) {
            boolean lastMessageSentByMe = model.getLastMessageSenderId().equals(FirebaseUtil.currentUserId());
            
            // Set group name and description
            String groupName = (model.getGroupName() != null && !model.getGroupName().isEmpty()) 
                    ? model.getGroupName() : "Group Chat";
            holder.usernameText.setText(groupName);
            
            // Set group icon (use a default group icon)
            holder.profilePic.setImageResource(R.drawable.group_icon);
            
            // For group chats, use chatroom ID for notification count
            int unreadCount = NotificationUtil.getMessageCount(chatroomId);
            
            // Handle message display
            if (lastMessageSentByMe) {
                holder.lastMessageText.setText("You: " + model.getLastMessage());
                holder.unreadBadge.setVisibility(View.GONE);
            } else if (unreadCount > 1) {
                holder.lastMessageText.setText(unreadCount + " new messages");
                holder.lastMessageText.setTextColor(context.getResources().getColor(R.color.instagram_blue, null));
                holder.lastMessageText.setTypeface(null, android.graphics.Typeface.BOLD);
                holder.unreadBadge.setVisibility(View.VISIBLE);
                holder.unreadBadge.setText(unreadCount > 99 ? "99+" : String.valueOf(unreadCount));
            } else if (unreadCount == 1) {
                holder.lastMessageText.setText(model.getLastMessage());
                holder.lastMessageText.setTextColor(context.getResources().getColor(R.color.black, null));
                holder.lastMessageText.setTypeface(null, android.graphics.Typeface.BOLD);
                holder.unreadBadge.setVisibility(View.VISIBLE);
                holder.unreadBadge.setText("1");
            } else {
                holder.lastMessageText.setText(model.getLastMessage());
                holder.lastMessageText.setTextColor(context.getResources().getColor(R.color.gray, null));
                holder.lastMessageText.setTypeface(null, android.graphics.Typeface.NORMAL);
                holder.unreadBadge.setVisibility(View.GONE);
            }
            
            // Set timestamp
            holder.lastMessageTime.setText(FirebaseUtil.timestampToString(model.getLastMessageTimestamp()));
            
            // Apply highlighting for group chats if needed
            if (chatroomId != null && chatroomId.equals(highlightedChatroomId)) {
                // Highlight the entire group chat item
                try {
                    holder.itemView.setBackgroundColor(context.getResources().getColor(R.color.light_blue, null));
                    Log.d(TAG, "🎆 ADAPTER: Applied highlight to group chat: " + groupName + " (ID: " + chatroomId + ")");
                } catch (Exception e) {
                    Log.e(TAG, "❌ ADAPTER: Error applying group chat highlight", e);
                    holder.itemView.setBackground(context.getDrawable(R.drawable.edit_text_rounded_corner));
                }
            } else {
                // Normal background
                try {
                    holder.itemView.setBackground(context.getDrawable(R.drawable.edit_text_rounded_corner));
                } catch (Exception e) {
                    Log.e(TAG, "❌ ADAPTER: Error setting normal background", e);
                }
            }
            
            // Click listener for group chat
            holder.itemView.setOnClickListener(v -> {
                // Clear notification count for this group chat
                NotificationUtil.clearMessageCount(chatroomId);
                
                // Hide unread badge immediately
                holder.unreadBadge.setVisibility(View.GONE);
                
                // Navigate to group chat activity
                Intent intent = new Intent(context, ChatActivity.class);
                intent.putExtra("isGroup", true);
                intent.putExtra("chatroomId", chatroomId);
                intent.putExtra("groupName", groupName);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            });
            
            // Long press for group context menu
            holder.itemView.setOnLongClickListener(v -> {
                showGroupContextMenu(v, chatroomId, groupName);
                return true;
            });
            
            Log.d(TAG, "📱 Group: " + groupName + " - Unread count: " + unreadCount);
        }
        
        private void handleIndividualChatDisplay(ChatroomModelViewHolder holder, ChatroomModel model, String chatroomId) {
            FirebaseUtil.getOtherUserFromChatroom(model.getUserIds())
                    .get().addOnCompleteListener(task -> {
                        if(task.isSuccessful() && task.getResult() != null && task.getResult().exists()){
                            boolean lastMessageSentByMe = model.getLastMessageSenderId().equals(FirebaseUtil.currentUserId());

                            UserModel otherUserModel = task.getResult().toObject(UserModel.class);
                            
                            // Additional validation to ensure we have valid user data
                            if (otherUserModel == null) {
                                Log.e(TAG, "❌ Failed to parse user data from Firestore document");
                                handleFailedUserLoad(holder, model, chatroomId);
                                return;
                            }

                            // Handle deleted user profiles gracefully
                            final String displayName = (otherUserModel != null && otherUserModel.getUsername() != null) 
                                    ? otherUserModel.getUsername() 
                                    : "Unknown User";

                            // Load profile picture - prioritize Firestore URLs (always up-to-date)
                            if (otherUserModel != null && otherUserModel.getProfilePicUrl() != null && !otherUserModel.getProfilePicUrl().isEmpty()) {
                                AndroidUtil.setProfilePic(context, otherUserModel.getProfilePicUrl(), holder.profilePic);
                            } else {
                                // Use default profile picture for users without profile pictures
                                holder.profilePic.setImageResource(R.drawable.person_icon);
                            }

                            holder.usernameText.setText(displayName);
                            
                            // Instagram-style message display with count
                            if (otherUserModel != null && otherUserModel.getUserId() != null) {
                                int unreadCount = NotificationUtil.getMessageCount(otherUserModel.getUserId());
                                
                                if (lastMessageSentByMe) {
                                    // Show "You: message" for messages sent by current user
                                    holder.lastMessageText.setText("You: " + model.getLastMessage());
                                    // Hide badge for own messages
                                    holder.unreadBadge.setVisibility(View.GONE);
                                } else if (unreadCount > 1) {
                                    // Instagram-style: Show count in message area for multiple messages
                                    holder.lastMessageText.setText(unreadCount + " new messages");
                                    holder.lastMessageText.setTextColor(context.getResources().getColor(R.color.instagram_blue, null));
                                    holder.lastMessageText.setTypeface(null, android.graphics.Typeface.BOLD);
                                    
                                    // Also show badge
                                    holder.unreadBadge.setVisibility(View.VISIBLE);
                                    holder.unreadBadge.setText(unreadCount > 99 ? "99+" : String.valueOf(unreadCount));
                                } else if (unreadCount == 1) {
                                    // Single unread message - show the actual message
                                    holder.lastMessageText.setText(model.getLastMessage());
                                    holder.lastMessageText.setTextColor(context.getResources().getColor(R.color.black, null));
                                    holder.lastMessageText.setTypeface(null, android.graphics.Typeface.BOLD);
                                    
                                    // Show single message badge
                                    holder.unreadBadge.setVisibility(View.VISIBLE);
                                    holder.unreadBadge.setText("1");
                                } else {
                                    // No unread messages - show normal message
                                    holder.lastMessageText.setText(model.getLastMessage());
                                    holder.lastMessageText.setTextColor(context.getResources().getColor(R.color.gray, null));
                                    holder.lastMessageText.setTypeface(null, android.graphics.Typeface.NORMAL);
                                    holder.unreadBadge.setVisibility(View.GONE);
                                }
                                
                                // Instagram-style highlighting for new messages
                                if (chatroomId != null && chatroomId.equals(highlightedChatroomId) && 
                                    otherUserModel.getUserId() != null && otherUserModel.getUserId().equals(highlightedSenderId)) {
                                    // Highlight the entire chat item
                                    try {
                                        holder.itemView.setBackgroundColor(context.getResources().getColor(R.color.light_blue, null));
                                        Log.d(TAG, "🎆 ADAPTER: Applied highlight to individual chat: " + displayName + " (ID: " + chatroomId + ")");
                                    } catch (Exception e) {
                                        Log.e(TAG, "❌ ADAPTER: Error applying individual chat highlight", e);
                                        holder.itemView.setBackground(context.getDrawable(R.drawable.edit_text_rounded_corner));
                                    }
                                } else {
                                    // Normal background
                                    try {
                                        holder.itemView.setBackground(context.getDrawable(R.drawable.edit_text_rounded_corner));
                                    } catch (Exception e) {
                                        Log.e(TAG, "❌ ADAPTER: Error setting normal background for individual chat", e);
                                    }
                                }
                                
                                Log.d(TAG, "💬 " + displayName + " - Unread count: " + unreadCount + ", Highlighted: " + (chatroomId.equals(highlightedChatroomId)));
                            } else {
                                holder.lastMessageText.setText(model.getLastMessage());
                                holder.lastMessageText.setTextColor(context.getResources().getColor(R.color.gray, null));
                                holder.lastMessageText.setTypeface(null, android.graphics.Typeface.NORMAL);
                                holder.unreadBadge.setVisibility(View.GONE);
                                holder.itemView.setBackground(context.getDrawable(R.drawable.edit_text_rounded_corner));
                            }
                            
                            holder.lastMessageTime.setText(FirebaseUtil.timestampToString(model.getLastMessageTimestamp()));
                            
                            holder.itemView.setOnClickListener(v -> {
                                if (otherUserModel != null && otherUserModel.getUserId() != null) {
                                    try {
                                        // Clear notification count for this chat
                                        NotificationUtil.clearMessageCount(otherUserModel.getUserId());
                                        
                                        // Hide unread badge immediately
                                        holder.unreadBadge.setVisibility(View.GONE);
                                        
                                        // Navigate to chat activity with proper validation
                                        Intent intent = new Intent(context, ChatActivity.class);
                                        
                                        // Validate user data before passing
                                        if (isValidUserModel(otherUserModel)) {
                                            AndroidUtil.passUserModelAsIntent(intent, otherUserModel);
                                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                            
                                            Log.d(TAG, "✅ Starting ChatActivity with user: " + otherUserModel.getUsername() + " (ID: " + otherUserModel.getUserId() + ")");
                                            context.startActivity(intent);
                                        } else {
                                            Log.e(TAG, "❌ Invalid user model data, cannot open chat");
                                            Toast.makeText(context, "Unable to open chat - invalid user data", Toast.LENGTH_SHORT).show();
                                        }
                                    } catch (Exception e) {
                                        Log.e(TAG, "❌ Error opening chat", e);
                                        Toast.makeText(context, "Error opening chat", Toast.LENGTH_SHORT).show();
                                    }
                                } else {
                                    Log.w(TAG, "⚠️ User model is null or incomplete");
                                    Toast.makeText(context, "User no longer exists", Toast.LENGTH_SHORT).show();
                                }
                            });
                            
                            // Long press for context menu - always allow deletion even for deleted users
                            holder.itemView.setOnLongClickListener(v -> {
                                showContextMenu(v, chatroomId, otherUserModel, displayName);
                                return true;
                            });

                        } else {
                            // Handle Firebase query failure or missing user document
                            Log.e(TAG, "❌ Firebase query failed or user document doesn't exist for chatroom: " + chatroomId);
                            if (task.getException() != null) {
                                Log.e(TAG, "Firebase exception: ", task.getException());
                            }
                            handleFailedUserLoad(holder, model, chatroomId);
                        }
                    });
        }
        
        private void showGroupContextMenu(View anchorView, String chatroomId, String groupName) {
            // Add haptic feedback
            anchorView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            
            // Add press animation
            animatePress(anchorView, () -> {
                // Create popup window
                View popupView = LayoutInflater.from(context).inflate(R.layout.chat_context_popup, null);
                PopupWindow popupWindow = new PopupWindow(popupView,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        true);
                
                // Set popup properties
                popupWindow.setElevation(16f);
                popupWindow.setAnimationStyle(R.style.PopupAnimation);
                
                // Find popup options
                LinearLayout openChatOption = popupView.findViewById(R.id.open_chat_option);
                LinearLayout deleteChatOption = popupView.findViewById(R.id.delete_chat_option);
                
                // Set click listeners
                openChatOption.setOnClickListener(v -> {
                    popupWindow.dismiss();
                    // Open group chat
                    Intent intent = new Intent(context, ChatActivity.class);
                    intent.putExtra("isGroup", true);
                    intent.putExtra("chatroomId", chatroomId);
                    intent.putExtra("groupName", groupName);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                });
                
                deleteChatOption.setOnClickListener(v -> {
                    popupWindow.dismiss();
                    showGroupDeleteConfirmation(chatroomId, groupName);
                });
                
                // Calculate position to show popup near the item with proper margins
                int[] anchorLocation = new int[2];
                anchorView.getLocationOnScreen(anchorLocation);
                
                // Measure popup to get its size
                popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                int popupWidth = popupView.getMeasuredWidth();
                int popupHeight = popupView.getMeasuredHeight();
                
                // Get screen dimensions
                int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
                int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
                
                // Calculate optimal position
                int xOffset = anchorView.getWidth() - popupWidth - 20; // Show at right edge with margin
                int yOffset = -(anchorView.getHeight() / 2) - (popupHeight / 2); // Center vertically
                
                // Adjust if popup would go off screen
                if (anchorLocation[0] + anchorView.getWidth() + xOffset + popupWidth > screenWidth) {
                    xOffset = -popupWidth - 20; // Show on left side instead
                }
                
                popupWindow.showAsDropDown(anchorView, xOffset, yOffset);
                
                // Animate popup entrance
                animatePopupEntrance(popupView);
            });
        }
        
        private void showGroupDeleteConfirmation(String chatroomId, String groupName) {
            // For group chats, only allow "Delete for Me" (leave group)
            String[] options = {
                "Leave Group",
                "Cancel"
            };
            
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("Leave " + groupName + "?")
                    .setItems(options, (dialog, which) -> {
                        switch (which) {
                            case 0: // Leave Group
                                deleteChatLocally(chatroomId, groupName);
                                break;
                            case 1: // Cancel
                            default:
                                // Do nothing - dialog will dismiss
                                break;
                        }
                    });
            
            AlertDialog dialog = builder.create();
            dialog.show();
        }
        
        private void showContextMenu(View anchorView, String chatroomId, UserModel otherUser, String displayName) {
            // Add haptic feedback
            anchorView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            
            // Add press animation
            animatePress(anchorView, () -> {
                // Create popup window
                View popupView = LayoutInflater.from(context).inflate(R.layout.chat_context_popup, null);
                PopupWindow popupWindow = new PopupWindow(popupView,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        true);
                
                // Set popup properties
                popupWindow.setElevation(16f);
                popupWindow.setAnimationStyle(R.style.PopupAnimation);
                
                // Find popup options
                LinearLayout openChatOption = popupView.findViewById(R.id.open_chat_option);
                LinearLayout deleteChatOption = popupView.findViewById(R.id.delete_chat_option);
                
                // Set click listeners
                openChatOption.setOnClickListener(v -> {
                    popupWindow.dismiss();
                    if (otherUser != null) {
                        if (contextMenuListener != null) {
                            contextMenuListener.onOpenChat(otherUser);
                        } else {
                            // Fallback to direct navigation
                            Intent intent = new Intent(context, ChatActivity.class);
                            AndroidUtil.passUserModelAsIntent(intent, otherUser);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(intent);
                        }
                    } else {
                        Toast.makeText(context, "User no longer exists", Toast.LENGTH_SHORT).show();
                    }
                });
                
                deleteChatOption.setOnClickListener(v -> {
                    popupWindow.dismiss();
                    showDeleteConfirmation(chatroomId, otherUser, displayName);
                });
                
                // Calculate position to show popup near the item with proper margins
                int[] anchorLocation = new int[2];
                anchorView.getLocationOnScreen(anchorLocation);
                
                // Measure popup to get its size
                popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                int popupWidth = popupView.getMeasuredWidth();
                int popupHeight = popupView.getMeasuredHeight();
                
                // Get screen dimensions
                int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
                int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
                
                // Calculate optimal position
                int xOffset = anchorView.getWidth() - popupWidth - 20; // Show at right edge with margin
                int yOffset = -(anchorView.getHeight() / 2) - (popupHeight / 2); // Center vertically
                
                // Adjust if popup would go off screen
                if (anchorLocation[0] + anchorView.getWidth() + xOffset + popupWidth > screenWidth) {
                    xOffset = -popupWidth - 20; // Show on left side instead
                }
                
                popupWindow.showAsDropDown(anchorView, xOffset, yOffset);
                
                // Animate popup entrance
                animatePopupEntrance(popupView);
            });
        }
        
        private void showDeleteConfirmation(String chatroomId, UserModel otherUser, String displayName) {
            // Create clean options array without descriptions
            String[] options = {
                "Delete for Me",
                "Delete for Everyone",
                "Cancel"
            };
            
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("Delete Chat with " + displayName)
                    .setItems(options, (dialog, which) -> {
                        switch (which) {
                            case 0: // Delete for Me
                                deleteChatLocally(chatroomId, displayName);
                                break;
                            case 1: // Delete for Everyone
                                if (contextMenuListener != null) {
                                    contextMenuListener.onDeleteChat(chatroomId, otherUser);
                                } else {
                                    // Fallback to direct deletion
                                    deleteChatFallback(chatroomId, displayName);
                                }
                                break;
                            case 2: // Cancel
                            default:
                                // Do nothing - dialog will dismiss
                                break;
                        }
                    });
            
            AlertDialog dialog = builder.create();
            dialog.show();
        }
        
        private void deleteChatFallback(String chatroomId, String displayName) {
            FirebaseUtil.deleteChatConversation(chatroomId,
                    aVoid -> {
                        Toast.makeText(context, "Chat with " + displayName + " deleted", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Chat deleted successfully: " + chatroomId);
                    },
                    e -> {
                        Toast.makeText(context, "Failed to delete chat", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Failed to delete chat: " + chatroomId, e);
                    });
        }
        
        /**
         * Delete chat locally (only affects current user's device)
         * @param chatroomId Chat room ID to delete locally
         * @param displayName Display name for user feedback
         */
        private void deleteChatLocally(String chatroomId, String displayName) {
            Log.d(TAG, "Deleting chat locally: " + chatroomId);
            
            // Mark chat as locally deleted
            LocalDeletionUtil.markChatAsLocallyDeleted(context, chatroomId);
            
            // Add to restoration service monitoring
            if (restorationService != null) {
                restorationService.addChatToMonitoring(chatroomId);
            }
            
            // Show confirmation
            Toast.makeText(context, "Chat with " + displayName + " deleted for you", Toast.LENGTH_SHORT).show();
            
            // Find and hide the chat item with animation
            animateChatDeletion(chatroomId);
        }
        
        /**
         * Animate chat deletion by refreshing the adapter
         * @param chatroomId Chat room ID being deleted
         */
        private void animateChatDeletion(String chatroomId) {
            // Find the position of the chat
            for (int i = 0; i < getItemCount(); i++) {
                if (getSnapshots().getSnapshot(i).getId().equals(chatroomId)) {
                    // Notify that this item changed (will trigger rebind and hide the chat)
                    notifyItemChanged(i);
                    break;
                }
            }
        }
        
        private void animatePress(View view, Runnable onAnimationEnd) {
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.95f, 1f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.95f, 1f);
            
            scaleX.setDuration(150);
            scaleY.setDuration(150);
            scaleX.setInterpolator(new AccelerateDecelerateInterpolator());
            scaleY.setInterpolator(new AccelerateDecelerateInterpolator());
            
            scaleX.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (onAnimationEnd != null) {
                        onAnimationEnd.run();
                    }
                }
            });
            
            scaleX.start();
            scaleY.start();
        }
        
        private void animatePopupEntrance(View popupView) {
            popupView.setAlpha(0f);
            popupView.setScaleX(0.8f);
            popupView.setScaleY(0.8f);
            
            popupView.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(250)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        }
        
        /**
         * Handle cases where user data cannot be loaded from Firebase
         * @param holder ViewHolder to update
         * @param model ChatroomModel with basic data
         * @param chatroomId Chatroom ID for fallback navigation
         */
        private void handleFailedUserLoad(ChatroomModelViewHolder holder, ChatroomModel model, String chatroomId) {
            Log.w(TAG, "⚠️ Handling failed user load for chatroom: " + chatroomId);
            
            // Set fallback display data
            holder.usernameText.setText("Unknown User");
            holder.lastMessageText.setText(model.getLastMessage() != null ? model.getLastMessage() : "No messages");
            holder.lastMessageText.setTextColor(context.getResources().getColor(R.color.gray, null));
            holder.lastMessageText.setTypeface(null, android.graphics.Typeface.NORMAL);
            holder.lastMessageTime.setText(FirebaseUtil.timestampToString(model.getLastMessageTimestamp()));
            holder.unreadBadge.setVisibility(View.GONE);
            holder.profilePic.setImageResource(R.drawable.person_icon);
            holder.itemView.setBackground(context.getDrawable(R.drawable.edit_text_rounded_corner));
            
            // Set click listener with fallback navigation using chatroomId
            holder.itemView.setOnClickListener(v -> {
                Log.w(TAG, "⚠️ Attempting fallback navigation for unknown user chat: " + chatroomId);
                try {
                    // Try to create a minimal UserModel for navigation
                    UserModel fallbackUser = createFallbackUserModel(chatroomId, model);
                    if (fallbackUser != null) {
                        Intent intent = new Intent(context, ChatActivity.class);
                        AndroidUtil.passUserModelAsIntent(intent, fallbackUser);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        
                        Log.d(TAG, "✅ Starting ChatActivity with fallback user for chatroom: " + chatroomId);
                        context.startActivity(intent);
                    } else {
                        Toast.makeText(context, "Cannot open chat - user data unavailable", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "❌ Error in fallback navigation", e);
                    Toast.makeText(context, "Unable to open chat", Toast.LENGTH_SHORT).show();
                }
            });
            
            // Long press for context menu - allow deletion even for unknown users
            holder.itemView.setOnLongClickListener(v -> {
                showContextMenu(v, chatroomId, null, "Unknown User");
                return true;
            });
        }
        
        /**
         * Create a fallback UserModel when user data cannot be loaded
         * @param chatroomId Chatroom ID
         * @param model ChatroomModel for additional context
         * @return Minimal UserModel or null if can't create one
         */
        private UserModel createFallbackUserModel(String chatroomId, ChatroomModel model) {
            try {
                // Extract other user ID from chatroom ID or user list
                String currentUserId = FirebaseUtil.currentUserId();
                String otherUserId = null;
                
                if (model.getUserIds() != null && model.getUserIds().size() == 2) {
                    // Find the user ID that's not the current user
                    for (String userId : model.getUserIds()) {
                        if (!userId.equals(currentUserId)) {
                            otherUserId = userId;
                            break;
                        }
                    }
                }
                
                if (otherUserId != null) {
                    UserModel fallbackUser = new UserModel();
                    fallbackUser.setUserId(otherUserId);
                    fallbackUser.setUsername("Unknown User");
                    fallbackUser.setPhone("");
                    fallbackUser.setFcmToken("");
                    fallbackUser.setProfilePicUrl("");
                    
                    Log.d(TAG, "✅ Created fallback user model with ID: " + otherUserId);
                    return fallbackUser;
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ Error creating fallback user model", e);
            }
            
            Log.w(TAG, "⚠️ Could not create fallback user model for chatroom: " + chatroomId);
            return null;
        }
        
        /**
         * Validate user model to ensure it has all required data for chat navigation
         * @param userModel User model to validate
         * @return true if user model is valid for navigation
         */
        private boolean isValidUserModel(UserModel userModel) {
            if (userModel == null) {
                Log.e(TAG, "❌ User model is null");
                return false;
            }
            
            // Be more lenient with userId validation to prevent crashes
            if (userModel.getUserId() == null || userModel.getUserId().isEmpty()) {
                Log.w(TAG, "⚠️ User ID is null or empty, but allowing navigation anyway");
                // Don't return false - this could break existing functionality
                // Instead, set a temporary ID if completely missing
                if (userModel.getUserId() == null) {
                    userModel.setUserId("unknown_" + System.currentTimeMillis());
                }
            }
            
            if (userModel.getUsername() == null || userModel.getUsername().isEmpty()) {
                Log.w(TAG, "⚠️ Username is null or empty, setting default");
                // Set a default username to prevent issues
                userModel.setUsername("Unknown User");
            }
            
            // Phone and FCM token can be null, that's okay
            Log.d(TAG, "✅ User model validation passed for: " + userModel.getUsername() + " (ID: " + userModel.getUserId() + ")");
            return true;
        }
        
        /**
         * Check if a locally deleted chat should be restored due to new messages
         * @param chatroomId Chat room ID
         * @param model Current chatroom model with latest data
         * @return true if chat should be restored
         */
        private boolean shouldRestoreChat(String chatroomId, ChatroomModel model) {
            // Get the timestamp when the chat was locally deleted
            long deletionTimestamp = LocalDeletionUtil.getChatDeletionTimestamp(context, chatroomId);
            
            if (deletionTimestamp == 0) {
                // No deletion timestamp found, restore the chat
                Log.d(TAG, "No deletion timestamp found, restoring chat: " + chatroomId);
                return true;
            }
            
            // Check if the last message timestamp is newer than deletion timestamp
            if (model.getLastMessageTimestamp() != null) {
                long lastMessageTime = model.getLastMessageTimestamp().toDate().getTime();
                boolean hasNewMessage = lastMessageTime > deletionTimestamp;
                
                // Also check if the last message is not from the current user
                // (to handle cases where user sends a message after deleting)
                String currentUserId = FirebaseUtil.currentUserId();
                boolean lastMessageFromOtherUser = !model.getLastMessageSenderId().equals(currentUserId);
                
                // Restore if there's a new message, especially from the other user
                boolean shouldRestore = hasNewMessage && (lastMessageFromOtherUser || hasNewMessage);
                
                Log.d(TAG, "Chat restoration check - Deletion time: " + deletionTimestamp + 
                    ", Last message time: " + lastMessageTime + 
                    ", From other user: " + lastMessageFromOtherUser +
                    ", Should restore: " + shouldRestore);
                
                return shouldRestore;
            }
            
            return false;
        }

        @NonNull
        @Override
        public ChatroomModelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context).inflate(R.layout.recent_chat_recycler_row,parent,false);
            return new ChatroomModelViewHolder(view);
        }

   

    class ChatroomModelViewHolder extends RecyclerView.ViewHolder{
            TextView usernameText;
            TextView lastMessageText;
            TextView lastMessageTime;
            ImageView profilePic;
            TextView unreadBadge;

            public ChatroomModelViewHolder(@NonNull View itemView) {
                super(itemView);
                usernameText = itemView.findViewById(R.id.user_name_text);
                lastMessageText = itemView.findViewById(R.id.last_message_text);
                lastMessageTime = itemView.findViewById(R.id.last_message_time_text);
                profilePic = itemView.findViewById(R.id.profile_pic_image_view);
                unreadBadge = itemView.findViewById(R.id.unread_count_badge);
            }
        }
}
