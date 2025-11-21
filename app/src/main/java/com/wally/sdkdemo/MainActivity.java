package com.wally.sdkdemo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


/**
 * Example MainActivity demonstrating the usage of AudioStreamController
 * for recording audio and uploading chunks in real-time
 */
public class MainActivity extends AppCompatActivity implements AudioStreamController.ControllerListener {
    
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final String UPLOAD_URL = "https://your-server.com/upload"; // Replace with your actual upload URL
    
    private AudioStreamController audioController;
    private Button btnStartStop;
    private TextView tvStatus;
    private TextView tvProgress;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Initialize UI components
        btnStartStop = findViewById(R.id.btn_start_stop);
        tvStatus = findViewById(R.id.tv_status);
        tvProgress = findViewById(R.id.tv_progress);
        
        // Initialize AudioStreamController
        String cacheDir = getCacheDir().getAbsolutePath();
        audioController = new AudioStreamController(this,cacheDir, UPLOAD_URL, this);
        
        // Set up button click listener
        btnStartStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (audioController.isRecording()) {
                    stopRecording();
                } else {
                    if (checkPermissions()) {
                        startRecording();
                    } else {
                        requestPermissions();
                    }
                }
            }
        });
        
        updateUI();
    }
    
    /**
     * Start audio recording
     */
    private void startRecording() {
        try {
            audioController.startRecording();
            btnStartStop.setText("Stop Recording");
            tvStatus.setText("Recording...");
        } catch (Exception e) {
            Toast.makeText(this, "Failed to start recording: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Stop audio recording
     */
    private void stopRecording() {
        try {
            audioController.stopRecording();
            btnStartStop.setText("Start Recording");
            tvStatus.setText("Stopped");
        } catch (Exception e) {
            Toast.makeText(this, "Failed to stop recording: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Check if required permissions are granted
     */
    private boolean checkPermissions() {
        int recordPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        int internetPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET);

        return recordPermission == PackageManager.PERMISSION_GRANTED &&
               internetPermission == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * Request required permissions
     */
    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
            new String[]{
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.INTERNET
            },
            PERMISSION_REQUEST_CODE);
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (allGranted) {
                Toast.makeText(this, "Permissions granted. You can start recording now.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permissions denied. Cannot record audio.", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    /**
     * Update UI state
     */
    private void updateUI() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (audioController.isRecording()) {
                    btnStartStop.setText("Stop Recording");
                    tvStatus.setText("Recording: " + audioController.getCurrentFilePath());
                } else {
                    btnStartStop.setText("Start Recording");
                    tvStatus.setText("Ready");
                }
                
                int uploaded = audioController.getUploadedChunkCount();
                int pending = audioController.getPendingChunkCount();
                tvProgress.setText("Uploaded: " + uploaded + " | Pending: " + pending);
            }
        });
    }
    
    // AudioStreamController.ControllerListener implementations
    
    @Override
    public void onRecordingStarted(String filePath) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "Recording started: " + filePath, Toast.LENGTH_SHORT).show();
                updateUI();
            }
        });
    }
    
    @Override
    public void onRecordingStopped() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "Recording stopped", Toast.LENGTH_SHORT).show();
                updateUI();
            }
        });
    }
    
    @Override
    public void onRecordingError(Exception e) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                updateUI();
            }
        });
    }
    
    @Override
    public void onChunkCreated(ChunkData chunk) {
        // Optional: Update UI when chunk is created
        updateUI();
    }
    
    @Override
    public void onUploadProgress(int uploadedChunks, int totalChunks) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateUI();
            }
        });
    }
    
    @Override
    public void onAllUploadsComplete() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "All uploads completed!", Toast.LENGTH_SHORT).show();
                updateUI();
            }
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (audioController != null) {
            audioController.release();
        }
    }
}
