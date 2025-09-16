package com.example.talkifyy.model;

import com.google.firebase.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public class ChatMessageModel {

    private String message;
    private String senderId;
    private Timestamp timestamp;
    private boolean isUnsent;
    private Timestamp unsentTimestamp;
    private String messageId; // For tracking specific messages
    
    
    // WhatsApp-style deletion fields
    private boolean deletedForEveryone;
    private Timestamp deletedForEveryoneTimestamp;
    private String deletedByUserId; // Who deleted the message for everyone
    private long recallWindowHours = 48; // Default recall window in hours
    
    // Message editing fields (WhatsApp-like)
    private boolean isEdited;
    private Timestamp editedTimestamp;
    private String originalMessage; // Store original message for reference
    private long editTimeWindowMinutes = 15; // Default edit window (15 minutes)
    
    // Image message fields
    private String messageType; // "text", "image", "multiple_images"
    private String imageUrl; // Single image URL
    private List<String> imageUrls; // Multiple image URLs
    private String imageCaption; // Caption for image(s)
    private ImageMetadata imageMetadata; // Image metadata (width, height, size)
    
    // Message reactions
    private Map<String, List<String>> reactions; // emoji -> list of user IDs who reacted
    private int totalReactions = 0; // Total count of all reactions

    public ChatMessageModel() {
        this.isUnsent = false;
        this.deletedForEveryone = false;
        this.isEdited = false;
        this.messageType = "text"; // Default to text message
        this.reactions = new HashMap<>();
        this.totalReactions = 0;
    }

    public ChatMessageModel(String message, String senderId, Timestamp timestamp) {
        this.message = message;
        this.senderId = senderId;
        this.timestamp = timestamp;
        this.isUnsent = false;
        this.deletedForEveryone = false;
        this.isEdited = false;
        this.messageType = "text"; // Default to text message
        this.reactions = new HashMap<>();
        this.totalReactions = 0;
    }

    public ChatMessageModel(String message, String senderId, Timestamp timestamp, String messageId) {
        this.message = message;
        this.senderId = senderId;
        this.timestamp = timestamp;
        this.messageId = messageId;
        this.isUnsent = false;
        this.deletedForEveryone = false;
        this.isEdited = false;
        this.messageType = "text"; // Default to text message
        this.reactions = new HashMap<>();
        this.totalReactions = 0;
    }
    
    // Constructor for single image message
    public ChatMessageModel(String senderId, Timestamp timestamp, String imageUrl, String caption, ImageMetadata metadata) {
        this.senderId = senderId;
        this.timestamp = timestamp;
        this.imageUrl = imageUrl;
        this.imageCaption = caption;
        this.imageMetadata = metadata;
        this.messageType = "image";
        this.message = caption != null && !caption.isEmpty() ? caption : "📷 Photo";
        this.isUnsent = false;
        this.deletedForEveryone = false;
        this.isEdited = false;
        this.reactions = new HashMap<>();
        this.totalReactions = 0;
    }
    
    // Constructor for multiple image message
    public ChatMessageModel(String senderId, Timestamp timestamp, List<String> imageUrls, String caption) {
        this.senderId = senderId;
        this.timestamp = timestamp;
        this.imageUrls = imageUrls;
        this.imageCaption = caption;
        this.messageType = "multiple_images";
        this.message = caption != null && !caption.isEmpty() ? caption : 
            "📷 " + (imageUrls != null ? imageUrls.size() : 0) + " Photos";
        this.isUnsent = false;
        this.deletedForEveryone = false;
        this.isEdited = false;
        this.reactions = new HashMap<>();
        this.totalReactions = 0;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isUnsent() {
        return isUnsent;
    }

    public void setUnsent(boolean unsent) {
        isUnsent = unsent;
    }

    public Timestamp getUnsentTimestamp() {
        return unsentTimestamp;
    }

    public void setUnsentTimestamp(Timestamp unsentTimestamp) {
        this.unsentTimestamp = unsentTimestamp;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    // Helper method to get display message
    public String getDisplayMessage() {
        if (isUnsent) {
            return "This message was unsent";
        }
        if (deletedForEveryone) {
            return "This message was deleted";
        }
        return message;
    }

    // Helper method to check if message can be unsent (within time limit)
    public boolean canBeUnsent(long unsendTimeWindowMinutes) {
        if (isUnsent || deletedForEveryone) {
            return false; // Already unsent or deleted for everyone
        }
        
        if (unsendTimeWindowMinutes <= 0) {
            return true; // No time limit
        }
        
        long currentTime = System.currentTimeMillis();
        long messageTime = timestamp.toDate().getTime();
        long timeDifference = currentTime - messageTime;
        long timeWindowMs = unsendTimeWindowMinutes * 60 * 1000;
        
        return timeDifference <= timeWindowMs;
    }
    
    // WhatsApp-style deletion methods
    
    /**
     * Check if message can be deleted for everyone by current user
     * @param currentUserId Current user ID
     * @param isGroupAdmin Whether current user is group admin
     * @return true if can delete for everyone
     */
    public boolean canDeleteForEveryone(String currentUserId, boolean isGroupAdmin) {
        if (deletedForEveryone) {
            return false; // Already deleted for everyone
        }
        
        // Current user can always delete their own messages within recall window
        if (senderId.equals(currentUserId)) {
            return isWithinRecallWindow();
        }
        
        // Group admins can delete others' messages
        return isGroupAdmin;
    }
    
    /**
     * Check if message is within recall window (48 hours by default)
     * @return true if within recall window
     */
    public boolean isWithinRecallWindow() {
        long currentTime = System.currentTimeMillis();
        long messageTime = timestamp.toDate().getTime();
        long timeDifference = currentTime - messageTime;
        long recallWindowMs = recallWindowHours * 60 * 60 * 1000;
        
        return timeDifference <= recallWindowMs;
    }
    
    /**
     * Mark message as deleted for everyone
     * @param deletedByUserId User who deleted the message
     */
    public void markDeletedForEveryone(String deletedByUserId) {
        this.deletedForEveryone = true;
        this.deletedForEveryoneTimestamp = Timestamp.now();
        this.deletedByUserId = deletedByUserId;
    }
    
    // Getters and setters for new fields
    
    public boolean isDeletedForEveryone() {
        return deletedForEveryone;
    }
    
    public void setDeletedForEveryone(boolean deletedForEveryone) {
        this.deletedForEveryone = deletedForEveryone;
    }
    
    public Timestamp getDeletedForEveryoneTimestamp() {
        return deletedForEveryoneTimestamp;
    }
    
    public void setDeletedForEveryoneTimestamp(Timestamp deletedForEveryoneTimestamp) {
        this.deletedForEveryoneTimestamp = deletedForEveryoneTimestamp;
    }
    
    public String getDeletedByUserId() {
        return deletedByUserId;
    }
    
    public void setDeletedByUserId(String deletedByUserId) {
        this.deletedByUserId = deletedByUserId;
    }
    
    public long getRecallWindowHours() {
        return recallWindowHours;
    }
    
    public void setRecallWindowHours(long recallWindowHours) {
        this.recallWindowHours = recallWindowHours;
    }
    
    // Reaction methods
    
    public Map<String, List<String>> getReactions() {
        if (reactions == null) {
            reactions = new HashMap<>();
        }
        return reactions;
    }
    
    public void setReactions(Map<String, List<String>> reactions) {
        this.reactions = reactions;
        updateTotalReactions();
    }
    
    public int getTotalReactions() {
        return totalReactions;
    }
    
    public void setTotalReactions(int totalReactions) {
        this.totalReactions = totalReactions;
    }
    
    /**
     * Add a reaction to this message
     * @param emoji The emoji reaction
     * @param userId The user ID who reacted
     * @return true if reaction was added, false if user already reacted with this emoji
     */
    public boolean addReaction(String emoji, String userId) {
        if (reactions == null) {
            reactions = new HashMap<>();
        }
        
        List<String> userList = reactions.get(emoji);
        if (userList == null) {
            userList = new ArrayList<>();
            reactions.put(emoji, userList);
        }
        
        if (!userList.contains(userId)) {
            userList.add(userId);
            updateTotalReactions();
            return true;
        }
        return false;
    }
    
    /**
     * Remove a reaction from this message
     * @param emoji The emoji reaction
     * @param userId The user ID who wants to remove their reaction
     * @return true if reaction was removed, false if user hadn't reacted with this emoji
     */
    public boolean removeReaction(String emoji, String userId) {
        if (reactions == null || !reactions.containsKey(emoji)) {
            return false;
        }
        
        List<String> userList = reactions.get(emoji);
        boolean removed = userList.remove(userId);
        
        if (removed) {
            if (userList.isEmpty()) {
                reactions.remove(emoji);
            }
            updateTotalReactions();
        }
        
        return removed;
    }
    
    /**
     * Toggle a user's reaction to this message
     * @param emoji The emoji reaction
     * @param userId The user ID
     * @return true if reaction was added, false if it was removed
     */
    public boolean toggleReaction(String emoji, String userId) {
        if (hasUserReacted(emoji, userId)) {
            removeReaction(emoji, userId);
            return false;
        } else {
            addReaction(emoji, userId);
            return true;
        }
    }
    
    /**
     * Check if a user has reacted with a specific emoji
     * @param emoji The emoji reaction
     * @param userId The user ID
     * @return true if user has reacted with this emoji
     */
    public boolean hasUserReacted(String emoji, String userId) {
        if (reactions == null || !reactions.containsKey(emoji)) {
            return false;
        }
        
        List<String> userList = reactions.get(emoji);
        return userList != null && userList.contains(userId);
    }
    
    /**
     * Get the count of reactions for a specific emoji
     * @param emoji The emoji reaction
     * @return count of reactions for this emoji
     */
    public int getReactionCount(String emoji) {
        if (reactions == null || !reactions.containsKey(emoji)) {
            return 0;
        }
        
        List<String> userList = reactions.get(emoji);
        return userList != null ? userList.size() : 0;
    }
    
    /**
     * Check if this message has any reactions
     * @return true if message has reactions
     */
    public boolean hasReactions() {
        return reactions != null && !reactions.isEmpty() && totalReactions > 0;
    }
    
    /**
     * Get list of emojis that have reactions
     * @return list of emoji strings
     */
    public List<String> getReactionEmojis() {
        if (reactions == null || reactions.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(reactions.keySet());
    }
    
    /**
     * Update the total reactions count
     */
    private void updateTotalReactions() {
        if (reactions == null) {
            totalReactions = 0;
            return;
        }
        
        int count = 0;
        for (List<String> userList : reactions.values()) {
            if (userList != null) {
                count += userList.size();
            }
        }
        totalReactions = count;
    }
    
    // Message editing methods
    
    /**
     * Check if this message can be edited by the current user
     * @param currentUserId Current user ID
     * @return true if message can be edited
     */
    public boolean canBeEdited(String currentUserId) {
        if (isUnsent || deletedForEveryone) {
            return false; // Cannot edit unsent or deleted messages
        }
        
        if (!senderId.equals(currentUserId)) {
            return false; // Can only edit own messages
        }
        
        // Check if within edit time window
        return isWithinEditWindow();
    }
    
    /**
     * Check if message is within edit time window
     * @return true if within edit window
     */
    public boolean isWithinEditWindow() {
        if (editTimeWindowMinutes <= 0) {
            return true; // No time limit
        }
        
        long currentTime = System.currentTimeMillis();
        long messageTime = timestamp.toDate().getTime();
        long timeDifference = currentTime - messageTime;
        long editWindowMs = editTimeWindowMinutes * 60 * 1000;
        
        return timeDifference <= editWindowMs;
    }
    
    /**
     * Edit the message content
     * @param newMessage New message content
     */
    public void editMessage(String newMessage) {
        if (originalMessage == null) {
            // Store original message before first edit
            this.originalMessage = this.message;
        }
        
        this.message = newMessage;
        this.isEdited = true;
        this.editedTimestamp = Timestamp.now();
    }
    
    /**
     * Get display message with edit indicator if edited
     * @return message with edit indicator
     */
    public String getDisplayMessageWithEditIndicator() {
        String displayMsg = getDisplayMessage();
        if (isEdited && !isUnsent && !deletedForEveryone) {
            return displayMsg + " (edited)";
        }
        return displayMsg;
    }
    
    // Getters and setters for edit fields
    
    public boolean isEdited() {
        return isEdited;
    }
    
    public void setEdited(boolean edited) {
        isEdited = edited;
    }
    
    public Timestamp getEditedTimestamp() {
        return editedTimestamp;
    }
    
    public void setEditedTimestamp(Timestamp editedTimestamp) {
        this.editedTimestamp = editedTimestamp;
    }
    
    public String getOriginalMessage() {
        return originalMessage;
    }
    
    public void setOriginalMessage(String originalMessage) {
        this.originalMessage = originalMessage;
    }
    
    public long getEditTimeWindowMinutes() {
        return editTimeWindowMinutes;
    }
    
    public void setEditTimeWindowMinutes(long editTimeWindowMinutes) {
        this.editTimeWindowMinutes = editTimeWindowMinutes;
    }
    
    // Getters and setters for image fields
    
    public String getMessageType() {
        return messageType;
    }
    
    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }
    
    public String getImageUrl() {
        return imageUrl;
    }
    
    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
    
    public List<String> getImageUrls() {
        return imageUrls;
    }
    
    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }
    
    public String getImageCaption() {
        return imageCaption;
    }
    
    public void setImageCaption(String imageCaption) {
        this.imageCaption = imageCaption;
    }
    
    public ImageMetadata getImageMetadata() {
        return imageMetadata;
    }
    
    public void setImageMetadata(ImageMetadata imageMetadata) {
        this.imageMetadata = imageMetadata;
    }
    
    // Helper methods for image messages
    
    public boolean isImageMessage() {
        return "image".equals(messageType) || "multiple_images".equals(messageType);
    }
    
    public boolean isSingleImage() {
        return "image".equals(messageType);
    }
    
    public boolean isMultipleImages() {
        return "multiple_images".equals(messageType);
    }
    
    public int getImageCount() {
        if (isSingleImage()) return 1;
        if (isMultipleImages() && imageUrls != null) return imageUrls.size();
        return 0;
    }
    
    public String getDisplayText() {
        if (isImageMessage()) {
            return imageCaption != null && !imageCaption.isEmpty() ? imageCaption : message;
        }
        return message;
    }
    
}
