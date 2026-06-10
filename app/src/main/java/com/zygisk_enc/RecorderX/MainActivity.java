package com.zygisk_enc.RecorderX;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private static final int SCREEN_CAPTURE_REQUEST_CODE = 1000;
    private static final int PERMISSION_REQUEST_CODE = 1001;
    
    private SettingsManager settingsManager;
    private Button btnRecord;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        settingsManager = new SettingsManager(this);
        initUI();
    }

    private void initUI() {
        btnRecord = findViewById(R.id.btnRecord);
        btnRecord.setOnClickListener(v -> toggleRecording());

        findViewById(R.id.btnViewSource).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/zygisk-enc/RecorderX"));
            startActivity(intent);
        });

        setupSpinner(R.id.codecSpinner, R.array.codec_options, settingsManager.getCodec(), 
            index -> settingsManager.setCodec(index));
        setupSpinner(R.id.resolutionSpinner, R.array.resolution_options, settingsManager.getResolution(), 
            index -> settingsManager.setResolution(index));
        setupSpinner(R.id.bitrateSpinner, R.array.bitrate_options, settingsManager.getBitrate(), 
            index -> settingsManager.setBitrate(index));
        setupSpinner(R.id.fpsSpinner, R.array.fps_options, settingsManager.getFps(), 
            index -> settingsManager.setFps(index));
        setupSpinner(R.id.audioSpinner, R.array.audio_options, settingsManager.getAudioSource(), 
            index -> settingsManager.setAudioSource(index));
    }

    private void setupSpinner(int viewId, int arrayId, int initialSelection, OnSelectionChanged listener) {
        AutoCompleteTextView spinner = findViewById(viewId);
        String[] options = getResources().getStringArray(arrayId);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, options);
        spinner.setAdapter(adapter);
        spinner.setText(options[initialSelection], false);
        spinner.setOnItemClickListener((parent, view, position, id) -> listener.onChanged(position));
    }

    private void toggleRecording() {
        if (RecorderService.isRecording()) {
            stopRecording();
        } else {
            if (checkPermissions()) {
                startProjectionRequest();
            } else {
                requestPermissions();
            }
        }
    }

    private boolean checkPermissions() {
        if (settingsManager.getAudioSource() == 1) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startProjectionRequest();
            } else {
                Toast.makeText(this, "Audio permission required", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startProjectionRequest() {
        MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (projectionManager != null) {
            startActivityForResult(projectionManager.createScreenCaptureIntent(), SCREEN_CAPTURE_REQUEST_CODE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SCREEN_CAPTURE_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                startRecording(resultCode, data);
            } else {
                Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startRecording(int resultCode, Intent data) {
        android.util.Log.d("RecorderX_Main", "startRecording: ResultCode=" + resultCode + ", Data=" + data);
        
        // Use static backup as the Intent extras can be stripped on some devices
        RecorderService.setProjectionData(resultCode, data);

        Intent intent = new Intent(this, RecorderService.class);
        intent.setAction(RecorderService.ACTION_START);
        intent.putExtra(RecorderService.EXTRA_RESULT_CODE, resultCode);
        intent.putExtra(RecorderService.EXTRA_DATA, data);
        
        try {
            ContextCompat.startForegroundService(this, intent);
            btnRecord.setText(R.string.stop_recording);
        } catch (Exception e) {
            android.util.Log.e("RecorderX_Main", "Failed to start service", e);
            Toast.makeText(this, "Failed to start recorder", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        Intent intent = new Intent(this, RecorderService.class);
        intent.setAction(RecorderService.ACTION_STOP);
        startService(intent);
        btnRecord.setText(R.string.start_recording);
    }

    @Override
    protected void onResume() {
        super.onResume();
        btnRecord.setText(RecorderService.isRecording() ? R.string.stop_recording : R.string.start_recording);
        
        // Handle auto-start request from Quick Settings Tile
        if (getIntent() != null && getIntent().getBooleanExtra("AUTO_START", false)) {
            getIntent().removeExtra("AUTO_START"); // Clear so it doesn't trigger again
            if (!RecorderService.isRecording()) {
                toggleRecording();
            }
        }
    }

    interface OnSelectionChanged {
        void onChanged(int index);
    }
}
