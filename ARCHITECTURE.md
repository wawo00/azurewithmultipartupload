# System Architecture Visualization

## Component Interaction Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                    AudioStreamController                         │
│  • Lifecycle Management (start/stop/release)                    │
│  • Component Coordination                                        │
│  • State Callbacks & Error Handling                             │
└──────────┬────────────────────────┬────────────────────┬────────┘
           │                        │                    │
           ▼                        ▼                    ▼
   ┌──────────────┐      ┌──────────────────┐   ┌─────────────┐
   │  WMARecorder │      │ FileChunkManager │   │ UploadQueue │
   │              │      │                  │   │             │
   │ • Record     │      │ • Monitor Size   │   │ • Workers   │
   │ • Write File │      │ • Create Chunks  │   │ • Upload    │
   └──────┬───────┘      │ • 256KB Slicing  │   │ • Retry     │
          │              └──────┬───────────┘   └─────┬───────┘
          │                     │                     │
          ▼                     ▼                     ▼
     ┌─────────┐         ┌──────────┐         ┌──────────────┐
     │  Audio  │         │  Chunk   │         │   HTTP       │
     │  File   │────────>│  Data    │────────>│   Upload     │
     │  .aac   │         │  256KB   │         │   Server     │
     └─────────┘         └──────────┘         └──────────────┘
```

## Thread Architecture

```
┌────────────────────────────────────────────────────────────────┐
│                         Main Thread                             │
│  • UI Updates                                                   │
│  • Controller Initialization                                    │
│  • Callback Handling                                            │
└─────────────┬──────────────────────────────────────────────────┘
              │
              ├──> Recording Thread
              │    │
              │    └──> WMARecorder.startRecording()
              │         • Continuously writes audio data
              │
              ├──> Timer Thread (File Size Monitor)
              │    │
              │    └──> Every 1 second
              │         • Check file size
              │         • Trigger chunk creation
              │
              └──> Upload Worker Threads (x2)
                   │
                   ├──> Worker-0
                   │    • Poll chunk from BlockingQueue
                   │    • Upload via HTTP
                   │    • Retry on failure
                   │
                   └──> Worker-1
                        • Poll chunk from BlockingQueue
                        • Upload via HTTP
                        • Retry on failure
```

## Data Flow Sequence

```
1. User Starts Recording
   ↓
2. AudioStreamController.startRecording()
   ↓
3. Create timestamp file: audio_20251120_070936.aac
   ↓
4. Start WMARecorder → Audio data written to file
   ↓
5. Start Timer → Check file size every 1 second
   ↓
6. File grows: 0 → 256KB → 512KB → 768KB...
   ↓
7. Timer detects size change → FileChunkManager.onFileSizeChanged()
   ↓
8. FileChunkManager checks if ≥256KB available
   ↓
9. IF yes → Create ChunkData(256KB, offset, index)
   ↓
10. Add chunk to BlockingQueue
    ↓
11. Upload Worker picks up chunk
    ↓
12. HTTP POST multipart/form-data
    ↓
13. IF success → Callback: onChunkUploadSuccess()
    IF fail → Retry (max 3 attempts)
    ↓
14. Continue until user stops recording
    ↓
15. AudioStreamController.stopRecording()
    ↓
16. Stop WMARecorder
    ↓
17. Stop Timer
    ↓
18. FileChunkManager.processRemainingData()
    ↓
19. Create final chunk (< 256KB) with isLastChunk=true
    ↓
20. Upload remaining chunks
    ↓
21. Callback: onAllChunksUploaded()
```

## Thread Synchronization

```
FileChunkManager (Producer)
    ║
    ║ synchronized(lockObject) {
    ║   while (availableData >= CHUNK_SIZE) {
    ║     createChunk()
    ║     uploadQueue.addChunk(chunk)  ──────┐
    ║   }                                    │
    ║ }                                      │
    ║                                        │
    ▼                                        ▼
                               BlockingQueue<ChunkData>
                                        │
                                        │ thread-safe
                                        │ put() / poll()
                                        │
                                        ▼
                         UploadQueue (Consumer)
                              ║
                              ║ ExecutorService(2 threads)
                              ║   Worker-0 {
                              ║     chunk = queue.poll()
                              ║     uploadWithRetry(chunk)
                              ║   }
                              ║   Worker-1 {
                              ║     chunk = queue.poll()
                              ║     uploadWithRetry(chunk)
                              ║   }
                              ▼
