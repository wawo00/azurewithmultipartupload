package com.wally.sdkdemo;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public class FileChunkManager {
    private static final String TAG = "FileChunkManager";
    private static final int CHUNK_SIZE = 256 * 1024; // 256KB
    
    private String filePath;
    private long lastUploadedPosition = 0;
    private int chunkIndex = 0;
    private UploadQueue uploadQueue;
    private final Object lockObject = new Object();
    private volatile boolean isProcessing = false;
    
    public FileChunkManager(String filePath, UploadQueue uploadQueue) {
        this.filePath = filePath;
        this.uploadQueue = uploadQueue;
    }
    
    /**
     * Handle file size changes and create chunks when new data is available
     */
    public void onFileSizeChanged(long newSize) {
        synchronized (lockObject) {
            File file = new File(filePath);
            if (!file.exists()) {
                System.err.println(TAG + ": File does not exist: " + filePath);
                return;
            }
            
            long fileSize = file.length();
            long availableData = fileSize - lastUploadedPosition;
            
            // Only process if we have at least one chunk worth of data
            if (availableData >= CHUNK_SIZE) {
                System.out.println(TAG + ": New data available: " + availableData + " bytes");
                processChunks(fileSize, false);
            }
        }
    }
    
    /**
     * Process all remaining data when recording is complete
     */
    public void processRemainingData() {
        synchronized (lockObject) {
            File file = new File(filePath);
            if (!file.exists()) {
                System.err.println(TAG + ": File does not exist: " + filePath);
                return;
            }
            
            long fileSize = file.length();
            System.out.println(TAG + ": Processing remaining data. File size: " + fileSize + 
                             ", Last uploaded position: " + lastUploadedPosition);
            processChunks(fileSize, true);
        }
    }
    
    /**
     * Process chunks from the current position to the file size
     */
    private void processChunks(long fileSize, boolean includeFinal) {
        if (isProcessing) {
            System.out.println(TAG + ": Already processing chunks, skipping");
            return;
        }
        
        try {
            isProcessing = true;
            File file = new File(filePath);
            
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                // Process full chunks
                while (lastUploadedPosition + CHUNK_SIZE <= fileSize) {
                    byte[] chunkData = new byte[CHUNK_SIZE];
                    raf.seek(lastUploadedPosition);
                    int bytesRead = raf.read(chunkData);
                    
                    if (bytesRead > 0) {
                        ChunkData chunk = new ChunkData(
                            chunkData,
                            lastUploadedPosition,
                            chunkIndex++,
                            file.getName(),
                            false,  // isLastChunk
                            System.currentTimeMillis()  // timestamp
                        );
                        
                        uploadQueue.addChunk(chunk);
                        lastUploadedPosition += bytesRead;
                        
                        System.out.println(TAG + ": Created chunk " + chunk.getChunkIndex() + 
                                         " at offset " + chunk.getOffset() + 
                                         " with size " + bytesRead + " bytes");
                    } else {
                        break;
                    }
                }
                
                // Process final partial chunk if requested
                if (includeFinal) {
                    long remainingBytes = fileSize - lastUploadedPosition;
                    if (remainingBytes > 0) {
                        byte[] finalChunkData = new byte[(int) remainingBytes];
                        raf.seek(lastUploadedPosition);
                        int bytesRead = raf.read(finalChunkData);
                        
                        if (bytesRead > 0) {
                            ChunkData finalChunk = new ChunkData(
                                finalChunkData,
                                lastUploadedPosition,
                                chunkIndex++,
                                file.getName(),
                                true,  // isLastChunk = true
                                System.currentTimeMillis()  // timestamp
                            );

                            uploadQueue.addChunk(finalChunk);
                            lastUploadedPosition += bytesRead;
                            
                            System.out.println(TAG + ": Created FINAL chunk " + finalChunk.getChunkIndex() + 
                                             " at offset " + finalChunk.getOffset() + 
                                             " with size " + bytesRead + " bytes");
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println(TAG + ": Error processing chunks: " + e.getMessage());
            e.printStackTrace();
        } finally {
            isProcessing = false;
        }
    }
    
    /**
     * Reset the manager for a new file
     */
    public void reset(String newFilePath) {
        synchronized (lockObject) {
            this.filePath = newFilePath;
            this.lastUploadedPosition = 0;
            this.chunkIndex = 0;
            System.out.println(TAG + ": Reset for new file: " + newFilePath);
        }
    }
    
    /**
     * Get the current chunk index
     */
    public int getChunkIndex() {
        synchronized (lockObject) {
            return chunkIndex;
        }
    }
    
    /**
     * Get the last uploaded position
     */
    public long getLastUploadedPosition() {
        synchronized (lockObject) {
            return lastUploadedPosition;
        }
    }
}