# Talkifyy - Firebase Chat Application

A real-time chat application built with Android and Firebase, featuring phone number authentication, real-time messaging, and push notifications.

## Features

- 📱 Phone number authentication with OTP verification
- 💬 Real-time messaging using Firebase Firestore
- 🔍 User search functionality
- 📱 Push notifications via FCM
- 👤 User profiles
- 🎨 Modern Material Design UI

## Project Structure

### Activities
- `SplashActivity.java` - App launch screen with authentication check
- `LoginPhoneNumberActivity.java` - Phone number input for authentication
- `LoginOtpActivity.java` - OTP verification screen
- `LoginUsernameActivity.java` - Username setup for new users
- `MainActivity.java` - Main app interface with bottom navigation
- `ChatActivity.java` - Individual chat conversation screen
- `SearchUserActivity.java` - User search interface

### Fragments
- `ChatFragment.java` - Recent chats list in main activity
- `ProfileFragment.java` - User profile display and logout
- `SearchUserFragment.java` - Search results display

### Models
- `UserModel.java` - User data structure
- `ChatMessageModel.java` - Chat message data structure
- `ChatroomModel.java` - Chat room data structure

### Adapters
- `ChatRecyclerAdapter.java` - Chat messages display
- `RecentChatRecyclerAdapter.java` - Recent chats list
- `SearchUserRecyclerAdapter.java` - Search results list

### Utilities
- `FirebaseUtil.java` - Firebase operations helper
- `AndroidUtil.java` - Android utility functions

### Services
- `FCMNotificationService.java` - Push notification handling

## Firebase Setup

1. **Create Firebase Project**
   - Go to [Firebase Console](https://console.firebase.google.com/)
   - Create a new project
   - Enable Authentication, Firestore, and Cloud Messaging

2. **Configure Authentication**
   - In Firebase Console, go to Authentication > Sign-in method
   - Enable "Phone" authentication
   - Configure your country/region settings

3. **Configure Firestore**
   - In Firebase Console, go to Firestore Database
   - Create database in production mode
   - Set up the following collections structure:
     ```
     users/
       {userId}/
         - phone: string
         - username: string
         - userId: string
         - fcmToken: string
         - createdTimestamp: timestamp
     
     chatrooms/
       {chatroomId}/
         - chatroomId: string
         - userIds: array
         - lastMessage: string
         - lastMessageSenderId: string
         - lastMessageTimestamp: timestamp
         
         chats/
           {messageId}/
             - message: string
             - senderId: string
             - timestamp: timestamp
     ```

4. **Download Configuration File**
   - In Firebase Console, go to Project Settings
   - Download `google-services.json`
   - Place it in `app/` directory (already included)

## Installation & Setup

1. **Clone the project** (if from repository)
   ```bash
   git clone <repository-url>
   cd Talkifyy
   ```

2. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an existing project"
   - Choose the project directory

3. **Sync Project**
   - Let Android Studio sync all dependencies
   - Make sure `google-services.json` is in the `app/` folder

4. **Run the Application**
   - Connect an Android device or start an emulator
   - Click "Run" in Android Studio

## Firebase Security Rules

### Firestore Rules
```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Users can read/write their own user document
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
    
    // Users can read other users for search functionality
    match /users/{userId} {
      allow read: if request.auth != null;
    }
    
    // Chatroom rules
    match /chatrooms/{chatroomId} {
      allow read, write: if request.auth != null && 
        request.auth.uid in resource.data.userIds;
    }
    
    // Chat messages rules
    match /chatrooms/{chatroomId}/chats/{messageId} {
      allow read, write: if request.auth != null;
    }
  }
}
```

## Dependencies

The app uses the following key dependencies:

- Firebase BOM 34.1.0
- Firebase Auth
- Firebase Firestore
- Firebase Messaging
- Firebase Storage
- FirebaseUI Firestore 9.0.0
- Country Code Picker 2.7.3
- Glide 4.16.0
- Material Design Components
- AndroidX components

## Usage

1. **First Launch**
   - Enter your phone number
   - Verify OTP sent to your phone
   - Set up your username

2. **Main Features**
   - **Chats Tab**: View recent conversations
   - **Profile Tab**: View/edit profile and logout
   - **Search**: Find other users to start conversations

3. **Starting a Chat**
   - Tap search icon in main screen
   - Enter username to search
   - Tap on user to start chatting

## Troubleshooting

### Common Issues

1. **Authentication Issues**
   - Ensure phone authentication is enabled in Firebase Console
   - Check that your app's SHA-1 fingerprint is added to Firebase

2. **Build Issues**
   - Make sure `google-services.json` is in the correct location
   - Sync project with Gradle files
   - Clean and rebuild project

3. **Notifications Not Working**
   - Verify FCM is enabled in Firebase Console
   - Check device/emulator has Google Play Services
   - Ensure POST_NOTIFICATIONS permission is granted

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

This project is open source and available under the [MIT License](LICENSE).
