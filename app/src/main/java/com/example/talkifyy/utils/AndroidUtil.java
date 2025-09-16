package com.example.talkifyy.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.ImageView;
import android.widget.Toast;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.example.talkifyy.R;
import com.example.talkifyy.model.UserModel;

public class AndroidUtil {

    public static void showToast(Context context , String message){
        Toast.makeText(context,message,Toast.LENGTH_LONG).show();
    }


    public static void passUserModelAsIntent(Intent intent, UserModel model){
        intent.putExtra("username",model.getUsername());
        intent.putExtra("phone",model.getPhone());
        intent.putExtra("userId",model.getUserId());
        intent.putExtra("fcmToken",model.getFcmToken());
        intent.putExtra("profilePicUrl",model.getProfilePicUrl());


    }


    public static UserModel getUserModelFromIntent(Intent intent){
        if (intent == null) {
            Log.e("AndroidUtil", "❌ Intent is null in getUserModelFromIntent");
            return null;
        }
        
        try {
            UserModel userModel = new UserModel();
            userModel.setUsername(intent.getStringExtra("username"));
            userModel.setPhone(intent.getStringExtra("phone"));
            userModel.setUserId(intent.getStringExtra("userId"));
            userModel.setFcmToken(intent.getStringExtra("fcmToken"));
            userModel.setProfilePicUrl(intent.getStringExtra("profilePicUrl"));
            
            // Log the extracted data for debugging
            Log.d("AndroidUtil", "✅ Extracted user from intent - Username: " + userModel.getUsername() + ", UserId: " + userModel.getUserId());
            
            // Instead of returning null, create a more robust fallback
            if (userModel.getUserId() == null || userModel.getUserId().isEmpty()) {
                Log.w("AndroidUtil", "⚠️ No userId found in intent extras, but still returning UserModel");
                // Don't return null - this could break existing code
                // Instead, set a default username if missing
                if (userModel.getUsername() == null) {
                    userModel.setUsername("Unknown User");
                }
            }
            
            return userModel;
        } catch (Exception e) {
            Log.e("AndroidUtil", "❌ Error extracting user model from intent, returning minimal UserModel", e);
            // Return a minimal UserModel instead of null to prevent crashes
            UserModel fallbackModel = new UserModel();
            fallbackModel.setUsername("Unknown User");
            fallbackModel.setUserId("");
            fallbackModel.setPhone("");
            fallbackModel.setFcmToken("");
            fallbackModel.setProfilePicUrl("");
            return fallbackModel;
        }
    }
    
    public static void setProfilePic(Context context, Uri imageUri, ImageView imageView) {
        Glide.with(context)
                .load(imageUri)
                .apply(RequestOptions.circleCropTransform())
                .placeholder(R.drawable.person_icon)
                .error(R.drawable.person_icon)
                .into(imageView);
    }
    
    public static void setProfilePic(Context context, String imageUrl, ImageView imageView) {
        if (imageUrl == null || imageUrl.isEmpty() || "null".equals(imageUrl)) {
            Log.d("AndroidUtil", "❌ Image URL is null/empty/null string, using default");
            setDefaultProfilePic(imageView);
            return;
        }
        
        // Clear any tint that might affect the loaded image
        imageView.setImageTintList(null);
        
        // Check if this is a base64 data URL
        if (imageUrl.startsWith("data:image")) {
            Log.d("AndroidUtil", "🔠 Loading base64 data URL image");
            loadBase64Image(imageUrl, imageView);
        } else {
            Log.d("AndroidUtil", "🔗 Loading URL image: " + imageUrl);
            // Regular URL - use Glide with robust error handling
            try {
                Glide.with(context)
                        .load(imageUrl)
                        .apply(RequestOptions.circleCropTransform())
                        .placeholder(R.drawable.person_icon)
                        .error(R.drawable.person_icon)
                        .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                            @Override
                            public boolean onLoadFailed(com.bumptech.glide.load.engine.GlideException e, Object model, 
                                com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, 
                                boolean isFirstResource) {
                                Log.e("AndroidUtil", "❌ Glide image load failed for: " + imageUrl, e);
                                setDefaultProfilePic(imageView);
                                return false; // Let Glide handle the error placeholder
                            }

                            @Override
                            public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, 
                                com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, 
                                com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                                Log.d("AndroidUtil", "✅ Image loaded successfully from: " + imageUrl);
                                return false; // Let Glide handle the resource
                            }
                        })
                        .into(imageView);
            } catch (Exception e) {
                Log.e("AndroidUtil", "❌ Exception in Glide image loading: " + imageUrl, e);
                setDefaultProfilePic(imageView);
            }
        }
    }
    
    private static void setDefaultProfilePic(ImageView imageView) {
        imageView.setImageResource(R.drawable.person_icon);
        // Apply a light gray tint to the default icon
        imageView.setImageTintList(android.content.res.ColorStateList.valueOf(
            imageView.getContext().getResources().getColor(R.color.light_gray, null)));
    }
    
    private static void loadBase64Image(String dataUrl, ImageView imageView) {
        try {
            // Extract base64 data from data URL
            // Format: "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD..."
            String base64Data = dataUrl.substring(dataUrl.indexOf(",") + 1);
            
            Log.d("AndroidUtil", "Base64 data length: " + base64Data.length());
            
            // Convert base64 to byte array
            byte[] imageBytes = Base64.decode(base64Data, Base64.DEFAULT);
            
            // Convert byte array to bitmap
            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            
            if (bitmap != null) {
                Log.d("AndroidUtil", "✅ Base64 image decoded successfully");
                
                // Clear any existing tint before loading the actual image
                imageView.setImageTintList(null);
                
                // Apply circular crop using Glide
                Glide.with(imageView.getContext())
                        .load(bitmap)
                        .apply(RequestOptions.circleCropTransform())
                        .placeholder(R.drawable.person_icon)
                        .error(R.drawable.person_icon)
                        .into(imageView);
            } else {
                Log.e("AndroidUtil", "❌ Failed to decode base64 image");
                setDefaultProfilePic(imageView);
            }
            
        } catch (Exception e) {
            Log.e("AndroidUtil", "❌ Error loading base64 image", e);
            setDefaultProfilePic(imageView);
        }
    }
}
