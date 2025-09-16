package com.example.talkifyy.model;

import com.google.firebase.Timestamp;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

public class ChatroomModel {
    String chatroomId;
    List<String> userIds;
    Timestamp lastMessageTimestamp;
    String lastMessageSenderId;
    String lastMessage;
    
    // Group-specific fields
    String groupName;
    String groupDescription;
    List<String> adminIds;
    boolean isGroup;
    String groupImageUrl;
    Timestamp createdTimestamp;
    String createdBy;

    public ChatroomModel() {
        this.adminIds = new ArrayList<>();
        this.isGroup = false;
    }

    // Constructor for 1-on-1 chats
    public ChatroomModel(String chatroomId, List<String> userIds, Timestamp lastMessageTimestamp, String lastMessageSenderId) {
        this.chatroomId = chatroomId;
        this.userIds = userIds;
        this.lastMessageTimestamp = lastMessageTimestamp;
        this.lastMessageSenderId = lastMessageSenderId;
        this.adminIds = new ArrayList<>();
        this.isGroup = false;
    }
    
    // Constructor for group chats
    public ChatroomModel(String chatroomId, List<String> userIds, Timestamp lastMessageTimestamp, 
                        String lastMessageSenderId, String groupName, String groupDescription, 
                        List<String> adminIds, String createdBy) {
        this.chatroomId = chatroomId;
        this.userIds = userIds;
        this.lastMessageTimestamp = lastMessageTimestamp;
        this.lastMessageSenderId = lastMessageSenderId;
        this.groupName = groupName;
        this.groupDescription = groupDescription;
        this.adminIds = adminIds != null ? adminIds : new ArrayList<>();
        this.isGroup = true;
        this.createdTimestamp = Timestamp.now();
        this.createdBy = createdBy;
    }

    public String getChatroomId() {
        return chatroomId;
    }

    public void setChatroomId(String chatroomId) {
        this.chatroomId = chatroomId;
    }

    public List<String> getUserIds() {
        return userIds;
    }

    public void setUserIds(List<String> userIds) {
        this.userIds = userIds;
    }

    public Timestamp getLastMessageTimestamp() {
        return lastMessageTimestamp;
    }

    public void setLastMessageTimestamp(Timestamp lastMessageTimestamp) {
        this.lastMessageTimestamp = lastMessageTimestamp;
    }

    public String getLastMessageSenderId() {
        return lastMessageSenderId;
    }

    public void setLastMessageSenderId(String lastMessageSenderId) {
        this.lastMessageSenderId = lastMessageSenderId;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    // Group-specific getters and setters
    
    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getGroupDescription() {
        return groupDescription;
    }

    public void setGroupDescription(String groupDescription) {
        this.groupDescription = groupDescription;
    }

    public List<String> getAdminIds() {
        return adminIds;
    }

    public void setAdminIds(List<String> adminIds) {
        this.adminIds = adminIds;
    }

    public boolean isGroup() {
        return isGroup;
    }

    public void setGroup(boolean group) {
        isGroup = group;
    }

    public String getGroupImageUrl() {
        return groupImageUrl;
    }

    public void setGroupImageUrl(String groupImageUrl) {
        this.groupImageUrl = groupImageUrl;
    }

    public Timestamp getCreatedTimestamp() {
        return createdTimestamp;
    }

    public void setCreatedTimestamp(Timestamp createdTimestamp) {
        this.createdTimestamp = createdTimestamp;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
    
    // Helper methods
    
    public boolean isUserAdmin(String userId) {
        return adminIds != null && adminIds.contains(userId);
    }
    
    public void addAdmin(String userId) {
        if (adminIds == null) {
            adminIds = new ArrayList<>();
        }
        if (!adminIds.contains(userId)) {
            adminIds.add(userId);
        }
    }
    
    public void removeAdmin(String userId) {
        if (adminIds != null) {
            adminIds.remove(userId);
        }
    }
    
    public String getDisplayName() {
        return isGroup ? groupName : "Chat";
    }

}
