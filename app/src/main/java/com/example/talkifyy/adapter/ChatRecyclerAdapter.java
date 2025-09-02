package com.example.talkifyy.adapter;


import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.talkifyy.R;
import com.example.talkifyy.model.ChatMessageModel;
import com.example.talkifyy.utils.FirebaseUtil;
import com.example.talkifyy.utils.LocalDeletionUtil;
import com.example.talkifyy.utils.WhatsAppStyleDeletionUtil;
import com.firebase.ui.firestore.FirestoreRecyclerAdapter;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.firebase.firestore.FirebaseFirestoreException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class ChatRecyclerAdapter extends FirestoreRecyclerAdapter<ChatMessageModel, ChatRecyclerAdapter.ChatModelViewHolder> {

        private static final String TAG = "ChatRecyclerAdapter";
        Context context;
        UnsendMessageListener unsendMessageListener;
        MessageDeletionListener messageDeletionListener;
        String chatroomId;
        long unsendTimeWindowMinutes = 10; // Default 10 minutes
        boolean isGroupChat = false; // Track if this is a group chat
        
        // Multi-select functionality
        private boolean isSelectionMode = false;
        private Set<String> selectedMessageIds = new HashSet<>();

        public ChatRecyclerAdapter(@NonNull FirestoreRecyclerOptions<ChatMessageModel> options, Context context, 
                                 String chatroomId, UnsendMessageListener listener) {
            super(options);
            this.context = context;
            this.chatroomId = chatroomId;
            this.unsendMessageListener = listener;
        }
        
        public void setUnsendTimeWindow(long minutes) {
            this.unsendTimeWindowMinutes = minutes;
        }
        
        /**
         * Set the WhatsApp-style deletion listener
         * @param listener MessageDeletionListener for handling deletion operations
         */
        public void setMessageDeletionListener(MessageDeletionListener listener) {
            this.messageDeletionListener = listener;
        }
        
        /**
         * Configure whether this is a group chat
         * @param isGroupChat true if this is a group chat, false for 1-on-1
         */
        public void setGroupChat(boolean isGroupChat) {
            this.isGroupChat = isGroupChat;
        }

        @Override
        protected void onBindViewHolder(@NonNull ChatModelViewHolder holder, int position, @NonNull ChatMessageModel model) {
            Log.d(TAG, "onBindViewHolder called for position: " + position + ", message: " + model.getMessage() + ", senderId: " + model.getSenderId());
            
            // Get document ID for this message (needed for unsend functionality)
            String messageId = getSnapshots().getSnapshot(position).getId();
            
            // Check if message is locally deleted - if so, hide it
            if (LocalDeletionUtil.isMessageLocallyDeleted(context, chatroomId, messageId)) {
                Log.d(TAG, "Message is locally deleted, hiding: " + messageId);
                // Hide the entire item by setting zero height and invisible
                holder.itemView.setVisibility(View.GONE);
                holder.itemView.setLayoutParams(new RecyclerView.LayoutParams(0, 0));
                return;
            }
            
            // Make sure the item is visible (in case it was previously hidden)
            holder.itemView.setVisibility(View.VISIBLE);
            holder.itemView.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            
            boolean isSelected = selectedMessageIds.contains(messageId);
            boolean isCurrentUser = model.getSenderId().equals(FirebaseUtil.currentUserId());
            
            if(isCurrentUser){
                Log.d(TAG, "Message from current user (right side)");
                setupRightMessage(holder, model, messageId, isSelected);
            } else {
                Log.d(TAG, "Message from other user (left side)");
                setupLeftMessage(holder, model, messageId, isSelected);
            }
        }
        
        private void setupRightMessage(ChatModelViewHolder holder, ChatMessageModel model, String messageId, boolean isSelected) {
            // Show right container, hide left
            holder.rightMessageContainer.setVisibility(View.VISIBLE);
            holder.leftMessageContainer.setVisibility(View.GONE);
            
            // Set message text with deletion placeholder if needed
            String displayText = model.getDisplayMessage();
            holder.rightChatTextview.setText(displayText);
            
            // Style deleted messages differently
            if (model.isDeletedForEveryone() || model.isUnsent()) {
                holder.rightChatTextview.setTypeface(null, Typeface.ITALIC);
                holder.rightChatTextview.setTextColor(Color.parseColor("#B0B0B0")); // Light gray
            } else {
                holder.rightChatTextview.setTypeface(null, Typeface.NORMAL);
                holder.rightChatTextview.setTextColor(Color.WHITE);
            }
            
            // Handle selection mode and animations
            setupSelectionState(holder.rightMessageCheckbox, holder.rightChatLayout, isSelected, true);
            
            // Set up click listeners
            setupClickListeners(holder.rightChatLayout, holder.rightMessageCheckbox, messageId, model);
        }
        
        private void setupLeftMessage(ChatModelViewHolder holder, ChatMessageModel model, String messageId, boolean isSelected) {
            // Show left container, hide right
            holder.leftMessageContainer.setVisibility(View.VISIBLE);
            holder.rightMessageContainer.setVisibility(View.GONE);
            
            // Set message text with deletion placeholder if needed
            String displayText = model.getDisplayMessage();
            holder.leftChatTextview.setText(displayText);
            
            // Style deleted messages differently
            if (model.isDeletedForEveryone() || model.isUnsent()) {
                holder.leftChatTextview.setTypeface(null, Typeface.ITALIC);
                holder.leftChatTextview.setTextColor(Color.parseColor("#B0B0B0")); // Light gray
            } else {
                holder.leftChatTextview.setTypeface(null, Typeface.NORMAL);
                holder.leftChatTextview.setTextColor(Color.WHITE);
            }
            
            // Handle selection mode and animations
            setupSelectionState(holder.leftMessageCheckbox, holder.leftChatLayout, isSelected, false);
            
            // Set up click listeners
            setupClickListeners(holder.leftChatLayout, holder.leftMessageCheckbox, messageId, model);
        }
        
        private void setupSelectionState(CheckBox checkbox, LinearLayout messageLayout, boolean isSelected, boolean isRightSide) {
            if (isSelectionMode) {
                // Show checkbox with animation if not already visible
                if (checkbox.getVisibility() != View.VISIBLE) {
                    animateCheckboxIn(checkbox);
                }
                
                // Update checkbox state without triggering listener
                checkbox.setOnCheckedChangeListener(null);
                checkbox.setChecked(isSelected);
                
                // Apply selection visual feedback
                applySelectionVisuals(messageLayout, isSelected);
            } else {
                // Hide checkbox with animation if visible
                if (checkbox.getVisibility() == View.VISIBLE) {
                    animateCheckboxOut(checkbox);
                }
                
                // Remove selection visuals
                removeSelectionVisuals(messageLayout);
            }
        }
        
        private void setupClickListeners(LinearLayout messageLayout, CheckBox checkbox, String messageId, ChatMessageModel model) {
            if (isSelectionMode) {
                // Selection mode: tap to toggle selection
                messageLayout.setOnClickListener(v -> {
                    animateMessageSelection(messageLayout, !selectedMessageIds.contains(messageId));
                    toggleMessageSelection(messageId);
                });
                
                checkbox.setOnClickListener(v -> {
                    animateMessageSelection(messageLayout, checkbox.isChecked());
                    toggleMessageSelection(messageId);
                });
                
                // Long press still works for additional selection
                messageLayout.setOnLongClickListener(v -> {
                    animateMessageSelection(messageLayout, !selectedMessageIds.contains(messageId));
                    toggleMessageSelection(messageId);
                    return true;
                });
            } else {
                // Normal mode: no click, long press for menu
                messageLayout.setOnClickListener(null);
                
                messageLayout.setOnLongClickListener(v -> {
                    showUnsendPopup(v, messageId, model);
                    return true;
                });
            }
        }
        
        private void animateCheckboxIn(CheckBox checkbox) {
            checkbox.setVisibility(View.VISIBLE);
            checkbox.setAlpha(0.0f);
            checkbox.setScaleX(0.5f);
            checkbox.setScaleY(0.5f);
            
            checkbox.animate()
                    .alpha(1.0f)
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(250)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        }
        
        private void animateCheckboxOut(CheckBox checkbox) {
            checkbox.animate()
                    .alpha(0.0f)
                    .scaleX(0.5f)
                    .scaleY(0.5f)
                    .setDuration(200)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .withEndAction(() -> checkbox.setVisibility(View.INVISIBLE))
                    .start();
        }
        
        private void animateMessageSelection(LinearLayout messageLayout, boolean selected) {
            if (selected) {
                // Selection animation: slight scale up + elevation
                messageLayout.animate()
                        .scaleX(1.02f)
                        .scaleY(1.02f)
                        .translationZ(8f)
                        .setDuration(150)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .start();
            } else {
                // Deselection animation: scale back + remove elevation
                messageLayout.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .translationZ(2f)
                        .setDuration(150)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .start();
            }
        }
        
        private void applySelectionVisuals(LinearLayout messageLayout, boolean isSelected) {
            if (isSelected) {
                // Selected state: higher elevation and slight scale
                messageLayout.setElevation(8f);
                messageLayout.setScaleX(1.02f);
                messageLayout.setScaleY(1.02f);
                messageLayout.setAlpha(0.9f);
            } else {
                // Default state: normal elevation and scale
                messageLayout.setElevation(2f);
                messageLayout.setScaleX(1.0f);
                messageLayout.setScaleY(1.0f);
                messageLayout.setAlpha(1.0f);
            }
        }
        
        private void removeSelectionVisuals(LinearLayout messageLayout) {
            // Animate back to normal state
            messageLayout.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .alpha(1.0f)
                    .translationZ(2f)
                    .setDuration(200)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .withEndAction(() -> messageLayout.setElevation(2f))
                    .start();
        }
        
        private void showUnsendPopup(View view, String messageId, ChatMessageModel model) {
            PopupMenu popup = new PopupMenu(context, view);
            
            String currentUserId = FirebaseUtil.currentUserId();
            boolean isGroupAdmin = messageDeletionListener != null ? 
                messageDeletionListener.isUserAdmin(chatroomId) : false;
            
            // Check which options should be available based on WhatsApp rules
            boolean canDeleteForMe = WhatsAppStyleDeletionUtil.canShowDeleteForMe(
                messageId, chatroomId, context);
            boolean canDeleteForEveryone = WhatsAppStyleDeletionUtil.canShowDeleteForEveryone(
                model, currentUserId, isGroupAdmin, isGroupChat);
            
            // Clear existing menu and add options based on rules
            popup.getMenu().clear();
            
            if (canDeleteForMe) {
                popup.getMenu().add(0, R.id.menu_delete_locally, 0, "Delete for Me")
                    .setIcon(R.drawable.icon_delete);
            }
            
            if (canDeleteForEveryone) {
                String title = "Delete for Everyone";
                // Add time remaining info for own messages
                if (model.getSenderId().equals(currentUserId)) {
                    String timeRemaining = WhatsAppStyleDeletionUtil.getRecallTimeRemaining(model);
                    if (timeRemaining != null) {
                        title += " (" + timeRemaining + ")";
                    }
                }
                popup.getMenu().add(0, R.id.menu_unsend, 0, title)
                    .setIcon(R.drawable.icon_back);
            }
            
            // Always add multi-select option
            popup.getMenu().add(0, R.id.menu_multi_select, 0, "Select Messages")
                .setIcon(R.drawable.icon_back);
            
            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.menu_delete_locally && canDeleteForMe) {
                    if (messageDeletionListener != null) {
                        messageDeletionListener.onDeleteForMe(messageId, chatroomId);
                    } else {
                        deleteMessageLocally(messageId); // Fallback
                    }
                    WhatsAppStyleDeletionUtil.logDeletionAction(
                        "DELETE_FOR_ME_SINGLE", messageId, chatroomId, currentUserId, true);
                    return true;
                } else if (item.getItemId() == R.id.menu_unsend && canDeleteForEveryone) {
                    if (messageDeletionListener != null) {
                        messageDeletionListener.onDeleteForEveryone(messageId, chatroomId);
                    } else if (unsendMessageListener != null) {
                        unsendMessageListener.onUnsendMessage(messageId, chatroomId); // Fallback
                    }
                    WhatsAppStyleDeletionUtil.logDeletionAction(
                        "DELETE_FOR_EVERYONE_SINGLE", messageId, chatroomId, currentUserId, true);
                    return true;
                } else if (item.getItemId() == R.id.menu_multi_select) {
                    startSelectionMode();
                    toggleMessageSelection(messageId);
                    return true;
                }
                return false;
            });
            
            // Only show popup if there are available options
            if (popup.getMenu().size() > 0) {
                popup.show();
            } else {
                Log.d(TAG, "No deletion options available for message: " + messageId);
                Toast.makeText(context, "No actions available for this message", Toast.LENGTH_SHORT).show();
            }
        }
        
        // Multi-select functionality methods
        public void startSelectionMode() {
            isSelectionMode = true;
            selectedMessageIds.clear();
            notifyDataSetChanged();
            if (unsendMessageListener != null) {
                unsendMessageListener.onSelectionModeChanged(true, 0);
            }
        }
        
        public void exitSelectionMode() {
            isSelectionMode = false;
            selectedMessageIds.clear();
            notifyDataSetChanged();
            if (unsendMessageListener != null) {
                unsendMessageListener.onSelectionModeChanged(false, 0);
            }
        }
        
        public boolean isSelectionMode() {
            return isSelectionMode;
        }
        
        public int getSelectedCount() {
            return selectedMessageIds.size();
        }
        
        private void toggleMessageSelection(String messageId) {
            if (selectedMessageIds.contains(messageId)) {
                selectedMessageIds.remove(messageId);
            } else {
                selectedMessageIds.add(messageId);
            }
            
            if (selectedMessageIds.isEmpty()) {
                exitSelectionMode();
            } else {
                // Only update the specific item that changed instead of the entire dataset
                updateSelectionForMessage(messageId);
                if (unsendMessageListener != null) {
                    unsendMessageListener.onSelectionModeChanged(true, selectedMessageIds.size());
                }
            }
        }
        
        private void updateSelectionForMessage(String messageId) {
            // Find the position of the changed message and update only that item
            for (int i = 0; i < getItemCount(); i++) {
                if (getSnapshots().getSnapshot(i).getId().equals(messageId)) {
                    notifyItemChanged(i);
                    break;
                }
            }
        }
        
        public void selectAll() {
            selectedMessageIds.clear();
            for (int i = 0; i < getItemCount(); i++) {
                String messageId = getSnapshots().getSnapshot(i).getId();
                selectedMessageIds.add(messageId);
            }
            notifyDataSetChanged();
            if (unsendMessageListener != null) {
                unsendMessageListener.onSelectionModeChanged(true, selectedMessageIds.size());
            }
        }
        
        public void deleteSelectedMessages() {
            if (!selectedMessageIds.isEmpty() && unsendMessageListener != null) {
                List<String> messagesToDelete = new ArrayList<>(selectedMessageIds);
                unsendMessageListener.onUnsendMultipleMessages(messagesToDelete, chatroomId);
                exitSelectionMode();
            }
        }
        
        // Local deletion methods
        
        /**
         * Delete a single message locally (only affects current user's device)
         * @param messageId Message ID to delete locally
         */
        private void deleteMessageLocally(String messageId) {
            Log.d(TAG, "Deleting message locally: " + messageId);
            
            // Mark message as locally deleted
            LocalDeletionUtil.markMessageAsLocallyDeleted(context, chatroomId, messageId);
            
            // Show confirmation
            Toast.makeText(context, "Message deleted for you", Toast.LENGTH_SHORT).show();
            
            // Find and remove the message from view with animation
            animateMessageDeletion(messageId);
        }
        
        /**
         * Delete multiple messages locally (only affects current user's device)
         * @param messageIds List of message IDs to delete locally
         */
        public void deleteSelectedMessagesLocally() {
            if (!selectedMessageIds.isEmpty()) {
                Set<String> messagesToDelete = new HashSet<>(selectedMessageIds);
                
                Log.d(TAG, "Deleting " + messagesToDelete.size() + " messages locally");
                
                // Mark all messages as locally deleted
                LocalDeletionUtil.markMessagesAsLocallyDeleted(context, chatroomId, messagesToDelete);
                
                // Show confirmation
                Toast.makeText(context, messagesToDelete.size() + " messages deleted for you", Toast.LENGTH_SHORT).show();
                
                // Exit selection mode and refresh
                exitSelectionMode();
                
                // Refresh the adapter to hide deleted messages
                notifyDataSetChanged();
            }
        }
        
        /**
         * Check if a message should be shown (not locally deleted)
         * @param messageId Message ID to check
         * @return true if message should be shown, false if locally deleted
         */
        public boolean shouldShowMessage(String messageId) {
            return !LocalDeletionUtil.isMessageLocallyDeleted(context, chatroomId, messageId);
        }
        
        /**
         * Animate message deletion with fade out effect
         * @param messageId Message ID being deleted
         */
        private void animateMessageDeletion(String messageId) {
            // Find the position of the message
            for (int i = 0; i < getItemCount(); i++) {
                if (getSnapshots().getSnapshot(i).getId().equals(messageId)) {
                    // Notify that this item changed (will trigger rebind and hide the message)
                    notifyItemChanged(i);
                    break;
                }
            }
        }

        @NonNull
        @Override
        public ChatModelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Log.d(TAG, "onCreateViewHolder called");
            View view = LayoutInflater.from(context).inflate(R.layout.chat_message_recycler_row,parent,false);
            return new ChatModelViewHolder(view);
        }
        
        @Override
        public void onDataChanged() {
            super.onDataChanged();
            Log.d(TAG, "onDataChanged called, itemCount: " + getItemCount());
        }
        
        @Override
        public void onError(@NonNull FirebaseFirestoreException e) {
            super.onError(e);
            Log.e(TAG, "Firestore RecyclerAdapter error", e);
        }

        class ChatModelViewHolder extends RecyclerView.ViewHolder{

            FrameLayout leftMessageContainer, rightMessageContainer;
            LinearLayout leftChatLayout,rightChatLayout;
            TextView leftChatTextview,rightChatTextview;
            CheckBox leftMessageCheckbox, rightMessageCheckbox;

            public ChatModelViewHolder(@NonNull View itemView) {
                super(itemView);

                leftMessageContainer = itemView.findViewById(R.id.left_message_container);
                rightMessageContainer = itemView.findViewById(R.id.right_message_container);
                leftChatLayout = itemView.findViewById(R.id.left_chat_layout);
                rightChatLayout = itemView.findViewById(R.id.right_chat_layout);
                leftChatTextview = itemView.findViewById(R.id.left_chat_textview);
                rightChatTextview = itemView.findViewById(R.id.right_chat_textview);
                leftMessageCheckbox = itemView.findViewById(R.id.left_message_checkbox);
                rightMessageCheckbox = itemView.findViewById(R.id.right_message_checkbox);
            }
        }
}
