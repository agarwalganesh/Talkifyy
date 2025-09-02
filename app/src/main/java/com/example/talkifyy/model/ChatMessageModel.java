package com.example.talkifyy.model;

import com.google.firebase.Timestamp;

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

    public ChatMessageModel() {
        this.isUnsent = false;
        this.deletedForEveryone = false;
    }

    public ChatMessageModel(String message, String senderId, Timestamp timestamp) {
        this.message = message;
        this.senderId = senderId;
        this.timestamp = timestamp;
        this.isUnsent = false;
        this.deletedForEveryone = false;
    }

    public ChatMessageModel(String message, String senderId, Timestamp timestamp, String messageId) {
        this.message = message;
        this.senderId = senderId;
        this.timestamp = timestamp;
        this.messageId = messageId;
        this.isUnsent = false;
        this.deletedForEveryone = false;
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
}
