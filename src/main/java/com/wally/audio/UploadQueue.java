package com.wally.audio;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
    
    public interface UploadListener {
        void onChunkUploadSuccess(ChunkData chunk);
        void onChunkUploadFailed(ChunkData chunk, Exception e);
        void onAllChunksUploaded();
    }
    
    public UploadQueue(UploadListener listener) {
        this.uploadListener = listener;
    }
    
    public UploadQueue(UploadListener listener, String uploadUrl) {
        this.uploadListener = listener;
        this.uploadUrl = uploadUrl;
    }
    
    public void addChunk(ChunkData chunk) {
        if (isRunning) {
            try {
                chunkQueue.put(chunk);
                System.out.println(TAG + ": Added chunk to queue: " + chunk.toString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println(TAG + ": Interrupted while adding chunk: " + e.getMessage());
            }
        }
    }
    
    public void startUploadWorker() {
        System.out.println(TAG + ": Starting upload workers");
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
            System.out.println(TAG + ": " + workerName + " started");
            while (isRunning || !chunkQueue.isEmpty()) {
                try {
                    ChunkData chunk = chunkQueue.poll(1, TimeUnit.SECONDS);
                    if (chunk != null) {
                        System.out.println(TAG + ": " + workerName + " processing chunk: " + chunk.getChunkIndex());
                        uploadChunkWithRetry(chunk);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println(TAG + ": " + workerName + " interrupted");
                    break;
                }
            }
            System.out.println(TAG + ": " + workerName + " stopped");
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
                
                System.out.println(TAG + ": Chunk uploaded successfully: " + chunk.getChunkIndex() + 
                          " (Total uploaded: " + uploadedChunks + ")");
                
                if (uploadListener != null) {
                    uploadListener.onChunkUploadSuccess(chunk);
                }
                
                // 如果是最后一个切片，通知完成
                if (chunk.isLastChunk() && chunkQueue.isEmpty()) {
                    System.out.println(TAG + ": All chunks uploaded");
                    if (uploadListener != null) {
                        uploadListener.onAllChunksUploaded();
                    }
                }
                return;
                
            } catch (Exception e) {
                retryCount++;
                System.out.println(TAG + ": Chunk upload failed (attempt " + retryCount + "/" + maxRetries + "): " + e.getMessage());
                
                if (retryCount >= maxRetries) {
                    System.err.println(TAG + ": Chunk upload failed after " + maxRetries + " attempts: " + e.getMessage());
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
        // 使用Java 11 HttpClient上传切片
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        
        // 创建multipart/form-data内容
        String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
        String multipartBody = createMultipartBody(chunk, boundary);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(multipartBody, StandardCharsets.UTF_8))
                .build();
        
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new IOException("Upload failed: " + response.statusCode() + " " + response.body());
        }
        
        System.out.println(TAG + ": Upload response for chunk " + chunk.getChunkIndex() + ": " + response.body());
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
        System.out.println(TAG + ": Stopping upload queue");
        isRunning = false;
        uploadExecutor.shutdown();
        try {
            if (!uploadExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                System.out.println(TAG + ": Upload executor did not terminate gracefully, forcing shutdown");
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