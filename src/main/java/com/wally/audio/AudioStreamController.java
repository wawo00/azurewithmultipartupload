package com.wally.audio;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Main controller for audio stream recording and multipart upload
 * Coordinates WMARecorder, FileChunkManager, and UploadQueue
 */
public class AudioStreamController implements UploadQueue.UploadListener {
    
    private static final String TAG = "AudioStreamController";
    private static final long FILE_SIZE_CHECK_INTERVAL = 1000; // Check file size every second
    
    private WMARecorder recorder;
    private FileChunkManager chunkManager;
    private UploadQueue uploadQueue;
    private String currentFilePath;
    private ControllerListener controllerListener;
    private Timer fileSizeMonitor;
    private volatile boolean isRecording = false;
    private String cacheDir;
    private String uploadUrl;
    
    /**
     * Listener interface for controller events
     */
    public interface ControllerListener {
        void onRecordingStarted(String filePath);
        void onRecordingStopped();
        void onRecordingError(Exception e);
        void onChunkCreated(ChunkData chunk);
        void onUploadProgress(int uploadedChunks, int totalChunks);
        void onAllUploadsComplete();
    }
    
    /**
     * Constructor with cache directory and upload URL
     */
    public AudioStreamController(String cacheDir, String uploadUrl, ControllerListener listener) {
        this.cacheDir = cacheDir;
        this.uploadUrl = uploadUrl;
        this.controllerListener = listener;
        this.recorder = new WMARecorder();
        this.uploadQueue = new UploadQueue(this, uploadUrl);
    }
    
    /**
     * Start audio recording and streaming upload
     */
    public void startRecording() {
        if (isRecording) {
            System.out.println(TAG + ": Recording already in progress");
            return;
        }
        
        try {
            // Generate timestamped file path
            currentFilePath = generateFilePath();
            
            // Ensure cache directory exists
            File cacheDirectory = new File(cacheDir);
            if (!cacheDirectory.exists()) {
                cacheDirectory.mkdirs();
            }
            
            File audioFile = new File(currentFilePath);
            
            // Initialize chunk manager and upload queue
            chunkManager = new FileChunkManager(currentFilePath, uploadQueue);
            uploadQueue.startUploadWorker();
            
            // Start file size monitoring
            startFileSizeMonitoring();
            
            // Start recording in a separate thread
            new Thread(() -> {
                try {
                    System.out.println(TAG + ": Starting recording to file: " + currentFilePath);
                    recorder.startRecording(audioFile);
                } catch (Exception e) {
                    System.err.println(TAG + ": Recording error: " + e.getMessage());
                    e.printStackTrace();
                    if (controllerListener != null) {
                        controllerListener.onRecordingError(e);
                    }
                }
            }, "RecordingThread").start();
            
            isRecording = true;
            
            if (controllerListener != null) {
                controllerListener.onRecordingStarted(currentFilePath);
            }
            
            System.out.println(TAG + ": Recording started successfully");
            
        } catch (Exception e) {
            System.err.println(TAG + ": Failed to start recording: " + e.getMessage());
            e.printStackTrace();
            if (controllerListener != null) {
                controllerListener.onRecordingError(e);
            }
        }
    }
    
    /**
     * Stop audio recording and upload remaining chunks
     */
    public void stopRecording() {
        if (!isRecording) {
            System.out.println(TAG + ": No recording in progress");
            return;
        }
        
        try {
            System.out.println(TAG + ": Stopping recording...");
            
            // Stop the recorder
            recorder.stopRecording();
            
            // Stop file size monitoring
            stopFileSizeMonitoring();
            
            // Process remaining data
            if (chunkManager != null) {
                System.out.println(TAG + ": Processing remaining chunks...");
                chunkManager.processRemainingData();
            }
            
            isRecording = false;
            
            if (controllerListener != null) {
                controllerListener.onRecordingStopped();
            }
            
            System.out.println(TAG + ": Recording stopped successfully");
            
        } catch (Exception e) {
            System.err.println(TAG + ": Error stopping recording: " + e.getMessage());
            e.printStackTrace();
            if (controllerListener != null) {
                controllerListener.onRecordingError(e);
            }
        }
    }
    
    /**
     * Start monitoring file size changes
     */
    private void startFileSizeMonitoring() {
        if (fileSizeMonitor != null) {
            fileSizeMonitor.cancel();
        }
        
        fileSizeMonitor = new Timer("FileSizeMonitor", true);
        fileSizeMonitor.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (currentFilePath != null && isRecording) {
                    File file = new File(currentFilePath);
                    if (file.exists()) {
                        long fileSize = file.length();
                        chunkManager.onFileSizeChanged(fileSize);
                    }
                }
            }
        }, FILE_SIZE_CHECK_INTERVAL, FILE_SIZE_CHECK_INTERVAL);
        
        System.out.println(TAG + ": File size monitoring started");
    }
    
    /**
     * Stop monitoring file size changes
     */
    private void stopFileSizeMonitoring() {
        if (fileSizeMonitor != null) {
            fileSizeMonitor.cancel();
            fileSizeMonitor = null;
            System.out.println(TAG + ": File size monitoring stopped");
        }
    }
    
    /**
     * Generate timestamped file path
     */
    private String generateFilePath() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        String timestamp = sdf.format(new Date());
        return cacheDir + "/audio_" + timestamp + ".aac";
    }
    
    /**
     * Clean up resources
     */
    public void release() {
        System.out.println(TAG + ": Releasing resources...");
        
        if (isRecording) {
            stopRecording();
        }
        
        stopFileSizeMonitoring();
        
        if (uploadQueue != null) {
            uploadQueue.stop();
        }
        
        System.out.println(TAG + ": Resources released");
    }
    
    /**
     * Get current recording status
     */
    public boolean isRecording() {
        return isRecording;
    }
    
    /**
     * Get current file path
     */
    public String getCurrentFilePath() {
        return currentFilePath;
    }
    
    /**
     * Get upload queue statistics
     */
    public int getPendingChunkCount() {
        return uploadQueue != null ? uploadQueue.getPendingChunkCount() : 0;
    }
    
    public int getUploadedChunkCount() {
        return uploadQueue != null ? uploadQueue.getUploadedChunkCount() : 0;
    }
    
    // UploadQueue.UploadListener implementation
    
    @Override
    public void onChunkUploadSuccess(ChunkData chunk) {
        System.out.println(TAG + ": Chunk uploaded successfully: " + chunk.toString());
        
        if (controllerListener != null) {
            int totalChunks = chunkManager != null ? chunkManager.getChunkIndex() : 0;
            controllerListener.onUploadProgress(getUploadedChunkCount(), totalChunks);
        }
    }
    
    @Override
    public void onChunkUploadFailed(ChunkData chunk, Exception e) {
        System.err.println(TAG + ": Chunk upload failed: " + chunk.toString());
        System.err.println(TAG + ": Error: " + e.getMessage());
        
        if (controllerListener != null) {
            controllerListener.onRecordingError(e);
        }
    }
    
    @Override
    public void onAllChunksUploaded() {
        System.out.println(TAG + ": All chunks uploaded successfully");
        
        if (controllerListener != null) {
            controllerListener.onAllUploadsComplete();
        }
    }
}
