package com.wally.sdkdemo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Kotlin implementation that records audio from the microphone using MediaRecorder
 * and outputs AAC format directly (no conversion needed).
 *
 * Usage:
 *  - Ensure RECORD_AUDIO permission is granted by the hosting Activity before calling startRecording()
 *  - Call startRecording(outputFile) to start recording directly to AAC file
 *  - Call stopRecording() to stop recording
 */
class WMARecorder(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = AtomicBoolean(false)
    private var outputFile: File? = null

    // Default settings for AAC recording
    companion object {
        const val DEFAULT_SAMPLE_RATE = 44100
        const val DEFAULT_BITRATE = 128000 // 128 kbps
    }

    data class RecordConfig(
        val sampleRate: Int = DEFAULT_SAMPLE_RATE,
        val bitRate: Int = DEFAULT_BITRATE,
        val channels: Int = 2 // 1 for mono, 2 for stereo
    )

    // Check whether RECORD_AUDIO permission is granted
    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Start recording AAC audio directly to the specified file.
     * Returns true if recording started successfully, false otherwise.
     */
    @SuppressLint("MissingPermission")
    fun startRecording(
        file: File,
        config: RecordConfig = RecordConfig()
    ): Boolean {
        if (!hasRecordPermission()) return false
        if (isRecording.get()) return false

        try {
            outputFile = file

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS) //不要用 MPEG-4,否则没法拼接
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(config.sampleRate)
                setAudioEncodingBitRate(config.bitRate)
                if (config.channels == 1) {
                    setAudioChannels(1) // Mono
                } else {
                    setAudioChannels(2) // Stereo
                }
                setOutputFile(file.absolutePath)

                prepare()
                start()
                isRecording.set(true)
            }

            return true

        } catch (e: IOException) {
            e.printStackTrace()
            cleanup()
            return false
        } catch (e: Exception) {
            e.printStackTrace()
            cleanup()
            return false
        }
    }

    /**
     * Stop recording and finalize the AAC file.
     * Returns true if stopped successfully, false if there was an error.
     */
    fun stopRecording(): Boolean {
        if (!isRecording.get()) return false

        return try {
            isRecording.set(false)
            mediaRecorder?.stop()
            cleanup()
            true
        } catch (e: RuntimeException) {
            e.printStackTrace()
            cleanup()
            false
        }
    }

    /**
     * Get the current recording state.
     */
    fun isRecording(): Boolean = isRecording.get()

    /**
     * Get the size of the current output file during or after recording.
     * Returns 0 if no file or recording hasn't started.
     */
    fun getOutputFileSize(): Long {
        return outputFile?.length() ?: 0L
    }

    /**
     * Get the current output file.
     */
    fun getOutputFile(): File? = outputFile

    private fun cleanup() {
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
        mediaRecorder = null
    }
}
