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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.GridLayoutManager;
import android.graphics.Rect;
import android.content.Intent;

import com.example.talkifyy.R;
import com.example.talkifyy.model.ChatMessageModel;
import com.example.talkifyy.utils.FirebaseUtil;
import com.example.talkifyy.utils.LocalDeletionUtil;
import com.example.talkifyy.utils.MessageReactionManager;
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
        EditMessageListener editMessageListener;
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
         * Set the message edit listener
         * @param listener EditMessageListener for handling message editing
         */
        public void setEditMessageListener(EditMessageListener listener) {
            this.editMessageListener = listener;
        }
        
        /**
         * Configure whether this is a group chat
         * @param isGroupChat true if this is a group chat, false for 1-on-1
         */
        public void setGroupChat(boolean isGroupChat) {
            Log.d(TAG, "⚙️ setGroupChat called: " + isGroupChat + " (was: " + this.isGroupChat + ")");
            if (this.isGroupChat != isGroupChat) {
                this.isGroupChat = isGroupChat;
                // Refresh the display to show/hide usernames
                notifyDataSetChanged();
                Log.d(TAG, "🔄 Adapter refreshed due to group chat status change");
            }
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
            
            setupTextMessage(holder, model, messageId, true);
            
            // Handle selection mode and animations
            setupSelectionState(holder.rightMessageCheckbox, holder.rightChatLayout, isSelected, true);
            
            // Update reactions display
            MessageReactionManager.updateReactionsDisplay(holder.rightReactionsContainer, model, context,
                new MessageReactionManager.OnReactionClickListener() {
                    @Override
                    public void onReactionSelected(String emoji) {
                        handleReactionSelected(messageId, emoji);
                    }
                    
                    @Override
                    public void onReactionToggle(String emoji) {
                        handleReactionToggle(messageId, emoji);
                    }
                });
            
            // Set up click listeners
            setupClickListeners(holder.rightChatLayout, holder.rightMessageCheckbox, messageId, model);
        }
        
        private void setupLeftMessage(ChatModelViewHolder holder, ChatMessageModel model, String messageId, boolean isSelected) {
            // Show left container, hide right
            holder.leftMessageContainer.setVisibility(View.VISIBLE);
            holder.rightMessageContainer.setVisibility(View.GONE);
            
            setupTextMessage(holder, model, messageId, false);
            
            // Handle selection mode and animations
            setupSelectionState(holder.leftMessageCheckbox, holder.leftChatLayout, isSelected, false);
            
            // Update reactions display
            MessageReactionManager.updateReactionsDisplay(holder.leftReactionsContainer, model, context,
                new MessageReactionManager.OnReactionClickListener() {
                    @Override
                    public void onReactionSelected(String emoji) {
                        handleReactionSelected(messageId, emoji);
                    }
                    
                    @Override
                    public void onReactionToggle(String emoji) {
                        handleReactionToggle(messageId, emoji);
                    }
                });
            
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
                // Normal mode: double tap to show reaction picker, long press for menu
                setupDoubleClickListener(messageLayout, messageId, model);
                
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
            boolean canEditMessage = model.canBeEdited(currentUserId);
            
            // Clear existing menu and add options based on rules
            popup.getMenu().clear();
            
            // Different options for images vs text messages
            if (model.isImageMessage()) {
                // Image-specific options
                popup.getMenu().add(0, R.id.menu_image_info, 0, "Info")
                    .setIcon(R.drawable.icon_search);
                
                popup.getMenu().add(0, R.id.menu_image_forward, 0, "Forward")
                    .setIcon(R.drawable.icon_back);
                
                popup.getMenu().add(0, R.id.menu_image_save, 0, "Save")
                    .setIcon(R.drawable.icon_delete);
                
                popup.getMenu().add(0, R.id.menu_image_reply, 0, "Reply")
                    .setIcon(R.drawable.icon_search);
            } else {
                // Add Edit option for text messages only
                if (canEditMessage) {
                    popup.getMenu().add(0, R.id.menu_edit_message, 0, "Edit")
                        .setIcon(R.drawable.icon_search);
                }
            }
            
            if (canDeleteForMe) {
                popup.getMenu().add(0, R.id.menu_delete_locally, 0, "Delete for Me")
                    .setIcon(R.drawable.icon_delete);
            }
            
            if (canDeleteForEveryone) {
                popup.getMenu().add(0, R.id.menu_unsend, 0, "Delete for Everyone")
                    .setIcon(R.drawable.icon_back);
            }
            
            // Always add multi-select option
            popup.getMenu().add(0, R.id.menu_multi_select, 0, "Select Messages")
                .setIcon(R.drawable.icon_back);
            
            popup.setOnMenuItemClickListener(item -> {
                // Handle image-specific menu items
                if (item.getItemId() == R.id.menu_image_info) {
                    showImageInfo(messageId, model);
                    return true;
                } else if (item.getItemId() == R.id.menu_image_forward) {
                    forwardImage(messageId, model);
                    return true;
                } else if (item.getItemId() == R.id.menu_image_save) {
                    saveImageToGallery(messageId, model);
                    return true;
                } else if (item.getItemId() == R.id.menu_image_reply) {
                    replyToImage(messageId, model);
                    return true;
                } else if (item.getItemId() == R.id.menu_edit_message && canEditMessage) {
                    if (editMessageListener != null) {
                        editMessageListener.onEditMessage(messageId, model.getMessage());
                    }
                    return true;
                } else if (item.getItemId() == R.id.menu_delete_locally && canDeleteForMe) {
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
        
        // Image-specific action methods
        
        /**
         * Show image information dialog
         */
        private void showImageInfo(String messageId, ChatMessageModel model) {
            Log.d(TAG, "Showing image info for: " + messageId);
            
            StringBuilder info = new StringBuilder();
            info.append("Image Information\n\n");
            
            // Basic message info
            info.append("Sent by: ").append(getSenderName(model)).append("\n");
            info.append("Date: ").append(FirebaseUtil.timestampToString(model.getTimestamp())).append("\n");
            
            // Image-specific info
            if (model.isSingleImage()) {
                info.append("Type: Single Image\n");
                if (model.getImageUrl() != null) {
                    info.append("URL: ").append(model.getImageUrl().substring(0, Math.min(50, model.getImageUrl().length()))).append("...\n");
                }
            } else if (model.isMultipleImages()) {
                info.append("Type: Multiple Images\n");
                info.append("Count: ").append(model.getImageCount()).append(" images\n");
            }
            
            // Caption if available
            if (model.getImageCaption() != null && !model.getImageCaption().isEmpty()) {
                info.append("Caption: ").append(model.getImageCaption()).append("\n");
            }
            
            // Message status
            if (model.isEdited()) {
                info.append("Status: Edited\n");
            }
            
            // Show info dialog
            androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(context);
            builder.setTitle("Image Info")
                   .setMessage(info.toString())
                   .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                   .show();
        }
        
        /**
         * Forward image to another chat
         */
        private void forwardImage(String messageId, ChatMessageModel model) {
            Log.d(TAG, "Forwarding image: " + messageId);
            
            // Show toast for now - in a real app, this would open a contact picker
            Toast.makeText(context, "Forward feature coming soon!", Toast.LENGTH_SHORT).show();
            
            // TODO: Implement forward functionality
            // This would typically:
            // 1. Open a contact/chat selection dialog
            // 2. Allow user to select recipient(s)
            // 3. Forward the image message to selected chat(s)
        }
        
        /**
         * Save image to device gallery
         */
        private void saveImageToGallery(String messageId, ChatMessageModel model) {
            Log.d(TAG, "Saving image to gallery: " + messageId);
            
            try {
                if (model.isSingleImage() && model.getImageUrl() != null) {
                    // For single image
                    saveImageToDevice(model.getImageUrl());
                } else if (model.isMultipleImages() && model.getImageUrls() != null) {
                    // For multiple images, save all
                    Toast.makeText(context, "Saving " + model.getImageUrls().size() + " images...", Toast.LENGTH_SHORT).show();
                    for (String imageUrl : model.getImageUrls()) {
                        saveImageToDevice(imageUrl);
                    }
                } else {
                    Toast.makeText(context, "No image to save", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error saving image to gallery", e);
                Toast.makeText(context, "Error saving image", Toast.LENGTH_SHORT).show();
            }
        }
        
        /**
         * Reply to image message
         */
        private void replyToImage(String messageId, ChatMessageModel model) {
            Log.d(TAG, "Replying to image: " + messageId);
            
            // Show toast for now - in a real app, this would set reply context
            String imageType = model.isSingleImage() ? "image" : model.getImageCount() + " images";
            Toast.makeText(context, "Replying to " + imageType + " from " + getSenderName(model), Toast.LENGTH_SHORT).show();
            
            // TODO: Implement reply functionality
            // This would typically:
            // 1. Set the reply context in ChatActivity
            // 2. Show the original image as reply reference
            // 3. Focus the message input field
        }
        
        /**
         * Save a single image to device gallery
         */
        private void saveImageToDevice(String imageUrl) {
            // For now, just show toast - in a real app, this would:
            // 1. Check storage permissions
            // 2. Download the image if it's a URL
            // 3. Save to gallery using MediaStore API
            
            if (imageUrl.startsWith("content://") || imageUrl.startsWith("file://")) {
                // Local file - could copy to gallery
                Toast.makeText(context, "Image saved to gallery!", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Would save local image: " + imageUrl);
            } else if (imageUrl.startsWith("http")) {
                // Remote URL - would need to download first
                Toast.makeText(context, "Downloading and saving image...", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Would download and save remote image: " + imageUrl);
            } else {
                Log.w(TAG, "Unknown image URL format: " + imageUrl);
                Toast.makeText(context, "Unable to save this image", Toast.LENGTH_SHORT).show();
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
            TextView leftUsernameTextview, rightUsernameTextview; // Added username TextViews
            TextView leftEditedIndicator, rightEditedIndicator; // Added edited indicators
            
            // Image views
            ImageView leftImageView, rightImageView;
            RecyclerView leftImagesRecycler, rightImagesRecycler;
            TextView leftImageCaption, rightImageCaption;
            
            CheckBox leftMessageCheckbox, rightMessageCheckbox;
            LinearLayout leftReactionsContainer, rightReactionsContainer;
            

            public ChatModelViewHolder(@NonNull View itemView) {
                super(itemView);

                leftMessageContainer = itemView.findViewById(R.id.left_message_container);
                rightMessageContainer = itemView.findViewById(R.id.right_message_container);
                leftChatLayout = itemView.findViewById(R.id.left_chat_layout);
                rightChatLayout = itemView.findViewById(R.id.right_chat_layout);
                leftChatTextview = itemView.findViewById(R.id.left_chat_textview);
                rightChatTextview = itemView.findViewById(R.id.right_chat_textview);
                leftUsernameTextview = itemView.findViewById(R.id.left_username_textview); // Added
                rightUsernameTextview = itemView.findViewById(R.id.right_username_textview); // Added
                leftEditedIndicator = itemView.findViewById(R.id.left_edited_indicator); // Added
                rightEditedIndicator = itemView.findViewById(R.id.right_edited_indicator); // Added
                
                // Initialize image views
                leftImageView = itemView.findViewById(R.id.left_image_view);
                rightImageView = itemView.findViewById(R.id.right_image_view);
                leftImagesRecycler = itemView.findViewById(R.id.left_images_recycler);
                rightImagesRecycler = itemView.findViewById(R.id.right_images_recycler);
                leftImageCaption = itemView.findViewById(R.id.left_image_caption);
                rightImageCaption = itemView.findViewById(R.id.right_image_caption);
                
                leftMessageCheckbox = itemView.findViewById(R.id.left_message_checkbox);
                rightMessageCheckbox = itemView.findViewById(R.id.right_message_checkbox);
                leftReactionsContainer = itemView.findViewById(R.id.left_reactions_container);
                rightReactionsContainer = itemView.findViewById(R.id.right_reactions_container);
            }
        }
        
        private void setupTextMessage(ChatModelViewHolder holder, ChatMessageModel model, String messageId, boolean isRightSide) {
            LinearLayout chatLayout = isRightSide ? holder.rightChatLayout : holder.leftChatLayout;
            TextView chatTextView = isRightSide ? holder.rightChatTextview : holder.leftChatTextview;
            TextView usernameTextView = isRightSide ? holder.rightUsernameTextview : holder.leftUsernameTextview;
            TextView editedIndicator = isRightSide ? holder.rightEditedIndicator : holder.leftEditedIndicator;
            
            // Image views
            ImageView imageView = isRightSide ? holder.rightImageView : holder.leftImageView;
            RecyclerView imagesRecycler = isRightSide ? holder.rightImagesRecycler : holder.leftImagesRecycler;
            TextView imageCaption = isRightSide ? holder.rightImageCaption : holder.leftImageCaption;
            
            CheckBox messageCheckbox = isRightSide ? holder.rightMessageCheckbox : holder.leftMessageCheckbox;
            LinearLayout reactionsContainer = isRightSide ? holder.rightReactionsContainer : holder.leftReactionsContainer;
            
            // Handle different message types
            if (model.isImageMessage()) {
                setupImageMessage(holder, model, messageId, isRightSide);
            } else {
                setupTextOnlyMessage(holder, model, messageId, isRightSide);
            }
            
            // Handle username display for group chats
            setupUsernameDisplay(holder, model, messageId, isRightSide, usernameTextView);
            
            // Set message text with deletion placeholder if needed
            String displayText = model.getDisplayMessage();
            chatTextView.setText(displayText);
            
            // Show/hide edited indicator
            if (model.isEdited() && !model.isUnsent() && !model.isDeletedForEveryone()) {
                editedIndicator.setVisibility(View.VISIBLE);
            } else {
                editedIndicator.setVisibility(View.GONE);
            }
            
            // Style deleted messages differently
            if (model.isDeletedForEveryone() || model.isUnsent()) {
                chatTextView.setTypeface(null, Typeface.ITALIC);
                chatTextView.setTextColor(Color.parseColor("#B0B0B0")); // Light gray
            } else {
                chatTextView.setTypeface(null, Typeface.NORMAL);
                chatTextView.setTextColor(Color.WHITE);
            }
            
            boolean isSelected = selectedMessageIds.contains(messageId);
            
            // Handle selection mode and animations
            setupSelectionState(messageCheckbox, chatLayout, isSelected, isRightSide);
            
            // Update reactions display
            MessageReactionManager.updateReactionsDisplay(reactionsContainer, model, context,
                new MessageReactionManager.OnReactionClickListener() {
                    @Override
                    public void onReactionSelected(String emoji) {
                        handleReactionSelected(messageId, emoji);
                    }
                    
                    @Override
                    public void onReactionToggle(String emoji) {
                        handleReactionToggle(messageId, emoji);
                    }
                });
            
            // This method is now split into setupImageMessage and setupTextOnlyMessage
        }
        
        private void setupImageMessage(ChatModelViewHolder holder, ChatMessageModel model, String messageId, boolean isRightSide) {
            // Get views based on side
            TextView chatTextView = isRightSide ? holder.rightChatTextview : holder.leftChatTextview;
            ImageView imageView = isRightSide ? holder.rightImageView : holder.leftImageView;
            RecyclerView imagesRecycler = isRightSide ? holder.rightImagesRecycler : holder.leftImagesRecycler;
            TextView imageCaption = isRightSide ? holder.rightImageCaption : holder.leftImageCaption;
            TextView editedIndicator = isRightSide ? holder.rightEditedIndicator : holder.leftEditedIndicator;
            LinearLayout chatLayout = isRightSide ? holder.rightChatLayout : holder.leftChatLayout;
            CheckBox messageCheckbox = isRightSide ? holder.rightMessageCheckbox : holder.leftMessageCheckbox;
            LinearLayout reactionsContainer = isRightSide ? holder.rightReactionsContainer : holder.leftReactionsContainer;
            
            // Hide text view for image messages
            chatTextView.setVisibility(View.GONE);
            
            if (model.isSingleImage()) {
                // Single image
                imageView.setVisibility(View.VISIBLE);
                imagesRecycler.setVisibility(View.GONE);
                
                // Load image using enhanced loading method
                String imageUrl = model.getImageUrl();
                Log.d(TAG, "Loading image: " + imageUrl);
                
                loadImageSafely(imageView, imageUrl);
                        
                    // Add click listener to open full-screen viewer
                    imageView.setOnClickListener(v -> {
                        if (!isSelectionMode) {
                            Log.d(TAG, "Image clicked, opening viewer: " + imageUrl);
                            openImageViewer(imageUrl, model.getImageCaption(), getSenderName(model));
                        } else {
                            // In selection mode, treat as message selection
                            animateMessageSelection(chatLayout, !selectedMessageIds.contains(messageId));
                            toggleMessageSelection(messageId);
                        }
                    });
                    
                    // Add long-press listener for deletion menu
                    imageView.setOnLongClickListener(v -> {
                        if (!isSelectionMode) {
                            Log.d(TAG, "Image long-pressed, showing delete menu");
                            showUnsendPopup(v, messageId, model);
                        } else {
                            // In selection mode, toggle selection
                            animateMessageSelection(chatLayout, !selectedMessageIds.contains(messageId));
                            toggleMessageSelection(messageId);
                        }
                        return true;
                    });
                    
            } else if (model.isMultipleImages()) {
                // Multiple images
                imageView.setVisibility(View.GONE);
                imagesRecycler.setVisibility(View.VISIBLE);
                
                // Setup RecyclerView adapter for multiple images
                java.util.List<String> imageUrls = model.getImageUrls();
                if (imageUrls != null && !imageUrls.isEmpty()) {
                    Log.d(TAG, "Setting up multiple images grid: " + imageUrls.size() + " images");
                    
                    // Create and set adapter for images grid
                    ChatImagesAdapter imagesAdapter = new ChatImagesAdapter(imageUrls, new ChatImagesAdapter.OnImageClickListener() {
                        @Override
                        public void onImageClick(String imageUrl, int position) {
                            if (!isSelectionMode) {
                                Log.d(TAG, "Multiple image clicked: " + imageUrl + " at position: " + position);
                                openSwipeableImageViewer(imageUrls, position, model.getImageCaption(), getSenderName(model));
                            } else {
                                // In selection mode, treat as message selection
                                animateMessageSelection(chatLayout, !selectedMessageIds.contains(messageId));
                                toggleMessageSelection(messageId);
                            }
                        }
                        
                        @Override
                        public void onImageLongClick(String imageUrl, int position) {
                            if (!isSelectionMode) {
                                Log.d(TAG, "Multiple image long-pressed, showing delete menu");
                                showUnsendPopup(imagesRecycler, messageId, model);
                            } else {
                                // In selection mode, toggle selection
                                animateMessageSelection(chatLayout, !selectedMessageIds.contains(messageId));
                                toggleMessageSelection(messageId);
                            }
                        }
                    });
                    
                    // Setup grid layout (2 columns for WhatsApp-like appearance)
                    GridLayoutManager gridLayoutManager = new GridLayoutManager(context, 2);
                    imagesRecycler.setLayoutManager(gridLayoutManager);
                    imagesRecycler.setAdapter(imagesAdapter);
                    
                    // Add some spacing between images
                    if (imagesRecycler.getItemDecorationCount() == 0) {
                        int spacing = (int) (4 * context.getResources().getDisplayMetrics().density); // 4dp spacing
                        imagesRecycler.addItemDecoration(new GridSpacingItemDecoration(2, spacing, true));
                    }
                } else {
                    Log.w(TAG, "Multiple images message but no image URLs found");
                    imagesRecycler.setVisibility(View.GONE);
                }
            }
            
            // Handle caption
            if (model.getImageCaption() != null && !model.getImageCaption().isEmpty()) {
                imageCaption.setVisibility(View.VISIBLE);
                imageCaption.setText(model.getImageCaption());
            } else {
                imageCaption.setVisibility(View.GONE);
            }
            
            // Show/hide edited indicator
            if (model.isEdited() && !model.isUnsent() && !model.isDeletedForEveryone()) {
                editedIndicator.setVisibility(View.VISIBLE);
            } else {
                editedIndicator.setVisibility(View.GONE);
            }
            
            // Setup username for group chats
            TextView usernameTextView = isRightSide ? holder.rightUsernameTextview : holder.leftUsernameTextview;
            setupUsernameDisplay(holder, model, messageId, isRightSide, usernameTextView);
            
            // Handle selection state
            boolean isSelected = selectedMessageIds.contains(messageId);
            setupSelectionState(messageCheckbox, chatLayout, isSelected, isRightSide);
            
            // Setup reactions
            MessageReactionManager.updateReactionsDisplay(reactionsContainer, model, context,
                new MessageReactionManager.OnReactionClickListener() {
                    @Override
                    public void onReactionSelected(String emoji) {
                        handleReactionSelected(messageId, emoji);
                    }
                    
                    @Override
                    public void onReactionToggle(String emoji) {
                        handleReactionToggle(messageId, emoji);
                    }
                });
                
            // Set up click listeners
            setupClickListeners(chatLayout, messageCheckbox, messageId, model);
        }
        
        private void setupTextOnlyMessage(ChatModelViewHolder holder, ChatMessageModel model, String messageId, boolean isRightSide) {
            // Get views based on side
            TextView chatTextView = isRightSide ? holder.rightChatTextview : holder.leftChatTextview;
            ImageView imageView = isRightSide ? holder.rightImageView : holder.leftImageView;
            RecyclerView imagesRecycler = isRightSide ? holder.rightImagesRecycler : holder.leftImagesRecycler;
            TextView imageCaption = isRightSide ? holder.rightImageCaption : holder.leftImageCaption;
            TextView editedIndicator = isRightSide ? holder.rightEditedIndicator : holder.leftEditedIndicator;
            LinearLayout chatLayout = isRightSide ? holder.rightChatLayout : holder.leftChatLayout;
            CheckBox messageCheckbox = isRightSide ? holder.rightMessageCheckbox : holder.leftMessageCheckbox;
            LinearLayout reactionsContainer = isRightSide ? holder.rightReactionsContainer : holder.leftReactionsContainer;
            
            // Show text view, hide image views
            chatTextView.setVisibility(View.VISIBLE);
            imageView.setVisibility(View.GONE);
            imagesRecycler.setVisibility(View.GONE);
            imageCaption.setVisibility(View.GONE);
            
            // Set message text with deletion placeholder if needed
            String displayText = model.getDisplayMessage();
            chatTextView.setText(displayText);
            
            // Style deleted messages differently
            if (model.isDeletedForEveryone() || model.isUnsent()) {
                chatTextView.setTypeface(null, Typeface.ITALIC);
                chatTextView.setTextColor(Color.parseColor("#B0B0B0")); // Light gray
            } else {
                chatTextView.setTypeface(null, Typeface.NORMAL);
                chatTextView.setTextColor(Color.WHITE);
            }
            
            // Show/hide edited indicator
            if (model.isEdited() && !model.isUnsent() && !model.isDeletedForEveryone()) {
                editedIndicator.setVisibility(View.VISIBLE);
            } else {
                editedIndicator.setVisibility(View.GONE);
            }
            
            // Setup username for group chats
            TextView usernameTextView = isRightSide ? holder.rightUsernameTextview : holder.leftUsernameTextview;
            setupUsernameDisplay(holder, model, messageId, isRightSide, usernameTextView);
            
            // Handle selection state
            boolean isSelected = selectedMessageIds.contains(messageId);
            setupSelectionState(messageCheckbox, chatLayout, isSelected, isRightSide);
            
            // Setup reactions
            MessageReactionManager.updateReactionsDisplay(reactionsContainer, model, context,
                new MessageReactionManager.OnReactionClickListener() {
                    @Override
                    public void onReactionSelected(String emoji) {
                        handleReactionSelected(messageId, emoji);
                    }
                    
                    @Override
                    public void onReactionToggle(String emoji) {
                        handleReactionToggle(messageId, emoji);
                    }
                });
                
            // Set up click listeners
            setupClickListeners(chatLayout, messageCheckbox, messageId, model);
        }
        
        /**
         * Setup username display for group chats
         */
        private void setupUsernameDisplay(ChatModelViewHolder holder, ChatMessageModel model, 
                                         String messageId, boolean isRightSide, TextView usernameTextView) {
            // Show usernames for all messages in group chats
            Log.d(TAG, "🔍 setupUsernameDisplay - isGroupChat: " + isGroupChat + ", isRightSide: " + isRightSide + ", senderId: " + model.getSenderId());
            if (isGroupChat) {
                String senderId = model.getSenderId();
                String currentUserId = FirebaseUtil.currentUserId();
                
                if (senderId != null) {
                    if (senderId.equals(currentUserId)) {
                        // For our own messages, show "You" instead of fetching from Firebase
                        usernameTextView.setText("You");
                        usernameTextView.setVisibility(View.VISIBLE);
                        Log.d(TAG, "📄 Username set to 'You' for current user message");
                    } else {
                        // For other users' messages, fetch username from Firestore
                        FirebaseUtil.allUserCollectionReference().document(senderId)
                                .get()
                                .addOnSuccessListener(documentSnapshot -> {
                                    if (documentSnapshot.exists()) {
                                        try {
                                            com.example.talkifyy.model.UserModel sender = documentSnapshot.toObject(com.example.talkifyy.model.UserModel.class);
                                        if (sender != null && sender.getUsername() != null) {
                                            usernameTextView.setText(sender.getUsername());
                                            usernameTextView.setVisibility(View.VISIBLE);
                                            Log.d(TAG, "📄 Username set to '" + sender.getUsername() + "' for other user message");
                                            } else {
                                                usernameTextView.setText("Unknown User");
                                                usernameTextView.setVisibility(View.VISIBLE);
                                            }
                                        } catch (Exception e) {
                                            Log.e(TAG, "Error parsing user data for username display", e);
                                            usernameTextView.setText("Unknown User");
                                            usernameTextView.setVisibility(View.VISIBLE);
                                        }
                                    } else {
                                        usernameTextView.setText("Unknown User");
                                        usernameTextView.setVisibility(View.VISIBLE);
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to fetch username for senderId: " + senderId, e);
                                    usernameTextView.setText("Unknown User");
                                    usernameTextView.setVisibility(View.VISIBLE);
                                });
                    }
                } else {
                    usernameTextView.setText("Unknown User");
                    usernameTextView.setVisibility(View.VISIBLE);
                }
            } else {
                // Not a group chat - hide username
                usernameTextView.setVisibility(View.GONE);
                Log.d(TAG, "❌ Username hidden (not a group chat)");
            }
        }
        
        /**
         * Open full-screen image viewer for single images
         */
        private void openImageViewer(String imageUrl, String caption, String senderName) {
            try {
                Intent intent = new Intent(context, com.example.talkifyy.ImageViewerActivity.class);
                intent.putExtra("imageUrl", imageUrl);
                intent.putExtra("imageCaption", caption != null ? caption : "");
                intent.putExtra("senderName", senderName != null ? senderName : "");
                
                // Add chatroom information for proper back navigation
                intent.putExtra("chatroomId", chatroomId);
                
                context.startActivity(intent);
                Log.d(TAG, "Started ImageViewerActivity with chatroomId: " + chatroomId);
            } catch (Exception e) {
                Log.e(TAG, "Error opening image viewer", e);
                Toast.makeText(context, "Unable to open image", Toast.LENGTH_SHORT).show();
            }
        }
        
        /**
         * Open swipeable image viewer for multiple images
         */
        private void openSwipeableImageViewer(java.util.List<String> imageUrls, int currentPosition, String caption, String senderName) {
            try {
                Intent intent = new Intent(context, com.example.talkifyy.SwipeableImageViewerActivity.class);
                
                // Convert List<String> to ArrayList<String> for intent
                java.util.ArrayList<String> imageUrlsList = new java.util.ArrayList<>(imageUrls);
                intent.putStringArrayListExtra("imageUrls", imageUrlsList);
                intent.putExtra("currentPosition", currentPosition);
                intent.putExtra("imageCaption", caption != null ? caption : "");
                intent.putExtra("senderName", senderName != null ? senderName : "");
                
                // Add chatroom information for proper back navigation
                intent.putExtra("chatroomId", chatroomId);
                
                context.startActivity(intent);
                Log.d(TAG, "Started SwipeableImageViewerActivity with " + imageUrls.size() + " images, starting at position " + currentPosition);
            } catch (Exception e) {
                Log.e(TAG, "Error opening swipeable image viewer", e);
                Toast.makeText(context, "Unable to open images", Toast.LENGTH_SHORT).show();
            }
        }
        
        /**
         * Get sender name for display
         */
        private String getSenderName(ChatMessageModel model) {
            if (model.getSenderId().equals(FirebaseUtil.currentUserId())) {
                return "You";
            }
            
            // For now, return "Contact" - in a real app, you'd fetch the actual name
            // This could be enhanced to cache user names or fetch from Firestore
            return "Contact";
        }
        
        // ChatImagesAdapter for displaying multiple images in a grid
        private static class ChatImagesAdapter extends RecyclerView.Adapter<ChatImagesAdapter.ImageViewHolder> {
            private java.util.List<String> imageUrls;
            private OnImageClickListener clickListener;
            
            public interface OnImageClickListener {
                void onImageClick(String imageUrl, int position);
                void onImageLongClick(String imageUrl, int position);
            }
            
            public ChatImagesAdapter(java.util.List<String> imageUrls, OnImageClickListener clickListener) {
                this.imageUrls = imageUrls;
                this.clickListener = clickListener;
                
                // Preload images to improve reliability
                preloadImages();
            }
            
            /**
             * Preload images to ensure they're accessible
             */
            private void preloadImages() {
                if (imageUrls == null || imageUrls.isEmpty()) return;
                
                for (int i = 0; i < imageUrls.size(); i++) {
                    String imageUrl = imageUrls.get(i);
                    Log.d("ChatImagesAdapter", "Preloading image " + (i + 1) + ": " + imageUrl);
                    
                    try {
                        if (imageUrl.startsWith("content://")) {
                            android.net.Uri uri = android.net.Uri.parse(imageUrl);
                            // This will cache the image in Glide's memory cache
                            // We don't need the result, just trigger the loading
                        }
                    } catch (Exception e) {
                        Log.w("ChatImagesAdapter", "Failed to preload image " + (i + 1), e);
                    }
                }
            }
            
            @NonNull
            @Override
            public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                ImageView imageView = new ImageView(parent.getContext());
                // Set larger size for grid images to be more visible
                int imageSize = (int) (100 * parent.getContext().getResources().getDisplayMetrics().density); // 100dp
                ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(imageSize, imageSize);
                imageView.setLayoutParams(layoutParams);
                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                // Remove background - we want to show the actual images
                imageView.setBackgroundColor(0x00000000); // Transparent background
                return new ImageViewHolder(imageView);
            }
            
            @Override
            public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
                String imageUrl = imageUrls.get(position);
                Log.d("ChatImagesAdapter", "Loading image at position " + position + ": " + imageUrl);
                
                try {
                    android.net.Uri imageUri = null;
                    
                    // Handle different URI formats - prioritize local URIs
                    if (imageUrl.startsWith("content://") || imageUrl.startsWith("file://")) {
                        imageUri = android.net.Uri.parse(imageUrl);
                        Log.d("ChatImagesAdapter", "Parsed local URI: " + imageUri);
                        
                        // Check if URI is accessible
                        try {
                            android.content.Context context = holder.imageView.getContext();
                            java.io.InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
                            if (inputStream != null) {
                                inputStream.close();
                                Log.d("ChatImagesAdapter", "URI is accessible: " + imageUri);
                            } else {
                                Log.w("ChatImagesAdapter", "URI returned null input stream: " + imageUri);
                            }
                        } catch (Exception uriException) {
                            Log.e("ChatImagesAdapter", "URI not accessible: " + imageUri, uriException);
                            // Try to copy the image to cache as fallback
                            try {
                                imageUri = copyImageToCache(holder.imageView.getContext(), imageUri);
                                Log.d("ChatImagesAdapter", "Copied image to cache: " + imageUri);
                            } catch (Exception copyException) {
                                Log.e("ChatImagesAdapter", "Failed to copy image to cache", copyException);
                            }
                        }
                        
                    } else if (imageUrl.startsWith("http")) {
                        Log.d("ChatImagesAdapter", "Loading remote URL: " + imageUrl);
                    } else {
                        Log.w("ChatImagesAdapter", "Unknown image URL format: " + imageUrl);
                    }

                    // Use safe loading approach similar to single images
                    loadMultipleImageSafely(holder.imageView, imageUrl);`
                        
                } catch (Exception e) {
                    Log.e("ChatImagesAdapter", "Exception loading image: " + imageUrl, e);
                    // Hide the image view instead of showing placeholder
                    holder.imageView.setVisibility(View.GONE);
                }
                
                // Set click listener
                holder.imageView.setOnClickListener(v -> {
                    if (clickListener != null) {
                        clickListener.onImageClick(imageUrl, position);
                    }
                });
                
                // Set long-click listener for deletion menu
                holder.imageView.setOnLongClickListener(v -> {
                    if (clickListener != null) {
                        clickListener.onImageLongClick(imageUrl, position);
                    }
                    return true;
                });
            }
            
            @Override
            public int getItemCount() {
                return imageUrls != null ? imageUrls.size() : 0;
            }
            
            static class ImageViewHolder extends RecyclerView.ViewHolder {
                ImageView imageView;
                
                public ImageViewHolder(@NonNull View itemView) {
                    super(itemView);
                    this.imageView = (ImageView) itemView;
                }
            }
            
            /**
             * Load image with retry mechanism for better reliability
             */
            private void loadImageWithRetry(ImageViewHolder holder, android.net.Uri imageUri, String originalUrl, int retryCount) {
                final int maxRetries = 2;
                
                com.bumptech.glide.Glide.with(holder.imageView.getContext())
                    .load(imageUri)
                    .centerCrop()
                    .placeholder(android.R.color.transparent)
                    .error(android.R.color.transparent)
                    .transform(new com.bumptech.glide.load.resource.bitmap.RoundedCorners(8))
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                    .skipMemoryCache(false)
                    .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                        @Override
                        public boolean onLoadFailed(com.bumptech.glide.load.engine.GlideException e, Object model, 
                            com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, 
                            boolean isFirstResource) {
                            Log.e("ChatImagesAdapter", "Failed to load image (attempt " + (retryCount + 1) + "): " + originalUrl, e);
                            
                            if (retryCount < maxRetries) {
                                // Retry with cache fallback
                                try {
                                    android.net.Uri cachedUri = copyImageToCache(holder.imageView.getContext(), imageUri);
                                    loadImageWithRetry(holder, cachedUri, originalUrl, retryCount + 1);
                                    return true; // Consume the error, we're retrying
                                } catch (Exception cacheException) {
                                    Log.e("ChatImagesAdapter", "Cache fallback failed", cacheException);
                                }
                            }
                            
                            // Final failure - hide image view instead of showing placeholder
                            holder.imageView.setVisibility(View.GONE);
                            return true; // Consume error
                        }
                        
                        @Override
                        public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model,
                            com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                            com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                            Log.d("ChatImagesAdapter", "Successfully loaded image: " + originalUrl);
                            // Remove background once image is loaded
                            holder.imageView.setBackgroundColor(0x00000000);
                            return false;
                        }
                    })
                    .into(holder.imageView);
            }
            
        /**
         * MEMORY-OPTIMIZED: Load grid image with aggressive optimization to prevent crashes
         */
        private void loadMultipleImageSafely(ImageView imageView, String imageUrl) {
            if (imageUrl == null || imageUrl.isEmpty()) {
                imageView.setVisibility(View.GONE);
                return;
            }
            
            Log.d("ChatImagesAdapter", "🖼️ Loading MEMORY-OPTIMIZED grid image: " + imageUrl);
            
            try {
                // MEMORY OPTIMIZATION: Clear existing image first
                imageView.setImageDrawable(null);
                
                // Make image view visible and set transparent background
                imageView.setVisibility(View.VISIBLE);
                imageView.setBackgroundColor(0x00000000);
                
                Object loadSource = imageUrl.startsWith("content://") || imageUrl.startsWith("file://") ?
                    android.net.Uri.parse(imageUrl) : imageUrl;
                
                com.bumptech.glide.Glide.with(imageView.getContext())
                    .load(loadSource)
                    .centerCrop()
                    .placeholder(android.R.color.transparent)
                    .error(android.R.color.transparent)
                    // CRITICAL MEMORY OPTIMIZATIONS FOR GRID:
                    .override(80, 80)  // VERY SMALL for grid to prevent memory crashes
                    // Memory format optimization
                    .transform(new com.bumptech.glide.load.resource.bitmap.RoundedCorners(4)) // Smaller radius
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.RESOURCE)
                    .skipMemoryCache(false)
                    .dontAnimate() // No animations for grid
                    .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                        @Override
                        public boolean onLoadFailed(com.bumptech.glide.load.engine.GlideException e, Object model, 
                            com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, 
                            boolean isFirstResource) {
                            Log.w("ChatImagesAdapter", "⚠️ Grid image load failed, trying MINIMAL fallback: " + imageUrl, e);
                            
                            // EXTREME FALLBACK: Ultra minimal settings
                            try {
                                com.bumptech.glide.Glide.with(imageView.getContext())
                                    .load(imageUrl)
                                    .centerCrop()
                                    .placeholder(android.R.color.transparent)
                                    .error(android.R.color.transparent)
                                    .override(60, 60)  // ULTRA SMALL
                                    // Ultra memory format
                                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                                    .skipMemoryCache(true)
                                    .dontAnimate()
                                    .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                                        @Override
                                        public boolean onLoadFailed(com.bumptech.glide.load.engine.GlideException e2, Object model2, 
                                            com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target2, 
                                            boolean isFirstResource2) {
                                            Log.e("ChatImagesAdapter", "❌ ULTRA FALLBACK FAILED - hiding grid image: " + imageUrl, e2);
                                            imageView.setVisibility(View.GONE);
                                            return true;
                                        }
                                        
                                        @Override
                                        public boolean onResourceReady(android.graphics.drawable.Drawable resource2, Object model2,
                                            com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target2,
                                            com.bumptech.glide.load.DataSource dataSource2, boolean isFirstResource2) {
                                            Log.d("ChatImagesAdapter", "✅ ULTRA FALLBACK grid image loaded: " + imageUrl);
                                            return false;
                                        }
                                    })
                                    .into(imageView);
                            } catch (Exception fallbackEx) {
                                Log.e("ChatImagesAdapter", "❌ Grid fallback completely failed", fallbackEx);
                                imageView.setVisibility(View.GONE);
                            }
                                
                            return true; // Consume the first error
                        }
                        
                        @Override
                        public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model,
                            com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                            com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                            Log.d("ChatImagesAdapter", "✅ PRIMARY grid image loaded: " + imageUrl);
                            return false;
                        }
                    })
                    .into(imageView);
                    
            } catch (Exception e) {
                Log.e("ChatImagesAdapter", "❌ CRITICAL: Grid image loading completely failed", e);
                imageView.setVisibility(View.GONE);
            }
        }
            
            /**
             * Copy image to app cache directory as fallback when URI permissions fail
             */
            private android.net.Uri copyImageToCache(android.content.Context context, android.net.Uri sourceUri) throws Exception {
                java.io.InputStream inputStream = null;
                java.io.FileOutputStream outputStream = null;
                
                try {
                    // Create cache file
                    String fileName = "cached_image_" + System.currentTimeMillis() + ".jpg";
                    java.io.File cacheFile = new java.io.File(context.getCacheDir(), fileName);
                    
                    // Copy image data
                    inputStream = context.getContentResolver().openInputStream(sourceUri);
                    if (inputStream == null) {
                        throw new Exception("Could not open input stream for URI: " + sourceUri);
                    }
                    
                    outputStream = new java.io.FileOutputStream(cacheFile);
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    
                    return android.net.Uri.fromFile(cacheFile);
                    
                } finally {
                    if (inputStream != null) {
                        try { inputStream.close(); } catch (Exception e) { /* ignore */ }
                    }
                    if (outputStream != null) {
                        try { outputStream.close(); } catch (Exception e) { /* ignore */ }
                    }
                }
            }
        }
        
        // GridSpacingItemDecoration for spacing between grid items
        private static class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {
            private int spanCount;
            private int spacing;
            private boolean includeEdge;
            
            public GridSpacingItemDecoration(int spanCount, int spacing, boolean includeEdge) {
                this.spanCount = spanCount;
                this.spacing = spacing;
                this.includeEdge = includeEdge;
            }
            
            @Override
            public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                int position = parent.getChildAdapterPosition(view);
                int column = position % spanCount;
                
                if (includeEdge) {
                    outRect.left = spacing - column * spacing / spanCount;
                    outRect.right = (column + 1) * spacing / spanCount;
                    
                    if (position < spanCount) {
                        outRect.top = spacing;
                    }
                    outRect.bottom = spacing;
                } else {
                    outRect.left = column * spacing / spanCount;
                    outRect.right = spacing - (column + 1) * spacing / spanCount;
                    if (position >= spanCount) {
                        outRect.top = spacing;
                    }
                }
            }
        }
        
        // Reaction handling methods
        
        /**
         * Setup double-click listener to show reaction picker
         */
        private void setupDoubleClickListener(LinearLayout messageLayout, String messageId, ChatMessageModel model) {
            messageLayout.setOnClickListener(new View.OnClickListener() {
                private static final long DOUBLE_CLICK_TIME_DELTA = 300; // ms
                private long lastClickTime = 0;
                
                @Override
                public void onClick(View v) {
                    long clickTime = System.currentTimeMillis();
                    if (clickTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA) {
                        // Double click - show reaction picker
                        handleDoubleClick(v, messageId, model);
                    }
                    lastClickTime = clickTime;
                }
            });
        }
        
        /**
         * Handle double-click - show reaction picker for emoji selection
         */
        private void handleDoubleClick(View view, String messageId, ChatMessageModel model) {
            // Show reaction picker on double tap for better user experience
            showReactionPicker(view, messageId, model);
        }
        
        /**
         * Show reaction picker for a message
         */
        private void showReactionPicker(View anchorView, String messageId, ChatMessageModel model) {
            MessageReactionManager.showReactionPicker(context, anchorView, 
                new MessageReactionManager.OnReactionClickListener() {
                    @Override
                    public void onReactionSelected(String emoji) {
                        handleReactionSelected(messageId, emoji);
                    }
                    
                    @Override
                    public void onReactionToggle(String emoji) {
                        handleReactionToggle(messageId, emoji);
                    }
                });
        }
        
        /**
         * Handle reaction selection
         */
        private void handleReactionSelected(String messageId, String emoji) {
            String currentUserId = FirebaseUtil.currentUserId();
            if (currentUserId == null) {
                Log.e(TAG, "User not logged in, cannot add reaction");
                return;
            }
            
            Log.d(TAG, "Adding reaction " + emoji + " to message " + messageId);
            
            FirebaseUtil.toggleMessageReaction(chatroomId, messageId, emoji, currentUserId,
                wasAdded -> {
                    Log.d(TAG, "Reaction " + (wasAdded ? "added" : "removed") + ": " + emoji);
                    MessageReactionManager.showReactionFeedback(context, emoji, wasAdded);
                },
                error -> {
                    Log.e(TAG, "Failed to toggle reaction", error);
                    Toast.makeText(context, "Failed to add reaction", Toast.LENGTH_SHORT).show();
                }
            );
        }
        
        /**
         * Handle reaction toggle (when clicking on existing reaction)
         */
        private void handleReactionToggle(String messageId, String emoji) {
            handleReactionSelected(messageId, emoji);
        }
        
        /**
         * MEMORY-OPTIMIZED: Safely load image with multiple fallback strategies and memory management
         */
        private void loadImageSafely(ImageView imageView, String imageUrl) {
            if (imageUrl == null || imageUrl.isEmpty()) {
                imageView.setVisibility(View.GONE);
                return;
            }
            
            Log.d(TAG, "🖼️ Loading image safely with memory optimization: " + imageUrl);
            
            try {
                // MEMORY OPTIMIZATION: Clear any existing image first
                imageView.setImageDrawable(null);
                
                // Make image view visible initially
                imageView.setVisibility(View.VISIBLE);
                
                // MEMORY-OPTIMIZED: Load with size limits to prevent OOM crashes
                loadImageWithMemoryOptimization(imageView, imageUrl);
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Error in loadImageSafely", e);
                imageView.setVisibility(View.GONE);
            }
        }
        
        /**
         * MEMORY-OPTIMIZED: Load image with aggressive memory management to prevent OOM crashes
         */
        private void loadImageWithMemoryOptimization(ImageView imageView, String imageUrl) {
            try {
                Log.d(TAG, "🚀 Loading image with AGGRESSIVE MEMORY OPTIMIZATION: " + imageUrl);
                
                Object loadSource = getImageLoadSource(imageUrl, 0);
                
                com.bumptech.glide.Glide.with(context)
                    .load(loadSource)
                    .placeholder(android.R.color.transparent)
                    .error(android.R.color.transparent)
                    // CRITICAL MEMORY OPTIMIZATIONS:
                    .override(150, 150)  // SMALLER SIZE to prevent memory crashes
                    .centerCrop()
                    // Memory optimization removed - using default format
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.RESOURCE) // Cache processed images
                    .skipMemoryCache(false) // Use memory cache but with limits
                    .dontAnimate() // Disable animations to save memory
                    .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                        @Override
                        public boolean onLoadFailed(com.bumptech.glide.load.engine.GlideException e, Object model, 
                            com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, 
                            boolean isFirstResource) {
                            Log.w(TAG, "⚠️ Primary image load failed, trying MINIMAL fallback: " + imageUrl, e);
                            
                            // FALLBACK: Try with even more aggressive memory settings
                            try {
                                com.bumptech.glide.Glide.with(context)
                                    .load(imageUrl)
                                    .placeholder(android.R.color.transparent)
                                    .error(android.R.color.transparent)
                                    .override(100, 100)  // EVEN SMALLER for fallback
                                    .centerCrop()
                                    // Fallback memory format
                                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                                    .skipMemoryCache(true) // Skip cache for fallback
                                    .dontAnimate()
                                    .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                                        @Override
                                        public boolean onLoadFailed(com.bumptech.glide.load.engine.GlideException e2, Object model2, 
                                            com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target2, 
                                            boolean isFirstResource2) {
                                            Log.e(TAG, "❌ FINAL FALLBACK FAILED - hiding image: " + imageUrl, e2);
                                            imageView.setVisibility(View.GONE);
                                            return true;
                                        }
                                        
                                        @Override
                                        public boolean onResourceReady(android.graphics.drawable.Drawable resource2, Object model2,
                                            com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target2,
                                            com.bumptech.glide.load.DataSource dataSource2, boolean isFirstResource2) {
                                            Log.d(TAG, "✅ FALLBACK image loaded: " + imageUrl);
                                            return false;
                                        }
                                    })
                                    .into(imageView);
                            } catch (Exception fallbackException) {
                                Log.e(TAG, "❌ Fallback loading also failed", fallbackException);
                                imageView.setVisibility(View.GONE);
                            }
                            
                            return true; // Consume the primary error
                        }
                        
                        @Override
                        public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model,
                            com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                            com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                            Log.d(TAG, "✅ PRIMARY image loaded successfully: " + imageUrl);
                            return false;
                        }
                    })
                    .into(imageView);
                    
            } catch (Exception e) {
                Log.e(TAG, "❌ CRITICAL: Image loading completely failed", e);
                imageView.setVisibility(View.GONE);
            }
        }
        
        /**
         * Get image source for loading strategy
         */
        private Object getImageLoadSource(String imageUrl, int strategyIndex) {
            switch (strategyIndex) {
                case 0:
                    // Try as URI first if it's a content or file URI
                    if (imageUrl.startsWith("content://") || imageUrl.startsWith("file://")) {
                        return android.net.Uri.parse(imageUrl);
                    }
                    return imageUrl;
                    
                case 1:
                case 2:
                default:
                    // Use string URL for other strategies
                    return imageUrl;
            }
        }
}
