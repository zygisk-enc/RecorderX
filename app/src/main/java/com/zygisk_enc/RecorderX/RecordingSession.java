package com.zygisk_enc.RecorderX;

import android.content.ContentValues;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.media.projection.MediaProjection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class RecordingSession {
    private static final String TAG = "RecorderX_Session";
    
    private final Context context;
    private final MediaProjection mediaProjection;
    private final SettingsManager settings;
    
    private MediaCodec videoEncoder;
    private MediaCodec audioEncoder;
    private MediaMuxer muxer;
    private AudioRecord audioRecord;
    private VirtualDisplay virtualDisplay;
    private Surface inputSurface;
    
    private int videoTrackIndex = -1;
    private int audioTrackIndex = -1;
    private boolean muxerStarted = false;
    private final AtomicBoolean isRecording = new AtomicBoolean(false);
    
    private Thread audioThread;
    private Thread videoThread;

    private String outputFilePath;
    private Uri outputUri;

    public RecordingSession(Context context, MediaProjection mediaProjection, SettingsManager settings) {
        this.context = context;
        this.mediaProjection = mediaProjection;
        this.settings = settings;
    }

    public void start() throws IOException {
        Log.i(TAG, "start() called. Initializing session components...");
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "RecorderX_" + timestamp + ".mp4";

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Video.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/RecorderX");

                outputUri = context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
                if (outputUri == null) throw new IOException("Failed to create MediaStore entry");

                ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(outputUri, "rw");
                if (pfd == null) throw new IOException("Failed to open FileDescriptor");
                muxer = new MediaMuxer(pfd.getFileDescriptor(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            } else {
                File storageDir = new File(Environment.getExternalStorageDirectory(), "RecorderX");
                if (!storageDir.exists() && !storageDir.mkdirs()) {
                    storageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "RecorderX");
                    storageDir.mkdirs();
                }
                File file = new File(storageDir, fileName);
                outputFilePath = file.getAbsolutePath();
                muxer = new MediaMuxer(outputFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            }

            setupVideoEncoder();
            
            if (settings.getAudioSource() != 0) {
                try {
                    setupAudioEncoder();
                } catch (Exception e) {
                    Log.e(TAG, "Audio setup failed, falling back to video-only", e);
                    audioEncoder = null;
                    audioRecord = null;
                }
            }
            
            isRecording.set(true);
            Log.d(TAG, "Starting encoders...");
            videoEncoder.start();
            if (audioEncoder != null) audioEncoder.start();
            
            DisplayMetrics metrics = new DisplayMetrics();
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) wm.getDefaultDisplay().getRealMetrics(metrics);
            int density = metrics.densityDpi > 0 ? metrics.densityDpi : 300;

            Log.d(TAG, "Registering MediaProjection callback (required for Android 14+)...");
            mediaProjection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    Log.i(TAG, "MediaProjection onStop() triggered by system");
                    stop();
                }
            }, new Handler(Looper.getMainLooper()));

            Log.d(TAG, "Creating VirtualDisplay (" + settings.getResolutionWidth() + "x" + settings.getResolutionHeight() + ")...");
            virtualDisplay = mediaProjection.createVirtualDisplay("RecorderX",
                    settings.getResolutionWidth(), settings.getResolutionHeight(), density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    inputSurface, null, null);
            
            if (virtualDisplay == null) {
                throw new IOException("Failed to create virtual display");
            }

            startEncodingThreads();
            Log.i(TAG, "Recording session started successfully");
        } catch (Exception e) {
            Log.e(TAG, "CRITICAL: Failed to start recording session", e);
            stop();
            throw e;
        }
    }

    private void setupVideoEncoder() throws IOException {
        String mime = settings.getVideoMimeType();
        int width = settings.getResolutionWidth();
        int height = settings.getResolutionHeight();
        
        MediaFormat format = MediaFormat.createVideoFormat(mime, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, settings.getBitrateValue());
        format.setInteger(MediaFormat.KEY_FRAME_RATE, settings.getFpsValue());
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
        
        Log.i(TAG, "Configuring Video Encoder: Codec=" + mime + 
                ", Res=" + width + "x" + height + 
                ", Bitrate=" + (settings.getBitrateValue() / 1000000) + "Mbps" + 
                ", FPS=" + settings.getFpsValue());

        videoEncoder = MediaCodec.createEncoderByType(mime);
        videoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        inputSurface = videoEncoder.createInputSurface();
    }

    private void setupAudioEncoder() throws IOException {
        int sampleRate = 48000;
        int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 4;

        if (settings.getAudioSource() == 2) {
            AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build();
            audioRecord = new AudioRecord.Builder()
                    .setAudioPlaybackCaptureConfig(config)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(audioFormat)
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelConfig)
                            .build())
                    .setBufferSizeInBytes(bufferSize)
                    .build();
        } else {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufferSize);
        }

        MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 2);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        
        audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        audioEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    }

    private void startEncodingThreads() {
        videoThread = new Thread(() -> {
            drainEncoder(videoEncoder, true);
        }, "VideoEncoderThread");
        videoThread.start();
        
        if (audioEncoder != null) {
            audioThread = new Thread(() -> {
                try {
                    if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                        synchronized (muxer) {
                            audioEncoder = null; // Signal video thread to not wait for audio
                        }
                        return;
                    }
                    audioRecord.startRecording();
                    if (audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                        synchronized (muxer) {
                            audioEncoder = null;
                        }
                        return;
                    }
                    drainAudio();
                } catch (Exception e) {
                    Log.e(TAG, "Fatal audio thread error", e);
                    synchronized (muxer) {
                        audioEncoder = null;
                    }
                }
            }, "AudioEncoderThread");
            audioThread.start();
        }
    }

    private long videoStartTimeUs = -1;

    private void drainEncoder(MediaCodec encoder, boolean isVideo) {
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int frameCount = 0;
        try {
            while (isRecording.get()) {
                int outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000);
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    synchronized (muxer) {
                        MediaFormat newFormat = encoder.getOutputFormat();
                        Log.d(TAG, (isVideo ? "Video" : "Audio") + " format changed: " + newFormat);
                        if (isVideo) videoTrackIndex = muxer.addTrack(newFormat);
                        else audioTrackIndex = muxer.addTrack(newFormat);
                        
                        if (videoTrackIndex != -1 && (audioEncoder == null || audioTrackIndex != -1)) {
                            if (!muxerStarted) {
                                Log.i(TAG, "Starting muxer... (Video: " + videoTrackIndex + ", Audio: " + audioTrackIndex + ")");
                                try {
                                    muxer.start();
                                    muxerStarted = true;
                                } catch (Exception e) {
                                    Log.e(TAG, "Muxer start failed", e);
                                }
                            }
                        }
                    }
                } else if (outputIndex >= 0) {
                    ByteBuffer outputBuffer = encoder.getOutputBuffer(outputIndex);
                    
                    if (muxerStarted && bufferInfo.size > 0) {
                        // NORMALIZE PTS: Most hardware encoders use system uptime which causes huge offsets
                        if (isVideo) {
                            if (videoStartTimeUs == -1) videoStartTimeUs = bufferInfo.presentationTimeUs;
                            bufferInfo.presentationTimeUs -= videoStartTimeUs;
                        }

                        if (bufferInfo.presentationTimeUs < 0) bufferInfo.presentationTimeUs = 0;
                        
                        muxer.writeSampleData(videoTrackIndex, outputBuffer, bufferInfo);
                    }
                    
                    if (isVideo) {
                        frameCount++;
                    }
                    encoder.releaseOutputBuffer(outputIndex, false);
                } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (isVideo && !muxerStarted && audioEncoder != null && frameCount > 300) {
                        Log.w(TAG, "Video frames Produced: " + frameCount + " but Audio track not ready. Forcing video-only muxer start.");
                        synchronized (muxer) {
                            if (!muxerStarted) {
                                try {
                                    muxer.start();
                                    muxerStarted = true;
                                } catch (Exception e) {
                                    Log.e(TAG, "Force-start muxer failed", e);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Drain " + (isVideo ? "video" : "audio") + " error", e);
        }
    }

    private void drainAudio() {
        int sampleRate = 48000;
        int bufferSize = 4096; 
        ByteBuffer pcmBuffer = ByteBuffer.allocateDirect(bufferSize);
        
        // Use system time for audio PTS sync if possible, but keep simple for now
        long totalSamples = 0;

        try {
            while (isRecording.get()) {
                pcmBuffer.clear();
                int read = audioRecord.read(pcmBuffer, bufferSize);
                if (read > 0) {
                    int inputIndex = audioEncoder.dequeueInputBuffer(10000);
                    if (inputIndex >= 0) {
                        ByteBuffer inputBuffer = audioEncoder.getInputBuffer(inputIndex);
                        inputBuffer.clear();
                        
                        int bytesToCopy = Math.min(read, inputBuffer.remaining());
                        pcmBuffer.position(0);
                        pcmBuffer.limit(bytesToCopy);
                        inputBuffer.put(pcmBuffer);
                        
                        // Audio PTS starting from 0
                        long pts = (totalSamples * 1000000L) / sampleRate;
                        audioEncoder.queueInputBuffer(inputIndex, 0, bytesToCopy, pts, 0);
                        totalSamples += (bytesToCopy / 4);
                    }
                }
                drainAudioOutput();
            }
        } catch (Exception e) {
            Log.e(TAG, "Audio capture error", e);
        }
    }

    private void drainAudioOutput() {
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputIndex = audioEncoder.dequeueOutputBuffer(bufferInfo, 0);
        while (outputIndex >= 0 || outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                synchronized (muxer) {
                    if (muxerStarted) return;
                    MediaFormat format = audioEncoder.getOutputFormat();
                    try {
                        audioTrackIndex = muxer.addTrack(format);
                        if (videoTrackIndex != -1 && !muxerStarted) {
                            muxer.start();
                            muxerStarted = true;
                        }
                    } catch (Exception ignored) {}
                }
            } else {
                ByteBuffer outputBuffer = audioEncoder.getOutputBuffer(outputIndex);
                if (muxerStarted && bufferInfo.size > 0 && audioTrackIndex != -1) {
                    // Audio PTS is already normalized (starts at 0) from drainAudio()
                    muxer.writeSampleData(audioTrackIndex, outputBuffer, bufferInfo);
                }
                audioEncoder.releaseOutputBuffer(outputIndex, false);
            }
            outputIndex = audioEncoder.dequeueOutputBuffer(bufferInfo, 0);
        }
    }

    public void stop() {
        isRecording.set(false);
        try {
            if (videoThread != null) videoThread.join(1000);
            if (audioThread != null) audioThread.join(1000);
        } catch (InterruptedException ignored) {}

        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        
        try { if (videoEncoder != null) { videoEncoder.stop(); videoEncoder.release(); } } catch (Exception ignored) {}
        try { if (audioEncoder != null) { audioEncoder.stop(); audioEncoder.release(); } } catch (Exception ignored) {}
        try { if (audioRecord != null) { audioRecord.stop(); audioRecord.release(); } } catch (Exception ignored) {}
        
        if (muxer != null) { 
            try { if (muxerStarted) muxer.stop(); } catch (Exception ignored) {}
            try { muxer.release(); } catch (Exception ignored) {}
            muxer = null;
        }

        // Finalize MediaStore or notify Scanner
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (outputUri != null) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Video.Media.IS_PENDING, 0);
                context.getContentResolver().update(outputUri, values, null, null);
            }
        } else if (outputFilePath != null) {
            MediaScannerConnection.scanFile(context, new String[]{outputFilePath}, null, null);
        }
        Log.d(TAG, "Session stopped and saved.");
    }
}
