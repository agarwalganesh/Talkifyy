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
import com.example.talkifyy.utils.AndroidUtil;
import com.example.talkifyy.utils.FirebaseUtil;
import com.example.talkifyy.utils.LocalDeletionUtil;
import com.firebase.ui.firestore.FirestoreRecyclerAdapter;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;


public class RecentChatRecyclerAdapter extends FirestoreRecyclerAdapter<ChatroomModel,
        RecentChatRecyclerAdapter.ChatroomModelViewHolder> {

        private static final String TAG = "RecentChatAdapter";
        Context context;
        ChatContextMenuListener contextMenuListener;

        public RecentChatRecyclerAdapter(@NonNull FirestoreRecyclerOptions<ChatroomModel> options, Context context) {
            super(options);
            this.context = context;
        }
        
        public void setChatContextMenuListener(ChatContextMenuListener listener) {
            this.contextMenuListener = listener;
        }
        
        // Cleanup method to prevent memory leaks
        public void cleanup() {
            this.contextMenuListener = null;
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
            
            FirebaseUtil.getOtherUserFromChatroom(model.getUserIds())
                    .get().addOnCompleteListener(task -> {
                        if(task.isSuccessful()){
                            boolean lastMessageSentByMe = model.getLastMessageSenderId().equals(FirebaseUtil.currentUserId());

                            UserModel otherUserModel = task.getResult().toObject(UserModel.class);

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
                            if(lastMessageSentByMe)
                                holder.lastMessageText.setText("You : "+model.getLastMessage());
                            else
                                holder.lastMessageText.setText(model.getLastMessage());
                            holder.lastMessageTime.setText(FirebaseUtil.timestampToString(model.getLastMessageTimestamp()));
                            
                            holder.itemView.setOnClickListener(v -> {
                                if (otherUserModel != null) {
                                    //navigate to chat activity only if user exists
                                    Intent intent = new Intent(context, ChatActivity.class);
                                    AndroidUtil.passUserModelAsIntent(intent,otherUserModel);
                                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    context.startActivity(intent);
                                } else {
                                    Toast.makeText(context, "User no longer exists", Toast.LENGTH_SHORT).show();
                                }
                            });
                            
                            // Long press for context menu - always allow deletion even for deleted users
                            holder.itemView.setOnLongClickListener(v -> {
                                showContextMenu(v, chatroomId, otherUserModel, displayName);
                                return true;
                            });

                        }
                    });
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
            // Create options array with descriptions
            String[] options = {
                "Delete for Me\nThis will only remove the chat from your device.",
                "Delete for Everyone\nThis will delete the chat for all participants.",
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
                return true;
            }
            
            // Check if the last message timestamp is newer than deletion timestamp
            if (model.getLastMessageTimestamp() != null) {
                long lastMessageTime = model.getLastMessageTimestamp().toDate().getTime();
                boolean hasNewMessage = lastMessageTime > deletionTimestamp;
                
                Log.d(TAG, "Chat restoration check - Deletion time: " + deletionTimestamp + 
                    ", Last message time: " + lastMessageTime + ", Should restore: " + hasNewMessage);
                
                return hasNewMessage;
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

            public ChatroomModelViewHolder(@NonNull View itemView) {
                super(itemView);
                usernameText = itemView.findViewById(R.id.user_name_text);
                lastMessageText = itemView.findViewById(R.id.last_message_text);
                lastMessageTime = itemView.findViewById(R.id.last_message_time_text);
                profilePic = itemView.findViewById(R.id.profile_pic_image_view);
            }
        }
}
