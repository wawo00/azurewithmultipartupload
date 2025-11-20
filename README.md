# Android Audio Stream Recording with Multipart Upload

This project implements a complete Android audio stream recording and multipart upload system using a producer-consumer pattern.

## 🎯 Features

- **Real-time Audio Recording**: Continuous audio capture using MediaRecorder
- **Automatic Chunking**: Splits audio files into 256KB chunks
- **Concurrent Upload**: Multiple upload workers for parallel chunk upload
- **Producer-Consumer Pattern**: Decoupled recording and uploading processes
- **Thread-Safe Operations**: Synchronized access to shared resources
- **Retry Mechanism**: Automatic retry for failed uploads with exponential backoff
- **Resource Management**: Graceful cleanup of threads and file resources

## 📁 Project Structure

```
azurewithmultipartupload/
├── src/main/java/com/wally/audio/
│   ├── WMARecorder.java           # Audio recorder (Java Sound API)
│   ├── ChunkData.java              # Chunk data model
│   ├── UploadQueue.java            # Upload queue with worker threads
│   ├── FileChunkManager.java      # File chunking manager
│   └── AudioStreamController.java  # Main controller
│
└── android-example/
    └── app/src/main/
        ├── java/com/wally/sdkdemo/
        │   └── MainActivity.java   # Android usage example
        ├── AndroidManifest.xml     # Permissions configuration
        ├── res/layout/
        │   └── activity_main.xml   # UI layout
        └── build.gradle            # Dependencies
```

## 🚀 Quick Start

### 1. Core Components

#### AudioStreamController

The main controller that coordinates all components:

```java
// Initialize the controller
String cacheDir = getCacheDir().getAbsolutePath();
String uploadUrl = "https://your-server.com/upload";

AudioStreamController controller = new AudioStreamController(
    cacheDir, 
    uploadUrl, 
    new AudioStreamController.ControllerListener() {
        @Override
        public void onRecordingStarted(String filePath) {
            Log.d("Audio", "Recording started: " + filePath);
        }
        
        @Override
        public void onRecordingStopped() {
            Log.d("Audio", "Recording stopped");
        }
        
        @Override
        public void onRecordingError(Exception e) {
            Log.e("Audio", "Error: " + e.getMessage());
        }
        
        @Override
        public void onChunkCreated(ChunkData chunk) {
            Log.d("Audio", "Chunk created: " + chunk.getChunkIndex());
        }
        
        @Override
        public void onUploadProgress(int uploadedChunks, int totalChunks) {
            Log.d("Audio", "Progress: " + uploadedChunks + "/" + totalChunks);
        }
        
        @Override
        public void onAllUploadsComplete() {
            Log.d("Audio", "All uploads complete!");
        }
    }
);

// Start recording
controller.startRecording();

// Stop recording
controller.stopRecording();

// Clean up
controller.release();
```

### 2. Android Integration

#### Required Permissions (AndroidManifest.xml)

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

#### Request Permissions at Runtime

```java
private static final int PERMISSION_REQUEST_CODE = 1001;

private void requestPermissions() {
    ActivityCompat.requestPermissions(this,
        new String[]{
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET
        },
        PERMISSION_REQUEST_CODE);
}
```

### 3. Build Configuration (build.gradle)

```gradle
dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.squareup.okhttp3:okhttp:4.11.0'
    implementation 'com.squareup.retrofit2:retrofit:2.9.0'
}

android {
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_11
        targetCompatibility JavaVersion.VERSION_11
    }
}
```

## 🏗️ Architecture

### Producer-Consumer Pattern

```
┌─────────────────┐         ┌──────────────────┐         ┌─────────────────┐
│   WMARecorder   │────────>│ FileChunkManager │────────>│  UploadQueue    │
│   (Producer)    │         │   (Chunking)     │         │  (Consumer)     │
└─────────────────┘         └──────────────────┘         └─────────────────┘
        │                            │                             │
        │                            │                             │
        v                            v                             v
  Audio Stream                 256KB Chunks              Upload Workers (x2)
```

### Component Responsibilities

1. **WMARecorder**: Captures audio and writes to file
2. **FileChunkManager**: Monitors file size and creates 256KB chunks
3. **UploadQueue**: Manages upload queue with 2 concurrent workers
4. **AudioStreamController**: Orchestrates all components and lifecycle

### Thread Safety

- **Synchronized blocks** in FileChunkManager for chunk creation
- **BlockingQueue** in UploadQueue for thread-safe chunk queueing
- **ExecutorService** for managed thread pool
- **Volatile flags** for state management

## 📊 File Naming Convention

Audio files are saved with timestamp format:

```
/data/user/0/com.wally.sdkdemo/cache/audio_20251120_070936.aac
```

Format: `audio_YYYYMMDD_HHmmss.aac`

## 🔧 Configuration

### Chunk Size

Default: 256KB (configurable in FileChunkManager)

```java
private static final int CHUNK_SIZE = 256 * 1024; // 256KB
```

### Upload Workers

Default: 2 concurrent workers (configurable in UploadQueue)

```java
private ExecutorService uploadExecutor = Executors.newFixedThreadPool(2);
```

### File Size Check Interval

Default: 1 second (configurable in AudioStreamController)

```java
private static final long FILE_SIZE_CHECK_INTERVAL = 1000; // 1 second
```

### Retry Configuration

Default: 3 retries with exponential backoff (configurable in UploadQueue)

```java
int maxRetries = 3;
Thread.sleep(1000 * retryCount); // Exponential backoff
```

## 📡 Upload Format

Chunks are uploaded using `multipart/form-data` format:

```
POST /upload HTTP/1.1
Content-Type: multipart/form-data; boundary=----WebKitFormBoundary...

------WebKitFormBoundary...
Content-Disposition: form-data; name="file"; filename="audio_20251120_070936.aac"
Content-Type: application/octet-stream

[Binary Data]
------WebKitFormBoundary...
Content-Disposition: form-data; name="chunkIndex"

0
------WebKitFormBoundary...
Content-Disposition: form-data; name="offset"

0
------WebKitFormBoundary...
Content-Disposition: form-data; name="isLastChunk"

false
------WebKitFormBoundary...--
```

## 🧪 Testing

The system ensures:

1. **Thread Safety**: All concurrent operations are synchronized
2. **Data Integrity**: Chunks maintain proper order and offset
3. **Error Handling**: Graceful handling of failures with retry
4. **Resource Cleanup**: Proper thread and file resource management

## ⚠️ Important Notes

### For Android Implementation

The current `WMARecorder.java` uses Java Sound API (`javax.sound.sampled`) which is **not available on Android**. For Android, you need to:

1. Replace with `MediaRecorder` or `AudioRecord` API
2. Use Android-specific audio formats (AAC, MP3, etc.)
3. Handle Android-specific permissions and lifecycle

### Example Android MediaRecorder Implementation

```java
MediaRecorder recorder = new MediaRecorder();
recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
recorder.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
recorder.setOutputFile(audioFile.getAbsolutePath());
recorder.prepare();
recorder.start();
```

## 🔒 Security Considerations

1. **HTTPS**: Use HTTPS for upload endpoint in production
2. **Authentication**: Add authentication headers to upload requests
3. **File Validation**: Validate file size and format
4. **Permission Handling**: Properly request and check runtime permissions
5. **Network Security**: Configure network security policy

## 📝 License

This project is provided as-is for educational and development purposes.

## 🤝 Contributing

Contributions are welcome! Please ensure:

- Thread-safe operations
- Proper error handling
- Comprehensive documentation
- Test coverage for critical paths

## 📞 Support

For issues or questions, please open an issue in the GitHub repository.
