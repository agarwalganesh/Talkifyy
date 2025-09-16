package com.example.talkifyy.model;

public class ImageMetadata {
    private int width;
    private int height;
    private long fileSize; // in bytes
    private String fileName;
    private String mimeType;
    private long uploadTimestamp;
    
    public ImageMetadata() {
        // Default constructor required for Firebase
    }
    
    public ImageMetadata(int width, int height, long fileSize, String fileName, String mimeType) {
        this.width = width;
        this.height = height;
        this.fileSize = fileSize;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.uploadTimestamp = System.currentTimeMillis();
    }
    
    // Getters and setters
    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
    
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }
    
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    
    public long getUploadTimestamp() { return uploadTimestamp; }
    public void setUploadTimestamp(long uploadTimestamp) { this.uploadTimestamp = uploadTimestamp; }
    
    public String getFormattedFileSize() {
        if (fileSize < 1024) return fileSize + " B";
        else if (fileSize < 1024 * 1024) return String.format("%.1f KB", fileSize / 1024.0);
        else return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
    }
}