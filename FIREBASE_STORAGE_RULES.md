# Firebase Storage Rules for Voice Messages

## Current Issue
Your app is showing "Object does not exist at location" error when uploading voice messages. This is likely due to Firebase Storage rules or configuration issues.

## Solution 1: Update Firebase Storage Rules

Go to your Firebase Console → Storage → Rules and update the rules to allow authenticated users to upload:

```javascript
rules_version = '2';

service firebase.storage {
  match /b/{bucket}/o {
    // Allow authenticated users to upload voice messages
    match /voice_messages/{allPaths=**} {
      allow read, write: if request.auth != null;
    }
    
    // Allow authenticated users to upload profile pictures
    match /profile_pic/{userId}.jpg {
      allow read: if request.auth != null;
      allow write: if request.auth != null && request.auth.uid == userId;
    }
    
    // Fallback rule for testing (remove in production)
    match /{allPaths=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

## Solution 2: Initialize Firebase Storage Properly

Make sure your Firebase Storage is properly initialized. Check these steps:

1. **Go to Firebase Console**
2. **Navigate to Storage**
3. **Click "Get Started"** if Storage is not yet initialized
4. **Choose "Start in test mode"** for now (we'll secure it later)
5. **Select a location** (preferably same as your Firestore)

## Solution 3: Test with Simple Rules (Temporary)

For testing purposes, you can use these simple rules (NOT for production):

```javascript
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /{allPaths=**} {
      allow read, write: if true;  // WARNING: This allows all access
    }
  }
}
```

**⚠️ WARNING:** The above rules allow anyone to read/write. Only use for testing!

## Solution 4: Check Your google-services.json

Make sure your `google-services.json` file includes the storage bucket configuration:

```json
{
  "project_info": {
    "storage_bucket": "your-project-name.firebasestorage.app"
  }
}
```

## Testing Steps

1. Apply the storage rules above
2. Make sure Firebase Storage is initialized in your Firebase Console
3. Test uploading a voice message
4. Check the Firebase Console → Storage to see if files are being uploaded

## Production Rules (Use Later)

Once testing works, use these secure rules:

```javascript
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    // Voice messages - only authenticated users can upload, everyone can read
    match /voice_messages/{messageId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null 
                  && resource == null  // Only allow new uploads
                  && request.resource.size <= 10 * 1024 * 1024  // Max 10MB
                  && request.resource.contentType.matches('audio/.*');
    }
    
    // Profile pictures
    match /profile_pic/{userId}.jpg {
      allow read: if request.auth != null;
      allow write: if request.auth != null 
                  && request.auth.uid == userId
                  && request.resource.size <= 5 * 1024 * 1024  // Max 5MB
                  && request.resource.contentType.matches('image/.*');
    }
  }
}
```

## Next Steps

1. **Apply the test rules** (Solution 1 or 3)
2. **Test voice message upload**
3. **If it works**, switch to production rules
4. **If it still fails**, check the Firebase Console logs for more details