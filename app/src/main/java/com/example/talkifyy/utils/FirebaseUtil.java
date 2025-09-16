package com.example.talkifyy.utils;

import android.util.Log;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.FirebaseApp;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

public class FirebaseUtil {
    public static String currentUserId(){
        try {
            String uid = FirebaseAuth.getInstance().getUid();
            if (uid == null) {
                Log.w("FirebaseUtil", "⚠️ currentUserId is null - user might not be logged in");
            }
            return uid;
        } catch (Exception e) {
            Log.e("FirebaseUtil", "❌ Error getting current user ID", e);
            return null;
        }
    }

    public static boolean isLoggedIn(){
        if(currentUserId()!=null){
            return true;
        }
        return false;
    }
    public static DocumentReference currentUserDetails(){
        String userId = currentUserId();
        if(userId == null) {
            throw new IllegalStateException("User is not logged in");
        }
        return FirebaseFirestore.getInstance().collection("users").document(userId);
    }



    public static CollectionReference allUserCollectionReference(){
        return FirebaseFirestore.getInstance().collection("users");
    }

    public static DocumentReference getChatroomReference(String chatroomId){
        return FirebaseFirestore.getInstance().collection("chatrooms").document(chatroomId);
    }


    public static String getChatroomId(String userId1,String userId2){
        // Add null safety checks
        if (userId1 == null || userId2 == null) {
            Log.e("FirebaseUtil", "❌ getChatroomId called with null userId: userId1=" + userId1 + ", userId2=" + userId2);
            // Return a fallback ID to prevent crashes
            return "unknown_" + System.currentTimeMillis();
        }
        
        if (userId1.isEmpty() || userId2.isEmpty()) {
            Log.w("FirebaseUtil", "⚠️ getChatroomId called with empty userId: userId1=" + userId1 + ", userId2=" + userId2);
            // Still generate an ID to prevent crashes
            return (userId1.isEmpty() ? "empty1" : userId1) + "_" + (userId2.isEmpty() ? "empty2" : userId2) + "_" + System.currentTimeMillis();
        }
        
        if(userId1.hashCode()<userId2.hashCode()){
            return userId1+"_"+userId2;
        }else{
            return userId2+"_"+userId1;
        }
    }

    public static CollectionReference getChatroomMessageReference(String chatroomId){
        return getChatroomReference(chatroomId).collection("chats");
    }

    public static CollectionReference allChatroomCollectionReference(){
        return FirebaseFirestore.getInstance().collection("chatrooms");
    }

    public static DocumentReference getOtherUserFromChatroom(List<String> userIds){
        if(userIds.get(0).equals(FirebaseUtil.currentUserId())){
            return allUserCollectionReference().document(userIds.get(1));
        }else{
            return allUserCollectionReference().document(userIds.get(0));
        }
    }


    public static String timestampToString(Timestamp timestamp){
        return new SimpleDateFormat("HH:mm").format(timestamp.toDate());
    }

    public static void logout(){
        FirebaseAuth.getInstance().signOut();
    }
    
    // Initialize Firebase Storage with proper configuration
    private static FirebaseStorage initializeFirebaseStorage() {
        try {
            Log.d("FirebaseUtil", "🔧 Initializing Firebase Storage...");
            
            // Try to get the default instance first
            FirebaseStorage storage = FirebaseStorage.getInstance();
            
            // Validate the storage bucket
            String bucket = storage.getReference().getBucket();
            Log.d("FirebaseUtil", "📦 Using storage bucket: " + bucket);
            
            if (bucket != null && !bucket.isEmpty()) {
                Log.d("FirebaseUtil", "✅ Firebase Storage initialized successfully");
                return storage;
            } else {
                Log.w("FirebaseUtil", "⚠️ Storage bucket is null, trying alternative initialization");
                return initializeStorageWithCustomBucket();
            }
            
        } catch (Exception e) {
            Log.e("FirebaseUtil", "❌ Failed to initialize default storage, trying alternative", e);
            return initializeStorageWithCustomBucket();
        }
    }
    