```

## Error Handling & Retry Flow

```
Upload Attempt
     │
     ├──> Attempt 1
     │    │
     │    ├──> Success → onChunkUploadSuccess()
     │    │
     │    └──> Failure
     │         ↓
     │         Wait 1 second
     │         ↓
     ├──> Attempt 2
     │    │
     │    ├──> Success → onChunkUploadSuccess()
     │    │
     │    └──> Failure
     │         ↓
     │         Wait 2 seconds
     │         ↓
     └──> Attempt 3
          │
          ├──> Success → onChunkUploadSuccess()
          │
          └──> Failure
               ↓
               onChunkUploadFailed(chunk, exception)
               ↓
               onRecordingError(exception)
```

## File Chunk Structure

```
Audio File: audio_20251120_070936.aac
├─────────────────────────────────────────────────────┤
│                                                      │
│  Chunk 0         Chunk 1         Chunk 2         Final
│  [0-256KB]      [256-512KB]     [512-768KB]    [768-950KB]
│  index=0        index=1         index=2        index=3
│  offset=0       offset=262144   offset=524288  offset=786432
│  isLast=false   isLast=false    isLast=false   isLast=true
│                                                      │
└─────────────────────────────────────────────────────┘
         Total Size: 950KB = 972,800 bytes

Each ChunkData contains:
  • byte[] data         : The actual audio bytes
  • long offset         : Position in original file
  • int chunkIndex      : Sequential chunk number
  • String fileName     : Original file name
  • boolean isLastChunk : Whether this is the final chunk
  • long timestamp      : Creation timestamp
```

## Upload Request Format

```http
POST /upload HTTP/1.1
Host: your-server.com
Content-Type: multipart/form-data; boundary=----WebKitFormBoundary1638252576000

------WebKitFormBoundary1638252576000
Content-Disposition: form-data; name="file"; filename="audio_20251120_070936.aac"
Content-Type: application/octet-stream

[Binary Audio Data - 262144 bytes]
------WebKitFormBoundary1638252576000
Content-Disposition: form-data; name="chunkIndex"

0
------WebKitFormBoundary1638252576000
Content-Disposition: form-data; name="offset"

0
------WebKitFormBoundary1638252576000
Content-Disposition: form-data; name="fileName"

audio_20251120_070936.aac
------WebKitFormBoundary1638252576000
Content-Disposition: form-data; name="isLastChunk"

false
------WebKitFormBoundary1638252576000
Content-Disposition: form-data; name="chunkSize"

262144
------WebKitFormBoundary1638252576000
Content-Disposition: form-data; name="timestamp"

1700467776000
------WebKitFormBoundary1638252576000--
```

## Android Permission Flow

```
App Launch
    │
    ├──> MainActivity.onCreate()
    │    │
    │    └──> checkPermissions()
    │         │
    │         ├──> All Granted
    │         │    │
    │         │    └──> Ready to record
    │         │
    │         └──> Not Granted
    │              │
    │              └──> requestPermissions()
    │                   │
    │                   ├──> RECORD_AUDIO
    │                   ├──> WRITE_EXTERNAL_STORAGE
    │                   └──> INTERNET
    │                        │
    │                        ▼
    │              onRequestPermissionsResult()
    │                        │
    │                        ├──> All Granted → Ready
    │                        │
    │                        └──> Denied → Show error
    │
    └──> User clicks "Start Recording"
         │
         └──> controller.startRecording()
```

## State Machine

```
     ┌─────────┐
     │  IDLE   │
     └────┬────┘
          │
          │ startRecording()
          ▼
     ┌──────────┐
     │RECORDING │──────┐
     └────┬─────┘      │
          │            │ onRecordingError()
          │            │
          │ stopRecording()
          │            │
          ▼            ▼
     ┌──────────┐  ┌──────┐
     │UPLOADING │  │ERROR │
     │REMAINING │  └──────┘
     └────┬─────┘
          │
          │ onAllChunksUploaded()
          ▼
     ┌─────────┐
     │COMPLETE │
     └─────────┘
