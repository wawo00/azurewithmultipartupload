package com.wally.audio;

public class ChunkData {
    private byte[] data;
    private long offset;
    private int chunkIndex;
    private String fileName;
    private boolean isLastChunk;
    private long timestamp;
    
    public ChunkData(byte[] data, long offset, int chunkIndex, String fileName) {
        this.data = data;
        this.offset = offset;
        this.chunkIndex = chunkIndex;
        this.fileName = fileName;
        this.isLastChunk = false;
        this.timestamp = System.currentTimeMillis();
    }
    
    // Getters and Setters
    public byte[] getData() { 
        return data; 
    }
    
    public void setData(byte[] data) {
        this.data = data;
    }
    
    public long getOffset() { 
        return offset; 
    }
    
    public void setOffset(long offset) {
        this.offset = offset;
    }
    
    public int getChunkIndex() { 
        return chunkIndex; 
    }
    
    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }
    
    public String getFileName() { 
        return fileName; 
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    public boolean isLastChunk() { 
        return isLastChunk; 
    }
    
    public void setLastChunk(boolean lastChunk) { 
        this.isLastChunk = lastChunk; 
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public int getSize() {
        return data != null ? data.length : 0;
    }
    
    @Override
    public String toString() {
        return "ChunkData{" +
                "offset=" + offset +
                ", chunkIndex=" + chunkIndex +
                ", fileName='" + fileName + '\'' +
                ", isLastChunk=" + isLastChunk +
                ", size=" + getSize() +
                '}';
    }
}