    // Alternative storage initialization with explicit bucket URL
    private static FirebaseStorage initializeStorageWithCustomBucket() {
        try {
            Log.d("FirebaseUtil", "🔧 Trying alternative storage initialization...");
            
            // Use the bucket URL from your google-services.json
            String bucketUrl = "gs://gkg-talkifyy.firebasestorage.app";
            
            FirebaseStorage storage = FirebaseStorage.getInstance(bucketUrl);
            Log.d("FirebaseUtil", "✅ Alternative Firebase Storage initialized with: " + bucketUrl);
            
            return storage;
            
        } catch (Exception e) {
            Log.e("FirebaseUtil", "❌ Alternative storage initialization failed", e);
            
            // Fallback to default instance
            Log.w("FirebaseUtil", "🔄 Falling back to default Firebase Storage instance");
            return FirebaseStorage.getInstance();
        }
    }
    
    // Get a properly initialized Firebase Storage instance
    public static FirebaseStorage getFirebaseStorage() {
        return initializeFirebaseStorage();
    }

    public static StorageReference getCurrentProfilePicStorageRef(){
        return getFirebaseStorage().getReference().child("profile_pic")
                .child(FirebaseUtil.currentUserId() + ".jpg");
    }

    public static StorageReference getOtherProfilePicStorageRef(String otherUserId){
        return getFirebaseStorage().getReference().child("profile_pic")
                .child(otherUserId + ".jpg");
    }

    public static void updateFCMToken(String token) {
        if (currentUserId() != null) {
            currentUserDetails().update("fcmToken", token);
        }
    }
    
    // Helper method to safely get profile picture download URL
    public static void getProfilePicDownloadUrl(String userId, 
                                              com.google.android.gms.tasks.OnSuccessListener<android.net.Uri> onSuccess,
                                              com.google.android.gms.tasks.OnFailureListener onFailure) {
        StorageReference ref = userId.equals(currentUserId()) ? 
                getCurrentProfilePicStorageRef() : 
                getOtherProfilePicStorageRef(userId);
                
        ref.getDownloadUrl()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }
    
    // Unsend message functionality - completely deletes the message (anyone can delete any message)
    public static void unsendMessage(String chatroomId, String messageId, 
                                   com.google.android.gms.tasks.OnSuccessListener<Void> onSuccess,
                                   com.google.android.gms.tasks.OnFailureListener onFailure) {
        DocumentReference messageRef = getChatroomMessageReference(chatroomId).document(messageId);
        
        // Directly delete the message without sender verification
        // Any user in the chat can delete any message
        messageRef.delete()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }
    
