package com.zygisk_enc.RecorderX;

import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class RecorderTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile != null) {
            boolean isRecording = RecorderService.isRecording();
            tile.setState(isRecording ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            tile.setLabel(isRecording ? "Stop Recording" : "Record Screen");
            tile.updateTile();
        }
    }

    @Override
    public void onClick() {
        super.onClick();
        if (RecorderService.isRecording()) {
            Intent intent = new Intent(this, RecorderService.class);
            intent.setAction(RecorderService.ACTION_STOP);
            startService(intent);
        } else {
            Intent intent = new Intent(this, RequestCaptureActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ needs a PendingIntent for TileService to launch activities
                android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(
                    this, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
                startActivityAndCollapse(pendingIntent);
            } else {
                startActivityAndCollapse(intent);
            }
        }
        updateTile();
    }
}
