package com.example.talkifyy.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class UnsendConfig {
    private static final String PREFS_NAME = "unsend_config";
    private static final String KEY_TIME_WINDOW_MINUTES = "time_window_minutes";
    private static final long DEFAULT_TIME_WINDOW_MINUTES = 0; // No time limit - can unsend anytime
    
    // Get the unsend time window in minutes
    public static long getUnsendTimeWindowMinutes(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(KEY_TIME_WINDOW_MINUTES, DEFAULT_TIME_WINDOW_MINUTES);
    }
    
    // Set the unsend time window in minutes (0 = no limit)
    public static void setUnsendTimeWindowMinutes(Context context, long minutes) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_TIME_WINDOW_MINUTES, minutes).apply();
    }
    
    // Check if unsend is enabled (time window > 0 or unlimited)
    public static boolean isUnsendEnabled(Context context) {
        return getUnsendTimeWindowMinutes(context) >= 0;
    }
    
    // Get user-friendly time window description
    public static String getTimeWindowDescription(Context context) {
        long minutes = getUnsendTimeWindowMinutes(context);
        if (minutes == 0) {
            return "No time limit";
        } else if (minutes == 1) {
            return "1 minute";
        } else if (minutes < 60) {
            return minutes + " minutes";
        } else {
            long hours = minutes / 60;
            long remainingMinutes = minutes % 60;
            if (remainingMinutes == 0) {
                return hours == 1 ? "1 hour" : hours + " hours";
            } else {
                return hours + "h " + remainingMinutes + "m";
            }
        }
    }
}