    // Check if user can unsend a message (anyone can delete any message now)
    public static void canUnsendMessage(String chatroomId, String messageId, long unsendTimeWindowMinutes,
                                      com.google.android.gms.tasks.OnSuccessListener<Boolean> onSuccess,
                                      com.google.android.gms.tasks.OnFailureListener onFailure) {
        DocumentReference messageRef = getChatroomMessageReference(chatroomId).document(messageId);
        
        messageRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                // Any user in the chat can delete any message
                onSuccess.onSuccess(true); // Always allow deletion
            } else {
                onFailure.onFailure(new Exception("Message not found or failed to retrieve"));
            }
        });
    }
    
    // Update last message in chatroom after a message was deleted
    public static void updateChatroomLastMessageIfNeeded(String chatroomId, String deletedMessageId) {
        DocumentReference chatroomRef = getChatroomReference(chatroomId);
        
        // Get the latest message from the chatroom to update last message
        getChatroomMessageReference(chatroomId)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        // Get the most recent message
                        com.google.firebase.firestore.DocumentSnapshot latestMessage = querySnapshot.getDocuments().get(0);
                        String latestMessageText = latestMessage.getString("message");
                        String latestSenderId = latestMessage.getString("senderId");
                        Timestamp latestTimestamp = latestMessage.getTimestamp("timestamp");
                        
                        // Update chatroom with the latest message info
                        chatroomRef.update(
                            "lastMessage", latestMessageText,
                            "lastMessageSenderId", latestSenderId,
                            "lastMessageTimestamp", latestTimestamp
                        );
                    } else {
                        // No messages left in chatroom
                        chatroomRef.update(
                            "lastMessage", "",
                            "lastMessageSenderId", "",
                            "lastMessageTimestamp", Timestamp.now()
                        );
                    }
                })
                .addOnFailureListener(e -> {
                    // Handle error - could log or show error message
                });
    }
    
    // Delete entire chat conversation
    public static void deleteChatConversation(String chatroomId, OnSuccessListener<Void> successListener, OnFailureListener failureListener) {
        Log.d("FirebaseUtil", "Deleting chat conversation: " + chatroomId);
        
        // First delete all messages in the chatroom
        getChatroomMessageReference(chatroomId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        // No messages to delete, just delete the chatroom document
                        deleteChatroomDocument(chatroomId, successListener, failureListener);
                    } else {
                        // Delete all messages first
                        int totalMessages = querySnapshot.size();
                        final int[] deletedCount = {0};
                        final boolean[] hasError = {false};
                        
                        for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                            document.getReference().delete()
                                    .addOnSuccessListener(aVoid -> {
                                        deletedCount[0]++;
                                        // Check if all messages are deleted
                                        if (deletedCount[0] == totalMessages && !hasError[0]) {
                                            // All messages deleted, now delete the chatroom document
                                            deleteChatroomDocument(chatroomId, successListener, failureListener);
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        if (!hasError[0]) {
                                            hasError[0] = true;
                                            Log.e("FirebaseUtil", "Failed to delete message in chat", e);
                                            failureListener.onFailure(e);
                                        }
                                    });
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("FirebaseUtil", "Failed to get messages for deletion", e);
                    failureListener.onFailure(e);
                });
    }
    
    // Helper method to delete chatroom document
    private static void deleteChatroomDocument(String chatroomId, OnSuccessListener<Void> successListener, OnFailureListener failureListener) {
        getChatroomReference(chatroomId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d("FirebaseUtil", "Chatroom document deleted successfully: " + chatroomId);
                    successListener.onSuccess(aVoid);
                })
                .addOnFailureListener(e -> {
                    Log.e("FirebaseUtil", "Failed to delete chatroom document", e);
                    failureListener.onFailure(e);
                });
    }
    
    // Clear chat history (delete all messages but keep chatroom)
    public static void clearChatHistory(String chatroomId, OnSuccessListener<Void> successListener, OnFailureListener failureListener) {
        Log.d("FirebaseUtil", "Clearing chat history: " + chatroomId);
        
        getChatroomMessageReference(chatroomId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        // No messages to clear
                        successListener.onSuccess(null);
                    } else {
                        // Delete all messages
                        int totalMessages = querySnapshot.size();
                        final int[] deletedCount = {0};
                        final boolean[] hasError = {false};
                        
                        for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                            document.getReference().delete()
                                    .addOnSuccessListener(aVoid -> {
                                        deletedCount[0]++;
                                        // Check if all messages are deleted
                                        if (deletedCount[0] == totalMessages && !hasError[0]) {
                                            // All messages deleted, update chatroom last message
                                            updateChatroomAfterClear(chatroomId, successListener, failureListener);
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        if (!hasError[0]) {
                                            hasError[0] = true;
                                            Log.e("FirebaseUtil", "Failed to delete message in chat clear", e);
                                            failureListener.onFailure(e);
                                        }
                                    });
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("FirebaseUtil", "Failed to get messages for clearing", e);
                    failureListener.onFailure(e);
                });
    }
    
    // Helper method to update chatroom after clearing history
    private static void updateChatroomAfterClear(String chatroomId, OnSuccessListener<Void> successListener, OnFailureListener failureListener) {
        getChatroomReference(chatroomId)
                .update(
                        "lastMessage", "",
                        "lastMessageSenderId", "",
                        "lastMessageTimestamp", Timestamp.now()
                )
                .addOnSuccessListener(aVoid -> {
                    Log.d("FirebaseUtil", "Chatroom updated after clearing history: " + chatroomId);
                    successListener.onSuccess(aVoid);
                })
                .addOnFailureListener(e -> {
                    Log.e("FirebaseUtil", "Failed to update chatroom after clearing", e);
                    // Still consider it a success since messages were deleted
                    successListener.onSuccess(null);
                });
    }
    
    // WhatsApp-style deletion methods
    
    /**
     * Delete a message for everyone (marks as deleted but keeps in database)
     * @param chatroomId Chatroom containing the message
     * @param messageId Message to delete for everyone
     * @param deletedByUserId User who initiated the deletion
     * @param onSuccess Success callback
     * @param onFailure Failure callback
     */
    public static void deleteMessageForEveryone(String chatroomId, String messageId, String deletedByUserId,
                                               OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        DocumentReference messageRef = getChatroomMessageReference(chatroomId).document(messageId);
        
        messageRef.update(
                "deletedForEveryone", true,
                "deletedForEveryoneTimestamp", Timestamp.now(),
                "deletedByUserId", deletedByUserId
        )
        .addOnSuccessListener(aVoid -> {
            Log.d("FirebaseUtil", "Message marked as deleted for everyone: " + messageId);
            onSuccess.onSuccess(aVoid);
        })
        .addOnFailureListener(error -> {
            Log.e("FirebaseUtil", "Failed to delete message for everyone: " + messageId, error);
            onFailure.onFailure(error);
        });
    }
    
    /**
     * Check if a message can be deleted for everyone based on WhatsApp rules
     * @param chatroomId Chatroom ID
     * @param messageId Message ID
     * @param currentUserId Current user ID
     * @param isGroupAdmin Whether current user is group admin
     * @param isGroupChat Whether this is a group chat
     * @param recallWindowHours Recall window in hours
     * @param onSuccess Success callback with boolean result
     * @param onFailure Failure callback
     */
    public static void canDeleteMessageForEveryone(String chatroomId, String messageId, String currentUserId,
                                                  boolean isGroupAdmin, boolean isGroupChat, long recallWindowHours,
                                                  OnSuccessListener<Boolean> onSuccess, OnFailureListener onFailure) {
        DocumentReference messageRef = getChatroomMessageReference(chatroomId).document(messageId);
        
        messageRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                DocumentSnapshot doc = task.getResult();
                
                // Check if already deleted for everyone
                Boolean deletedForEveryone = doc.getBoolean("deletedForEveryone");
                if (deletedForEveryone != null && deletedForEveryone) {
                    onSuccess.onSuccess(false);
                    return;
                }
                
                String senderId = doc.getString("senderId");
                Timestamp timestamp = doc.getTimestamp("timestamp");
                
                // Rule 1: Own messages - check recall window
                if (senderId != null && senderId.equals(currentUserId)) {
                    if (timestamp != null && recallWindowHours > 0) {
                        long currentTime = System.currentTimeMillis();
                        long messageTime = timestamp.toDate().getTime();
                        long timeDifference = currentTime - messageTime;
                        long recallWindowMs = recallWindowHours * 60 * 60 * 1000;
                        
                        onSuccess.onSuccess(timeDifference <= recallWindowMs);
                    } else {
                        onSuccess.onSuccess(true); // No time limit or timestamp issue
                    }
                    return;
                }
                
                // Rule 2: Other's messages in 1-on-1 chat - not allowed
                if (!isGroupChat) {
                    onSuccess.onSuccess(false);
                    return;
                }
                
                // Rule 3: Other's messages in group chat - only admins can delete
                onSuccess.onSuccess(isGroupAdmin);
                
            } else {
                onFailure.onFailure(new Exception("Message not found or failed to retrieve"));
            }
        });
    }
    
    /**
     * Batch delete multiple messages for everyone
     * @param chatroomId Chatroom ID
     * @param messageIds List of message IDs to delete
     * @param deletedByUserId User who initiated the deletion
     * @param onComplete Callback when all operations complete (provides success and failure counts)
     */
    public static void deleteMultipleMessagesForEveryone(String chatroomId, List<String> messageIds, String deletedByUserId,
                                                        OnBatchCompleteListener onComplete) {
        final int totalCount = messageIds.size();
        final int[] completedCount = {0};
        final int[] successCount = {0};
        final int[] failureCount = {0};
        
        for (String messageId : messageIds) {
            deleteMessageForEveryone(chatroomId, messageId, deletedByUserId,
                aVoid -> {
                    synchronized (completedCount) {
                        completedCount[0]++;
                        successCount[0]++;
                        
                        if (completedCount[0] == totalCount) {
                            onComplete.onComplete(successCount[0], failureCount[0]);
                        }
                    }
                },
                error -> {
                    synchronized (completedCount) {
                        completedCount[0]++;
                        failureCount[0]++;
                        
                        if (completedCount[0] == totalCount) {
                            onComplete.onComplete(successCount[0], failureCount[0]);
                        }
                    }
                }
            );
        }
    }
    
    
    /**
     * Update chatroom last message info, handling deleted messages appropriately
     * @param chatroomId Chatroom ID
     */
    public static void updateChatroomLastMessageAfterDeletion(String chatroomId) {
        DocumentReference chatroomRef = getChatroomReference(chatroomId);
        
        // Get the latest non-deleted message from the chatroom
        getChatroomMessageReference(chatroomId)
                .whereEqualTo("deletedForEveryone", false) // Only non-deleted messages
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        // Get the most recent non-deleted message
                        DocumentSnapshot latestMessage = querySnapshot.getDocuments().get(0);
                        String latestMessageText = latestMessage.getString("message");
                        String latestSenderId = latestMessage.getString("senderId");
                        Timestamp latestTimestamp = latestMessage.getTimestamp("timestamp");
                        
                        // Update chatroom with the latest message info
                        chatroomRef.update(
                            "lastMessage", latestMessageText,
                            "lastMessageSenderId", latestSenderId,
                            "lastMessageTimestamp", latestTimestamp
                        );
                    } else {
                        // No non-deleted messages left in chatroom
                        chatroomRef.update(
                            "lastMessage", "",
                            "lastMessageSenderId", "",
                            "lastMessageTimestamp", Timestamp.now()
                        );
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("FirebaseUtil", "Failed to update chatroom after deletion", e);
                });
    }
    
    // Interface for batch operation completion
    public interface OnBatchCompleteListener {
        void onComplete(int successCount, int failureCount);
    }
    
    // Message Reaction Methods
    
    /**
     * Add or remove a reaction from a message
     * @param chatroomId Chatroom ID
     * @param messageId Message ID
     * @param emoji Emoji reaction
     * @param userId User ID
     * @param onSuccess Success callback with boolean (true if added, false if removed)
     * @param onFailure Failure callback
     */
    public static void toggleMessageReaction(String chatroomId, String messageId, String emoji, String userId,
                                           OnSuccessListener<Boolean> onSuccess, OnFailureListener onFailure) {
        DocumentReference messageRef = getChatroomMessageReference(chatroomId).document(messageId);
        
        messageRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                // Get current reactions
                @SuppressWarnings("unchecked")
                Map<String, Object> reactions = (Map<String, Object>) documentSnapshot.get("reactions");
                if (reactions == null) {
                    reactions = new HashMap<>();
                }
                
                // Get users for this emoji
                @SuppressWarnings("unchecked")
                List<String> userList = (List<String>) reactions.get(emoji);
                if (userList == null) {
                    userList = new ArrayList<>();
                }
                
                boolean wasAdded;
                if (userList.contains(userId)) {
                    // Remove reaction
                    userList.remove(userId);
                    if (userList.isEmpty()) {
                        reactions.remove(emoji);
                    } else {
                        reactions.put(emoji, userList);
                    }
                    wasAdded = false;
                } else {
                    // Add reaction
                    userList.add(userId);
                    reactions.put(emoji, userList);
                    wasAdded = true;
                }
                
                // Calculate total reactions
                int totalReactions = 0;
                for (Object value : reactions.values()) {
                    if (value instanceof List) {
                        totalReactions += ((List<?>) value).size();
                    }
                }
                
                // Update in Firestore
                messageRef.update(
                    "reactions", reactions,
                    "totalReactions", totalReactions
                ).addOnSuccessListener(aVoid -> {
                    Log.d("FirebaseUtil", "Reaction " + (wasAdded ? "added" : "removed") + ": " + emoji);
                    onSuccess.onSuccess(wasAdded);
                }).addOnFailureListener(onFailure);
                
            } else {
                onFailure.onFailure(new Exception("Message not found"));
            }
        }).addOnFailureListener(onFailure);
    }
    
    /**
     * Get reactions for a specific message
     * @param chatroomId Chatroom ID
     * @param messageId Message ID
     * @param onSuccess Success callback with reactions map
     * @param onFailure Failure callback
     */
    public static void getMessageReactions(String chatroomId, String messageId,
                                         OnSuccessListener<Map<String, List<String>>> onSuccess,
                                         OnFailureListener onFailure) {
        DocumentReference messageRef = getChatroomMessageReference(chatroomId).document(messageId);
        
        messageRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> reactionsData = (Map<String, Object>) documentSnapshot.get("reactions");
                
                Map<String, List<String>> reactions = new HashMap<>();
                if (reactionsData != null) {
                    for (Map.Entry<String, Object> entry : reactionsData.entrySet()) {
                        @SuppressWarnings("unchecked")
                        List<String> userList = (List<String>) entry.getValue();
                        if (userList != null && !userList.isEmpty()) {
                            reactions.put(entry.getKey(), userList);
                        }
                    }
                }
                
                onSuccess.onSuccess(reactions);
            } else {
                onFailure.onFailure(new Exception("Message not found"));
            }
        }).addOnFailureListener(onFailure);
    }
    
    /**
     * Check if a user has reacted with a specific emoji to a message
     * @param chatroomId Chatroom ID
     * @param messageId Message ID
     * @param emoji Emoji reaction
     * @param userId User ID
     * @param onSuccess Success callback with boolean result
     * @param onFailure Failure callback
     */
    public static void hasUserReactedToMessage(String chatroomId, String messageId, String emoji, String userId,
                                             OnSuccessListener<Boolean> onSuccess, OnFailureListener onFailure) {
        getMessageReactions(chatroomId, messageId, 
            reactions -> {
                List<String> userList = reactions.get(emoji);
                boolean hasReacted = userList != null && userList.contains(userId);
                onSuccess.onSuccess(hasReacted);
            },
            onFailure
        );
    }
    
    /**
     * Get reaction count for a specific emoji on a message
     * @param chatroomId Chatroom ID
     * @param messageId Message ID
     * @param emoji Emoji reaction
     * @param onSuccess Success callback with count
     * @param onFailure Failure callback
     */
    public static void getReactionCount(String chatroomId, String messageId, String emoji,
                                      OnSuccessListener<Integer> onSuccess, OnFailureListener onFailure) {
        getMessageReactions(chatroomId, messageId,
            reactions -> {
                List<String> userList = reactions.get(emoji);
                int count = userList != null ? userList.size() : 0;
                onSuccess.onSuccess(count);
            },
            onFailure
        );
    }
    
    /**
     * Clear all reactions from a message (admin function)
     * @param chatroomId Chatroom ID
     * @param messageId Message ID
     * @param onSuccess Success callback
     * @param onFailure Failure callback
     */
    public static void clearMessageReactions(String chatroomId, String messageId,
                                           OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        DocumentReference messageRef = getChatroomMessageReference(chatroomId).document(messageId);
        
        messageRef.update(
            "reactions", new HashMap<>(),
            "totalReactions", 0
        ).addOnSuccessListener(aVoid -> {
            Log.d("FirebaseUtil", "Cleared all reactions for message: " + messageId);
            onSuccess.onSuccess(aVoid);
        }).addOnFailureListener(onFailure);
    }
    
    // Group Chat Methods
    
    /**
     * Generate a unique chatroom ID for group chats
     * @return Unique group chatroom ID
     */
    public static String generateGroupChatroomId() {
        return FirebaseFirestore.getInstance().collection("chatrooms").document().getId();
    }
    
    /**
     * Create a new group chat
     * @param groupName Name of the group
     * @param groupDescription Description of the group
     * @param userIds List of user IDs to add to the group
     * @param createdBy User ID of the group creator
     * @param onSuccess Success callback with group ID
     * @param onFailure Failure callback
     */
    public static void createGroupChat(String groupName, String groupDescription, List<String> userIds,
                                     String createdBy, OnSuccessListener<String> onSuccess, OnFailureListener onFailure) {
        String groupId = generateGroupChatroomId();
        
        // Ensure creator is in the user list and is an admin
        List<String> allUserIds = new ArrayList<>(userIds);
        if (!allUserIds.contains(createdBy)) {
            allUserIds.add(createdBy);
        }
        
        com.example.talkifyy.model.ChatroomModel groupChatroom = new com.example.talkifyy.model.ChatroomModel(
            groupId,
            allUserIds,
            Timestamp.now(),
            "",
            groupName,
            groupDescription,
            Arrays.asList(createdBy), // Creator as admin
            createdBy
        );
        
        getChatroomReference(groupId).set(groupChatroom)
                .addOnSuccessListener(aVoid -> {
                    Log.d("FirebaseUtil", "Group chat created successfully: " + groupName);
                    onSuccess.onSuccess(groupId);
                })
                .addOnFailureListener(error -> {
                    Log.e("FirebaseUtil", "Failed to create group chat: " + groupName, error);
                    onFailure.onFailure(error);
                });
    }
    
    /**
     * Update group name
     * @param chatroomId Group chatroom ID
     * @param newGroupName New group name
     * @param onSuccess Success callback
     * @param onFailure Failure callback
     */
    public static void updateGroupName(String chatroomId, String newGroupName, 
                                     OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        getChatroomReference(chatroomId)
                .update("groupName", newGroupName)
                .addOnSuccessListener(aVoid -> {
                    Log.d("FirebaseUtil", "Group name updated successfully: " + newGroupName);
                    onSuccess.onSuccess(aVoid);
                })
                .addOnFailureListener(error -> {
                    Log.e("FirebaseUtil", "Failed to update group name", error);
                    onFailure.onFailure(error);
                });
    }
    
    /**
     * Update group description
     * @param chatroomId Group chatroom ID
     * @param newDescription New group description
     * @param onSuccess Success callback
     * @param onFailure Failure callback
     */
    public static void updateGroupDescription(String chatroomId, String newDescription,
                                            OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        getChatroomReference(chatroomId)
                .update("groupDescription", newDescription)
                .addOnSuccessListener(aVoid -> {
                    Log.d("FirebaseUtil", "Group description updated successfully");
                    onSuccess.onSuccess(aVoid);
                })
                .addOnFailureListener(error -> {
                    Log.e("FirebaseUtil", "Failed to update group description", error);
                    onFailure.onFailure(error);
                });
    }
    
    /**
     * Add user to group
     * @param chatroomId Group chatroom ID
     * @param userId User ID to add
     * @param onSuccess Success callback
     * @param onFailure Failure callback
     */
    public static void addUserToGroup(String chatroomId, String userId,
                                    OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        getChatroomReference(chatroomId)
                .update("userIds", FieldValue.arrayUnion(userId))
                .addOnSuccessListener(aVoid -> {
                    Log.d("FirebaseUtil", "User added to group successfully: " + userId);
                    onSuccess.onSuccess(aVoid);
                })
                .addOnFailureListener(error -> {
                    Log.e("FirebaseUtil", "Failed to add user to group: " + userId, error);
                    onFailure.onFailure(error);
                });
    }
    
    /**
     * Add multiple users to group
     * @param chatroomId Group chatroom ID
     * @param userIds List of user IDs to add
     * @param onSuccess Success callback
     * @param onFailure Failure callback
     */
    public static void addUsersToGroup(String chatroomId, List<String> userIds,
                                     OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        if (userIds == null || userIds.isEmpty()) {
            onFailure.onFailure(new Exception("No users to add"));
            return;
        }
        
        getChatroomReference(chatroomId)
                .update("userIds", FieldValue.arrayUnion(userIds.toArray()))
                .addOnSuccessListener(aVoid -> {
                    Log.d("FirebaseUtil", "Users added to group successfully: " + userIds.size() + " users");
                    onSuccess.onSuccess(aVoid);
                })
                .addOnFailureListener(error -> {
                    Log.e("FirebaseUtil", "Failed to add users to group", error);
                    onFailure.onFailure(error);
                });
    }
    
    /**
     * Remove user from group
     * @param chatroomId Group chatroom ID
     * @param userId User ID to remove
     * @param onSuccess Success callback
     * @param onFailure Failure callback
     */
    public static void removeUserFromGroup(String chatroomId, String userId,
                                         OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        getChatroomReference(chatroomId)
                .update("userIds", FieldValue.arrayRemove(userId))
                .addOnSuccessListener(aVoid -> {
                    Log.d("FirebaseUtil", "User removed from group successfully: " + userId);
                    onSuccess.onSuccess(aVoid);
                })
                .addOnFailureListener(error -> {
                    Log.e("FirebaseUtil", "Failed to remove user from group: " + userId, error);
                    onFailure.onFailure(error);
                });
    }
    
    /**
     * Add admin to group
     * @param chatroomId Group chatroom ID
     * @param userId User ID to make admin
     * @param onSuccess Success callback
     * @param onFailure Failure callback
     */
    public static void addGroupAdmin(String chatroomId, String userId,
                                   OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        getChatroomReference(chatroomId)
                .update("adminIds", FieldValue.arrayUnion(userId))
                .addOnSuccessListener(aVoid -> {
                    Log.d("FirebaseUtil", "Admin added to group successfully: " + userId);
                    onSuccess.onSuccess(aVoid);
                })
                .addOnFailureListener(error -> {
                    Log.e("FirebaseUtil", "Failed to add admin to group: " + userId, error);
                    onFailure.onFailure(error);
                });
    }
    
    /**
     * Remove admin from group
     * @param chatroomId Group chatroom ID
     * @param userId User ID to remove admin status from
     * @param onSuccess Success callback
     * @param onFailure Failure callback
     */
    public static void removeGroupAdmin(String chatroomId, String userId,
                                      OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        getChatroomReference(chatroomId)
                .update("adminIds", FieldValue.arrayRemove(userId))
                .addOnSuccessListener(aVoid -> {
                    Log.d("FirebaseUtil", "Admin removed from group successfully: " + userId);
                    onSuccess.onSuccess(aVoid);
                })
                .addOnFailureListener(error -> {
                    Log.e("FirebaseUtil", "Failed to remove admin from group: " + userId, error);
                    onFailure.onFailure(error);
                });
    }
    
    /**
     * Check if user is admin in a specific group
     * @param chatroomId Group chatroom ID
     * @param userId User ID to check
     * @param onSuccess Success callback with boolean result
     * @param onFailure Failure callback
     */
    public static void isUserGroupAdmin(String chatroomId, String userId,
                                      OnSuccessListener<Boolean> onSuccess, OnFailureListener onFailure) {
        getChatroomReference(chatroomId).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                DocumentSnapshot doc = task.getResult();
                
                @SuppressWarnings("unchecked")
                List<String> admins = (List<String>) doc.get("adminIds");
                
                boolean isAdmin = admins != null && admins.contains(userId);
                onSuccess.onSuccess(isAdmin);
            } else {
                onFailure.onFailure(new Exception("Failed to check admin status"));
            }
        });
    }
    
    /**
     * Get group information
     * @param chatroomId Group chatroom ID
     * @param onSuccess Success callback with ChatroomModel
     * @param onFailure Failure callback
     */
    public static void getGroupInfo(String chatroomId, 
                                  OnSuccessListener<com.example.talkifyy.model.ChatroomModel> onSuccess, 
                                  OnFailureListener onFailure) {
        getChatroomReference(chatroomId).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                com.example.talkifyy.model.ChatroomModel chatroom = task.getResult().toObject(com.example.talkifyy.model.ChatroomModel.class);
                if (chatroom != null) {
                    onSuccess.onSuccess(chatroom);
                } else {
                    onFailure.onFailure(new Exception("Failed to parse group information"));
                }
            } else {
                onFailure.onFailure(new Exception("Group not found"));
            }
        });
    }

}
