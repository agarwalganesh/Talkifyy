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

import java.text.SimpleDateFormat;
import java.util.List;

public class FirebaseUtil {
    public static String currentUserId(){
        return FirebaseAuth.getInstance().getUid();
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

    public static StorageReference getCurrentProfilePicStorageRef(){
        return FirebaseStorage.getInstance().getReference().child("profile_pic")
                .child(FirebaseUtil.currentUserId() + ".jpg");
    }

    public static StorageReference getOtherProfilePicStorageRef(String otherUserId){
        return FirebaseStorage.getInstance().getReference().child("profile_pic")
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
     * Check if current user is admin in a group chat
     * @param chatroomId Chatroom ID
     * @param userId User ID to check
     * @param onSuccess Success callback with boolean result
     * @param onFailure Failure callback
     */
    public static void isUserGroupAdmin(String chatroomId, String userId,
                                       OnSuccessListener<Boolean> onSuccess, OnFailureListener onFailure) {
        // For now, return false as this is primarily a 1-on-1 chat system
        // In a full group chat implementation, this would check the chatroom's admin list
        DocumentReference chatroomRef = getChatroomReference(chatroomId);
        
        chatroomRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                DocumentSnapshot doc = task.getResult();
                
                // Check if this chatroom has admin information
                @SuppressWarnings("unchecked")
                List<String> admins = (List<String>) doc.get("admins");
                
                if (admins != null) {
                    onSuccess.onSuccess(admins.contains(userId));
                } else {
                    // No admin list, assume false for 1-on-1 chats
                    onSuccess.onSuccess(false);
                }
            } else {
                onFailure.onFailure(new Exception("Failed to check admin status"));
            }
        });
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

}