```

## Configuration Matrix

| Component | Parameter | Default | Configurable |
|-----------|-----------|---------|--------------|
| FileChunkManager | CHUNK_SIZE | 256KB | ✓ |
| UploadQueue | Worker Threads | 2 | ✓ |
| UploadQueue | Max Retries | 3 | ✓ |
| UploadQueue | Retry Backoff | Exponential | ✓ |
| AudioStreamController | File Check Interval | 1000ms | ✓ |
| AudioStreamController | File Name Format | audio_YYYYMMdd_HHmmss.aac | ✓ |
| WMARecorder | Sample Rate | 44100 Hz | ✓ |
| WMARecorder | Bit Depth | 16 bit | ✓ |
| WMARecorder | Channels | 2 (Stereo) | ✓ |

## Memory & Performance Characteristics

```
Memory Usage per Chunk:
  • ChunkData object: ~40 bytes (object overhead)
  • byte[] data: 262,144 bytes
  • Metadata fields: ~64 bytes
  • Total: ~262KB per chunk in queue

Peak Memory (2 workers, 5 chunks in queue):
  • 5 chunks × 262KB = 1.31 MB
  • Worker threads: ~1 MB each
  • Controller overhead: ~100 KB
  • Total: ~3.5 MB

Performance:
  • Chunk creation: ~5ms per chunk
  • Queue operations: <1ms (thread-safe)
  • Upload time: Network dependent
  • File size check: <1ms
```

## Class Diagram

```
┌──────────────────────────────────────┐
│      AudioStreamController           │
├──────────────────────────────────────┤
│ - recorder: WMARecorder              │
│ - chunkManager: FileChunkManager     │
│ - uploadQueue: UploadQueue           │
│ - fileSizeMonitor: Timer             │
│ - isRecording: boolean               │
├──────────────────────────────────────┤
│ + startRecording(): void             │
│ + stopRecording(): void              │
│ + release(): void                    │
│ + isRecording(): boolean             │
│ - generateFilePath(): String         │
└──────────────┬───────────────────────┘
               │ uses
               ▼
┌──────────────────────────────────────┐
│       FileChunkManager               │
├──────────────────────────────────────┤
│ - filePath: String                   │
│ - lastUploadedPosition: long         │
│ - chunkIndex: int                    │
│ - uploadQueue: UploadQueue           │
│ - lockObject: Object                 │
├──────────────────────────────────────┤
│ + onFileSizeChanged(long): void      │
│ + processRemainingData(): void       │
│ - processChunks(long, boolean): void │
│ + reset(String): void                │
└──────────────┬───────────────────────┘
               │ creates
               ▼
┌──────────────────────────────────────┐
│          ChunkData                   │
├──────────────────────────────────────┤
│ - data: byte[]                       │
│ - offset: long                       │
│ - chunkIndex: int                    │
│ - fileName: String                   │
│ - isLastChunk: boolean               │
│ - timestamp: long                    │
├──────────────────────────────────────┤
│ + getters/setters                    │
│ + getSize(): int                     │
└──────────────┬───────────────────────┘
               │ queued to
               ▼
┌──────────────────────────────────────┐
│         UploadQueue                  │
├──────────────────────────────────────┤
│ - chunkQueue: BlockingQueue          │
│ - uploadExecutor: ExecutorService    │
│ - isRunning: boolean                 │
│ - uploadListener: UploadListener     │
├──────────────────────────────────────┤
│ + addChunk(ChunkData): void          │
│ + startUploadWorker(): void          │
│ + stop(): void                       │
│ - uploadChunkWithRetry(chunk): void  │
└──────────────────────────────────────┘
```

This architecture ensures:
✓ Decoupled components
✓ Thread-safe operations
✓ Scalable upload handling
✓ Robust error recovery
✓ Clean resource management
