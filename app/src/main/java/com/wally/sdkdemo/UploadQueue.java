package com.wally.sdkdemo;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class UploadQueue {
    private static final String TAG = "UploadQueue";
    
    private BlockingQueue<ChunkData> chunkQueue = new LinkedBlockingQueue<>();
    private ExecutorService uploadExecutor = Executors.newFixedThreadPool(2);
    private volatile boolean isRunning = true;
    private UploadListener uploadListener;
    private int uploadedChunks = 0;
    private String uploadUrl = "YOUR_UPLOAD_SERVER_URL"; // 请替换为实际的上传URL
    private Context context; // 添加Context成员变量

    public interface UploadListener {
        void onChunkUploadSuccess(ChunkData chunk);
        void onChunkUploadFailed(ChunkData chunk, Exception e);
        void onAllChunksUploaded();
    }
    
    public UploadQueue(Context context, UploadListener listener) {
        this.context = context;
        this.uploadListener = listener;
    }
    
    public UploadQueue(Context context, UploadListener listener, String uploadUrl) {
        this.context = context;
        this.uploadListener = listener;
        this.uploadUrl = uploadUrl;
    }
    
    public void addChunk(ChunkData chunk) {
        if (isRunning) {
            try {
                chunkQueue.put(chunk);
                Log.d(TAG, "Added chunk to queue: " + chunk.toString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.e(TAG, "Interrupted while adding chunk: " + e.getMessage());
            }
        }
    }
    
    public void startUploadWorker() {
        Log.d(TAG, "Starting upload workers");
        for (int i = 0; i < 2; i++) { // 启动2个上传工作线程
            uploadExecutor.submit(new UploadWorker("Worker-" + i));
        }
    }
    
    private class UploadWorker implements Runnable {
        private String workerName;
        
        public UploadWorker(String name) {
            this.workerName = name;
        }
        
        @Override
        public void run() {
            Log.d(TAG, workerName + " started");
            while (isRunning || !chunkQueue.isEmpty()) {
                try {
                    ChunkData chunk = chunkQueue.poll(1, TimeUnit.SECONDS);
                    if (chunk != null) {
                        Log.d(TAG, workerName + " processing chunk: " + chunk.getChunkIndex());
                        uploadChunkWithRetry(chunk);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.i(TAG, workerName + " interrupted");
                    break;
                }
            }
            Log.d(TAG, workerName + " stopped");
        }
    }
    
    private void uploadChunkWithRetry(ChunkData chunk) {
        int maxRetries = 3;
        int retryCount = 0;
        
        while (retryCount < maxRetries) {
            try {
                uploadChunk(chunk);
                
                synchronized (this) {
                    uploadedChunks++;
                }
                
                Log.i(TAG, "Chunk uploaded successfully: " + chunk.getChunkIndex() +
                          " (Total uploaded: " + uploadedChunks + ")");
                
                if (uploadListener != null) {
                    uploadListener.onChunkUploadSuccess(chunk);
                }
                
                // 如果是最后一个切片，通知完成
                if (chunk.isLastChunk() && chunkQueue.isEmpty()) {
                    Log.i(TAG, "All chunks uploaded");
                    if (uploadListener != null) {
                        uploadListener.onAllChunksUploaded();
                    }
                }
                return;
                
            } catch (Exception e) {
                retryCount++;
                Log.w(TAG, "Chunk upload failed (attempt " + retryCount + "/" + maxRetries + "): " + e.getMessage());

                if (retryCount >= maxRetries) {
                    Log.e(TAG, "Chunk upload failed after " + maxRetries + " attempts: " + e.getMessage());
                    if (uploadListener != null) {
                        uploadListener.onChunkUploadFailed(chunk, e);
                    }
                } else {
                    // 重试前等待
                    try {
                        Thread.sleep(1000 * retryCount);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }
    
    private void uploadChunk(ChunkData chunk) throws Exception {
        // 为了测试简便，只使用模拟上传
        fakeUpload(chunk);
    }

    // 模拟上传操作 - 将接收到的数据块合并写入本地文件用于验证
    private void fakeUpload(ChunkData chunk) throws Exception {
        // 模拟网络请求延迟 (100-800ms随机延迟)
        int delay = 100 + (int)(Math.random() * 700);
        Thread.sleep(delay);

        // 模拟偶发的网络错误 (8%概率)
        if (Math.random() < 0.08) {
            throw new Exception("Simulated network error for chunk " + chunk.getChunkIndex());
        }

        // 将数据块写入本地合并文件
        writeChunkToMergedFile(chunk);

        // 模拟成功上传
        Log.d(TAG, "Simulated upload completed for chunk " + chunk.getChunkIndex() +
                          " (size: " + chunk.getSize() + " bytes, delay: " + delay + "ms)");
    }

    /**
     * 将数据块写入本地合并文件用于验证
     * 文件保存在应用内部存储的 merged_files 目录下
     * 使用同步机制确保多线程安全
     */
    private synchronized void writeChunkToMergedFile(ChunkData chunk) {
        RandomAccessFile randomAccessFile = null;
        try {
            // 通过Context获取应用内部存储目录，更简洁的方式
            File mergedDir = new File(context.getFilesDir(), "merged_files");
            if (!mergedDir.exists()) {
                mergedDir.mkdirs();
            }

            // 创建合并文件，以原文件名命名
            File mergedFile = new File(mergedDir, "merged_" + chunk.getFileName());

            // 使用 RandomAccessFile 在指定偏移量写入数据
            randomAccessFile = new RandomAccessFile(mergedFile, "rw");

            // 定位到数据块的偏移位置
            randomAccessFile.seek(chunk.getOffset());

            // 写入数据块内容
            if (chunk.getData() != null && chunk.getData().length > 0) {
                randomAccessFile.write(chunk.getData());
                // 强制将数据写入磁盘
                randomAccessFile.getFD().sync();
            }

            Log.d(TAG, "Chunk " + chunk.getChunkIndex() + " written to merged file: " + mergedFile.getAbsolutePath() +
                      " at offset: " + chunk.getOffset() + ", size: " + chunk.getSize());

            // 如果是最后一个数据块，记录合并完成信息并验证文件
            if (chunk.isLastChunk()) {
                long finalSize = mergedFile.length();
                Log.i(TAG, "File merge completed! Final merged file: " + mergedFile.getAbsolutePath() +
                          ", size: " + finalSize + " bytes");

                // 验证合并文件的完整性
                validateMergedFile(mergedFile);
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to write chunk to merged file: " + e.getMessage(), e);
            // 重新抛出异常，让重试机制处理
            throw new RuntimeException("Failed to merge chunk", e);
        } finally {
            // 确保文件正确关闭
            if (randomAccessFile != null) {
                try {
                    randomAccessFile.close();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing RandomAccessFile: " + e.getMessage());
                }
            }
        }
    }

    /**
     * 验证合并文件的完整性
     */
    private void validateMergedFile(File mergedFile) {
        try {
            if (mergedFile.exists() && mergedFile.length() > 0) {
                Log.i(TAG, "Merged file validation - Name: " + mergedFile.getName() +
                          ", Size: " + mergedFile.length() + " bytes" +
                          ", Path: " + mergedFile.getAbsolutePath());

                // 检查文件是否可读
                if (mergedFile.canRead()) {
                    Log.i(TAG, "Merged file is readable and ready for playback");
                } else {
                    Log.w(TAG, "Merged file exists but is not readable");
                }
            } else {
                Log.e(TAG, "Merged file is empty or does not exist");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error validating merged file: " + e.getMessage());
        }
    }

    private String createMultipartBody(ChunkData chunk, String boundary) {
        StringBuilder builder = new StringBuilder();
        
        // 文件数据部分
        builder.append("--").append(boundary).append("\r\n");
        builder.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(chunk.getFileName()).append("\"\r\n");
        builder.append("Content-Type: application/octet-stream\r\n\r\n");
        builder.append(new String(chunk.getData(), StandardCharsets.ISO_8859_1)).append("\r\n");
        
        // 其他参数
        addFormField(builder, boundary, "chunkIndex", String.valueOf(chunk.getChunkIndex()));
        addFormField(builder, boundary, "offset", String.valueOf(chunk.getOffset()));
        addFormField(builder, boundary, "fileName", chunk.getFileName());
        addFormField(builder, boundary, "isLastChunk", String.valueOf(chunk.isLastChunk()));
        addFormField(builder, boundary, "chunkSize", String.valueOf(chunk.getSize()));
        addFormField(builder, boundary, "timestamp", String.valueOf(chunk.getTimestamp()));
        
        builder.append("--").append(boundary).append("--\r\n");
        
        return builder.toString();
    }
    
    private void addFormField(StringBuilder builder, String boundary, String name, String value) {
        builder.append("--").append(boundary).append("\r\n");
        builder.append("Content-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n");
        builder.append(value).append("\r\n");
    }
    
    public void stop() {
        Log.d(TAG, "Stopping upload queue");
        isRunning = false;
        uploadExecutor.shutdown();
        try {
            if (!uploadExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                Log.w(TAG, "Upload executor did not terminate gracefully, forcing shutdown");
                uploadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            uploadExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    public int getPendingChunkCount() {
        return chunkQueue.size();
    }
    
    public int getUploadedChunkCount() {
        return uploadedChunks;
    }
    
    public void setUploadUrl(String url) {
        this.uploadUrl = url;
    }
}
