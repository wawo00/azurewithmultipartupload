import java.io.*;
import java.nio.file.*;

public class FileChunkManager {
    private static final int CHUNK_SIZE = 256 * 1024; // 256 KB
    private File file;
    private long totalSize;
    private long currentSize;

    public FileChunkManager(File file) {
        this.file = file;
        this.totalSize = file.length();
        this.currentSize = 0;
    }

    // Method to handle changes in file size
    public void onFileSizeChanged() {
        long newSize = file.length();
        if (newSize != totalSize) {
            totalSize = newSize;
            // Logic to handle the updated file size
            // This can include notifying listeners, updating state, etc.
        }
    }

    // Method to process any remaining data after chunk processing
    public void processRemainingData() throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long remainingDataSize = totalSize - currentSize;
            if (remainingDataSize > 0) {
                byte[] remainingData = new byte[(int)remainingDataSize];
                raf.seek(currentSize);
                raf.readFully(remainingData);
                // Process the remaining data (e.g., send the data to the stream)
                // Reset currentSize to totalSize after processing
                currentSize = totalSize;
            }
        }
    }

    // Additional methods for chunk processing can be added here
}