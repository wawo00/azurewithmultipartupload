package com.wally.audio;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;

public class WMARecorder {

    private AudioFileFormat.Type fileType;
    private TargetDataLine line;

    public WMARecorder() {
        fileType = AudioFileFormat.Type.WAVE; // WAV as placeholder, change to WMA
    }

    // Start recording
    public void startRecording(File file) {
        AudioFormat format = getAudioFormat();
        try {
            line = AudioSystem.getTargetDataLine(format);
            line.open(format);
            line.start();

            AudioInputStream audioInputStream = new AudioInputStream(line);
            // Monitor file size
            new Thread(() -> {
                while (line.isOpen()) {
                    System.out.println("Current file size: " + file.length() + " bytes");
                    try {
                        Thread.sleep(1000); // Check every second
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
            
            AudioSystem.write(audioInputStream, fileType, file);

        } catch (LineUnavailableException | IOException ex) {
            ex.printStackTrace();
        }
    }

    // Stop recording
    public void stopRecording() {
        if (line != null) {
            line.stop();
            line.close();
        }
    }

    private AudioFormat getAudioFormat() {
        float sampleRate = 44100; // Sample rate
        int sampleSizeInBits = 16; // Sample size
        int channels = 2; // Number of channels
        boolean signed = true; // Signed
        boolean bigEndian = false; // Big endian
        return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
    }
}
