package com.example.talkifyy.adapter;

/**
 * Interface for handling message editing operations
 */
public interface EditMessageListener {
    /**
     * Called when a message needs to be edited
     * @param messageId The ID of the message to edit
     * @param currentMessage The current content of the message
     */
    void onEditMessage(String messageId, String currentMessage);
}