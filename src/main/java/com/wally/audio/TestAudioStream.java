package com.wally.audio;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

/**
 * Simple test program to demonstrate the audio streaming and upload system
 */
public class TestAudioStream {
    
    public static void main(String[] args) {
        System.out.println("=== Audio Stream Upload System Test ===\n");
        
        // Create test directory
        String testDir = "/tmp/audio-test";
        File dir = new File(testDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        
        // Test 1: FileChunkManager with simulated file
        testFileChunkManager(testDir);
        
        // Test 2: UploadQueue
        testUploadQueue();
        
        // Test 3: AudioStreamController (without actual recording)
        testAudioStreamController(testDir);
        
        System.out.println("\n=== All Tests Completed ===");
    }
    
    /**
     * Test FileChunkManager functionality
     */
    private static void testFileChunkManager(String testDir) {
        System.out.println("--- Test 1: FileChunkManager ---");
        
        try {
            // Create a test file with simulated audio data
            String testFilePath = testDir + "/test_audio.dat";
            File testFile = new File(testFilePath);
            
            // Write 1MB of test data (will be split into 4 chunks of 256KB each)
            System.out.println("Creating test file with 1MB of data...");
            createTestFile(testFile, 1024 * 1024);
            
            // Create upload queue
            TestUploadListener listener = new TestUploadListener();
            UploadQueue uploadQueue = new UploadQueue(listener, "http://test-server.com/upload");
            uploadQueue.startUploadWorker();
            
            // Create chunk manager
            FileChunkManager chunkManager = new FileChunkManager(testFilePath, uploadQueue);
            
            // Simulate file size changes (as if recording is happening)
            System.out.println("Processing chunks...");
            chunkManager.onFileSizeChanged(testFile.length());
            
            // Wait a bit for chunks to be created
            Thread.sleep(500);
            
            // Process remaining data
            chunkManager.processRemainingData();
            
            System.out.println("FileChunkManager created " + chunkManager.getChunkIndex() + " chunks");
            System.out.println("Queue has " + uploadQueue.getPendingChunkCount() + " pending chunks");
            
            // Clean up
            Thread.sleep(1000);
            uploadQueue.stop();
            testFile.delete();
            
            System.out.println("✓ FileChunkManager test completed\n");
            
        } catch (Exception e) {
            System.err.println("✗ FileChunkManager test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Test UploadQueue functionality
     */
    private static void testUploadQueue() {
        System.out.println("--- Test 2: UploadQueue ---");
        
        try {
            TestUploadListener listener = new TestUploadListener();
            UploadQueue uploadQueue = new UploadQueue(listener, "http://test-server.com/upload");
            uploadQueue.startUploadWorker();
            
            // Create test chunks
            System.out.println("Adding 5 test chunks to queue...");
            for (int i = 0; i < 5; i++) {
                byte[] data = new byte[256 * 1024];
                new Random().nextBytes(data);
                
                ChunkData chunk = new ChunkData(data, i * 256 * 1024, i, "test_audio.dat");
                if (i == 4) {
                    chunk.setLastChunk(true);
                }
                
                uploadQueue.addChunk(chunk);
            }
            
            System.out.println("Queue has " + uploadQueue.getPendingChunkCount() + " pending chunks");
            
            // Wait for queue to process (note: actual upload will fail since server doesn't exist)
            Thread.sleep(2000);
            
            uploadQueue.stop();
            
            System.out.println("✓ UploadQueue test completed\n");
            
        } catch (Exception e) {
            System.err.println("✗ UploadQueue test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Test AudioStreamController functionality (without actual audio recording)
     */
    private static void testAudioStreamController(String testDir) {
        System.out.println("--- Test 3: AudioStreamController ---");
        
        try {
            TestControllerListener listener = new TestControllerListener();
            AudioStreamController controller = new AudioStreamController(
                testDir,
                "http://test-server.com/upload",
                listener
            );
            
            System.out.println("Controller created successfully");
            System.out.println("Cache directory: " + testDir);
            System.out.println("Is recording: " + controller.isRecording());
            
            // Test lifecycle methods (without actual recording since we're not on Android)
            System.out.println("\nNote: Actual recording requires Android MediaRecorder API");
            System.out.println("Controller provides the following capabilities:");
            System.out.println("  - startRecording() / stopRecording()");
            System.out.println("  - File size monitoring");
            System.out.println("  - Automatic chunking");
            System.out.println("  - Upload coordination");
            System.out.println("  - Lifecycle management");
            
            // Clean up
            controller.release();
            
            System.out.println("\n✓ AudioStreamController test completed\n");
            
        } catch (Exception e) {
            System.err.println("✗ AudioStreamController test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Create a test file with random data
     */
    private static void createTestFile(File file, int size) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            byte[] buffer = new byte[8192];
            Random random = new Random();
            int remaining = size;
            
            while (remaining > 0) {
                int toWrite = Math.min(buffer.length, remaining);
                random.nextBytes(buffer);
                fos.write(buffer, 0, toWrite);
                remaining -= toWrite;
            }
        }
    }
    
    /**
     * Test implementation of UploadListener
     */
    static class TestUploadListener implements UploadQueue.UploadListener {
        @Override
        public void onChunkUploadSuccess(ChunkData chunk) {
            System.out.println("  ✓ Chunk " + chunk.getChunkIndex() + " upload success (simulated)");
        }
        
        @Override
        public void onChunkUploadFailed(ChunkData chunk, Exception e) {
            System.out.println("  ✗ Chunk " + chunk.getChunkIndex() + " upload failed: " + e.getMessage());
        }
        
        @Override
        public void onAllChunksUploaded() {
            System.out.println("  ✓ All chunks uploaded!");
        }
    }
    
    /**
     * Test implementation of ControllerListener
     */
    static class TestControllerListener implements AudioStreamController.ControllerListener {
        @Override
        public void onRecordingStarted(String filePath) {
            System.out.println("  → Recording started: " + filePath);
        }
        
        @Override
        public void onRecordingStopped() {
            System.out.println("  → Recording stopped");
        }
        
        @Override
        public void onRecordingError(Exception e) {
            System.out.println("  ✗ Recording error: " + e.getMessage());
        }
        
        @Override
        public void onChunkCreated(ChunkData chunk) {
            System.out.println("  → Chunk " + chunk.getChunkIndex() + " created");
        }
        
        @Override
        public void onUploadProgress(int uploadedChunks, int totalChunks) {
            System.out.println("  → Upload progress: " + uploadedChunks + "/" + totalChunks);
        }
        
        @Override
        public void onAllUploadsComplete() {
            System.out.println("  ✓ All uploads complete!");
        }
    }
}
