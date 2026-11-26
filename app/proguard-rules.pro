# Keep Firestore data models (serialized/deserialized via reflection)
-keep class com.example.data.UserProfile { *; }
-keep class com.example.data.SocialGroup { *; }
-keep class com.example.data.Outing { *; }
-keep class com.example.data.GroupChatMessage { *; }
-keep class com.example.data.OutingStory { *; }
-keep class com.example.data.OutingStatus { *; }
-keep class com.example.data.RsvpChoice { *; }

# Keep Moshi DTOs and generated adapters
-keep class com.example.data.*Dto { *; }
-keep class com.example.data.*Response { *; }
-keep class com.example.data.*JsonAdapter { *; }

# Keep Retrofit service interfaces
-keep interface com.example.data.BibleApiService { *; }
-keep interface com.example.data.BollsBibleApiService { *; }

# Keep stack trace line numbers for crash diagnostics
-keepattributes SourceFile,LineNumberTable
