package com.example.talkifyy.utils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import com.example.talkifyy.R;
import com.example.talkifyy.model.ChatMessageModel;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Utility class for managing message reactions
 */
public class MessageReactionManager {

    private static final String TAG = "MessageReactionManager";

    // Available emoji reactions
    public static final List<String> AVAILABLE_EMOJIS = Arrays.asList("👍", "❤️", "😂", "😮", "😢", "😡");
    public static final List<Integer> EMOJI_IDS = Arrays.asList(
        R.id.emoji_thumbs_up, R.id.emoji_heart, R.id.emoji_laugh,
        R.id.emoji_wow, R.id.emoji_sad, R.id.emoji_angry
    );

    /**
     * Interface for handling reaction events
     */
    public interface OnReactionClickListener {
        void onReactionSelected(String emoji);
        void onReactionToggle(String emoji);
    }

    /**
     * Show reaction picker popup
     * @param context Application context
     * @param anchorView View to anchor the popup to
     * @param listener Reaction click listener
     */
    public static void showReactionPicker(Context context, View anchorView, OnReactionClickListener listener) {
        // Create popup view
        View popupView = LayoutInflater.from(context).inflate(R.layout.reaction_picker_dialog, null);
        
        // Create popup window
        PopupWindow popupWindow = new PopupWindow(popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);

        // Set popup properties
        popupWindow.setElevation(16f);
        popupWindow.setAnimationStyle(android.R.style.Animation_Dialog);

        // Set click listeners for each emoji
        for (int i = 0; i < EMOJI_IDS.size(); i++) {
            final String emoji = AVAILABLE_EMOJIS.get(i);
            TextView emojiView = popupView.findViewById(EMOJI_IDS.get(i));
            
            emojiView.setOnClickListener(v -> {
                // Add scale animation
                animateEmojiSelection(emojiView, () -> {
                    if (listener != null) {
                        listener.onReactionSelected(emoji);
                    }
                    popupWindow.dismiss();
                });
            });
        }

        // Calculate position
        int[] anchorLocation = new int[2];
        anchorView.getLocationOnScreen(anchorLocation);

        // Show popup above the message
        int yOffset = -(anchorView.getHeight() + 20);
        popupWindow.showAsDropDown(anchorView, 0, yOffset);

        // Animate popup entrance
        animatePopupEntrance(popupView);

        Log.d(TAG, "Reaction picker shown");
    }

    /**
     * Update reactions display in a message layout
     * @param reactionsContainer Container to display reactions
     * @param message Message model with reactions
     * @param context Application context
     * @param listener Reaction click listener
     */
    public static void updateReactionsDisplay(LinearLayout reactionsContainer, 
                                            ChatMessageModel message, 
                                            Context context,
                                            OnReactionClickListener listener) {
        
        if (reactionsContainer == null) {
            Log.w(TAG, "Reactions container is null");
            return;
        }

        // Clear existing reactions
        reactionsContainer.removeAllViews();

        if (!message.hasReactions()) {
            reactionsContainer.setVisibility(View.GONE);
            return;
        }

        reactionsContainer.setVisibility(View.VISIBLE);

        Map<String, List<String>> reactions = message.getReactions();
        String currentUserId = FirebaseUtil.currentUserId();

        for (Map.Entry<String, List<String>> entry : reactions.entrySet()) {
            String emoji = entry.getKey();
            List<String> userIds = entry.getValue();
            
            if (userIds == null || userIds.isEmpty()) {
                continue;
            }

            // Create reaction item view
            View reactionView = LayoutInflater.from(context).inflate(R.layout.reaction_item, null);
            TextView emojiText = reactionView.findViewById(R.id.reaction_emoji);
            TextView countText = reactionView.findViewById(R.id.reaction_count);

            emojiText.setText(emoji);
            countText.setText(String.valueOf(userIds.size()));

            // Highlight if current user has reacted
            boolean userReacted = userIds.contains(currentUserId);
            if (userReacted) {
                reactionView.setBackgroundResource(R.drawable.reaction_item_background);
                reactionView.setSelected(true);
            }

            // Set click listener to toggle reaction
            reactionView.setOnClickListener(v -> {
                animateReactionToggle(reactionView, () -> {
                    if (listener != null) {
                        listener.onReactionToggle(emoji);
                    }
                });
            });

            // Add to container
            reactionsContainer.addView(reactionView);
        }

        Log.d(TAG, "Updated reactions display with " + reactions.size() + " emoji types");
    }

    /**
     * Animate emoji selection
     */
    private static void animateEmojiSelection(View emojiView, Runnable onComplete) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(emojiView, "scaleX", 1f, 1.3f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(emojiView, "scaleY", 1f, 1.3f, 1f);
        
        scaleX.setDuration(200);
        scaleY.setDuration(200);
        
        scaleX.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
        
        scaleX.start();
        scaleY.start();
    }

    /**
     * Animate reaction toggle
     */
    private static void animateReactionToggle(View reactionView, Runnable onComplete) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(reactionView, "scaleX", 1f, 0.9f, 1.1f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(reactionView, "scaleY", 1f, 0.9f, 1.1f, 1f);
        
        scaleX.setDuration(300);
        scaleY.setDuration(300);
        
        scaleX.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
        
        scaleX.start();
        scaleY.start();
    }

    /**
     * Animate popup entrance
     */
    private static void animatePopupEntrance(View popupView) {
        popupView.setAlpha(0f);
        popupView.setScaleX(0.8f);
        popupView.setScaleY(0.8f);
        
        popupView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200)
                .start();
    }

    /**
     * Show reaction feedback toast
     */
    public static void showReactionFeedback(Context context, String emoji, boolean added) {
        String message = added ? "Reacted with " + emoji : "Removed " + emoji + " reaction";
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * Get emoji name for accessibility
     */
    public static String getEmojiName(String emoji) {
        switch (emoji) {
            case "👍": return "Thumbs up";
            case "❤️": return "Heart";
            case "😂": return "Laughing";
            case "😮": return "Wow";
            case "😢": return "Sad";
            case "😡": return "Angry";
            default: return "Reaction";
        }
    }
}