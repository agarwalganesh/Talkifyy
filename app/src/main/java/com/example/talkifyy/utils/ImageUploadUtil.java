package com.example.talkifyy.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import com.example.talkifyy.model.ImageMetadata;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ImageUploadUtil {
    private static final String TAG = "ImageUploadUtil";
    private static final int MAX_IMAGE_WIDTH = 1920;
    private static final int MAX_IMAGE_HEIGHT = 1920;
    private static final int JPEG_QUALITY = 85; // High quality compression
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB max per image

    public interface ImageUploadListener {
        void onUploadProgress(int progress);
        void onUploadSuccess(String downloadUrl, ImageMetadata metadata);
        void onUploadFailure(Exception e);
    }

    public interface MultipleImageUploadListener {
        void onUploadProgress(int completedCount, int totalCount);
        void onUploadSuccess(List<String> downloadUrls);
        void onUploadFailure(Exception e);
    }

    /**
     * Upload a single image to Firebase Storage
     */
    public static void uploadImage(Context context, Uri imageUri, String chatroomId, ImageUploadListener listener) {
        Log.d(TAG, "Starting image upload - URI: " + imageUri + ", ChatroomId: " + chatroomId);
        
        try {
            // Compress image
            Log.d(TAG, "Compressing image...");
            byte[] compressedData = compressImage(context, imageUri);
            if (compressedData == null) {
                Log.e(TAG, "Image compression failed");
                listener.onUploadFailure(new Exception("Failed to compress image"));
                return;
            }
            Log.d(TAG, "Image compressed successfully, size: " + compressedData.length + " bytes");

            // Get image metadata
            Log.d(TAG, "Getting image metadata...");
            ImageMetadata metadata = getImageMetadata(context, imageUri, compressedData.length);
            Log.d(TAG, "Metadata: " + metadata.getWidth() + "x" + metadata.getHeight() + ", " + metadata.getFormattedFileSize());
            
            // Generate unique filename
            String fileName = "chat_images/" + chatroomId + "/" + UUID.randomUUID().toString() + ".jpg";
            Log.d(TAG, "Generated filename: " + fileName);
            
            // Upload to Firebase Storage
            Log.d(TAG, "Starting Firebase Storage upload...");
            StorageReference storageRef = FirebaseStorage.getInstance().getReference().child(fileName);
            
            UploadTask uploadTask = storageRef.putBytes(compressedData);
            uploadTask.addOnProgressListener(taskSnapshot -> {
                double progress = (100.0 * taskSnapshot.getBytesTransferred()) / taskSnapshot.getTotalByteCount();
                listener.onUploadProgress((int) progress);
            }).addOnSuccessListener(taskSnapshot -> {
                storageRef.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                    Log.d(TAG, "Image uploaded successfully: " + downloadUri.toString());
                    listener.onUploadSuccess(downloadUri.toString(), metadata);
                }).addOnFailureListener(listener::onUploadFailure);
            }).addOnFailureListener(listener::onUploadFailure);

        } catch (Exception e) {
            Log.e(TAG, "Error uploading image", e);
            listener.onUploadFailure(e);
        }
    }

    /**
     * Upload multiple images to Firebase Storage
     */
    public static void uploadMultipleImages(Context context, List<Uri> imageUris, String chatroomId, 
                                          MultipleImageUploadListener listener) {
        if (imageUris == null || imageUris.isEmpty()) {
            listener.onUploadFailure(new Exception("No images to upload"));
            return;
        }

        List<String> downloadUrls = new ArrayList<>();
        final int[] completedCount = {0};
        final int totalCount = imageUris.size();

        for (int i = 0; i < imageUris.size(); i++) {
            Uri imageUri = imageUris.get(i);
            
            uploadImage(context, imageUri, chatroomId, new ImageUploadListener() {
                @Override
                public void onUploadProgress(int progress) {
                    // Individual progress can be tracked here if needed
                }

                @Override
                public void onUploadSuccess(String downloadUrl, ImageMetadata metadata) {
                    downloadUrls.add(downloadUrl);
                    completedCount[0]++;
                    
                    listener.onUploadProgress(completedCount[0], totalCount);
                    
                    if (completedCount[0] == totalCount) {
                        listener.onUploadSuccess(downloadUrls);
                    }
                }

                @Override
                public void onUploadFailure(Exception e) {
                    Log.e(TAG, "Failed to upload image " + (completedCount[0] + 1), e);
                    listener.onUploadFailure(e);
                }
            });
        }
    }

    /**
     * Compress image while maintaining good quality
     */
    private static byte[] compressImage(Context context, Uri imageUri) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
            Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream);
            inputStream.close();

            if (originalBitmap == null) {
                return null;
            }

            // Calculate scaled dimensions
            int[] scaledDimensions = calculateScaledDimensions(
                originalBitmap.getWidth(), 
                originalBitmap.getHeight()
            );

            // Scale bitmap if needed
            Bitmap scaledBitmap;
            if (scaledDimensions[0] != originalBitmap.getWidth() || 
                scaledDimensions[1] != originalBitmap.getHeight()) {
                scaledBitmap = Bitmap.createScaledBitmap(
                    originalBitmap, 
                    scaledDimensions[0], 
                    scaledDimensions[1], 
                    true
                );
                originalBitmap.recycle();
            } else {
                scaledBitmap = originalBitmap;
            }

            // Compress to JPEG
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream);
            byte[] compressedData = outputStream.toByteArray();
            
            scaledBitmap.recycle();
            outputStream.close();

            Log.d(TAG, "Image compressed - Original size: " + compressedData.length + " bytes");
            return compressedData;

        } catch (IOException e) {
            Log.e(TAG, "Error compressing image", e);
            return null;
        }
    }

    /**
     * Calculate scaled dimensions to fit within max limits
     */
    private static int[] calculateScaledDimensions(int originalWidth, int originalHeight) {
        if (originalWidth <= MAX_IMAGE_WIDTH && originalHeight <= MAX_IMAGE_HEIGHT) {
            return new int[]{originalWidth, originalHeight};
        }

        double widthRatio = (double) MAX_IMAGE_WIDTH / originalWidth;
        double heightRatio = (double) MAX_IMAGE_HEIGHT / originalHeight;
        double scaleFactor = Math.min(widthRatio, heightRatio);

        int scaledWidth = (int) (originalWidth * scaleFactor);
        int scaledHeight = (int) (originalHeight * scaleFactor);

        return new int[]{scaledWidth, scaledHeight};
    }

    /**
     * Get image metadata
     */
    private static ImageMetadata getImageMetadata(Context context, Uri imageUri, long compressedSize) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(inputStream, null, options);
            inputStream.close();

            String fileName = "image_" + System.currentTimeMillis() + ".jpg";
            String mimeType = context.getContentResolver().getType(imageUri);
            if (mimeType == null) mimeType = "image/jpeg";

            return new ImageMetadata(
                options.outWidth, 
                options.outHeight, 
                compressedSize, 
                fileName, 
                mimeType
            );
        } catch (Exception e) {
            Log.e(TAG, "Error getting image metadata", e);
            return new ImageMetadata(0, 0, compressedSize, "unknown.jpg", "image/jpeg");
        }
    }

    /**
     * Delete image from Firebase Storage
     */
    public static void deleteImage(String imageUrl, DeleteImageListener listener) {
        try {
            StorageReference storageRef = FirebaseStorage.getInstance().getReferenceFromUrl(imageUrl);
            storageRef.delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Image deleted successfully: " + imageUrl);
                    if (listener != null) listener.onDeleteSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to delete image: " + imageUrl, e);
                    if (listener != null) listener.onDeleteFailure(e);
                });
        } catch (Exception e) {
            Log.e(TAG, "Error deleting image", e);
            if (listener != null) listener.onDeleteFailure(e);
        }
    }

    public interface DeleteImageListener {
        void onDeleteSuccess();
        void onDeleteFailure(Exception e);
    }
}