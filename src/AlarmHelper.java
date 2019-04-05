package com.wsforeground.plugin;

import android.content.Context;
import android.content.res.Resources;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;


import static android.content.Context.VIBRATOR_SERVICE;


public class AlarmHelper implements BaseAlarmHelper {

    private MediaPlayer player;

    private Context context;

    private Uri newOrderUri;
    private Uri editedOrderUri;
    private Uri cancelledOrderUri;

    private static final int REPEAT = 0;
    private static final int SINGLE = -1;

    private Vibrator vibrator;

    private Set<String> ringingNewOrders;

    private int notificationPlayedCount;
    private boolean longNotificationActive = false;

    private boolean isVibrating = false;
    private long[] lastVibrationPattern = null;

    private long[] vibrationPatternNew = {0, 155, 700, 2195};
    private long[] vibrationPatternEdit = {250, 750, 500, 250, 750, 250};
    private long[] vibrationPatternCancel = {250, 250, 250, 250, 750, 750, 250, 250};
    private int getSoundId (String name)
    {
        Resources res  = context.getResources();
        String pkgName = context.getPackageName();

        int resId =  res.getIdentifier(name, "raw", pkgName);
        return resId;
    }

    public AlarmHelper(Context context) {
        try {
            this.context = context;
            newOrderUri = Uri.parse("android.resource://" + context.getPackageName() + "/" + getSoundId("new_order"));
            editedOrderUri = Uri.parse("android.resource://" + context.getPackageName() + "/" + getSoundId("order_changed"));
            cancelledOrderUri = Uri.parse("android.resource://" + context.getPackageName() + "/" + getSoundId("order_cancelled"));

            ringingNewOrders = new HashSet<>();
            vibrator = (Vibrator) context.getSystemService(VIBRATOR_SERVICE);
        } catch (Exception e) {
            // Timber.e(e);
        }
    }

    @Override
    public void stopNotification(String id) {
        ringingNewOrders.remove(id);
        if (ringingNewOrders.isEmpty()) {
            MediaPlayer player = getPlayer();
            if (player != null) {
                player.reset();
            }
            cancelVibrate();
        }
    }

    @Override
    public void startAutoStopNotification() {
        longNotificationActive = true;
        notificationPlayedCount = 0;
        MediaPlayer player = getPlayer();
        if (player != null) {
            vibrate(vibrationPatternNew, SINGLE);
            try {
                player.reset();
                player.setDataSource(context, newOrderUri);
                player.prepareAsync();
                player.setLooping(false);
                player.setOnPreparedListener(MediaPlayer::start);
                player.setOnCompletionListener(mp -> {
                    if (notificationPlayedCount < 20) {
                        notificationPlayedCount++;
                        player.seekTo(0);
                        player.start();
                    } else {
                        longNotificationActive = false;
                        notificationPlayedCount = 0;
                    }
                });
            } catch (IOException e) {
                // Timber.e(e);
            }
        }
    }

    @Override
    public void stopNotifications() {
        ringingNewOrders.clear();
        longNotificationActive = false;
        notificationPlayedCount = 0;
        MediaPlayer player = getPlayer();
        if (player != null) {
            player.reset();
        }
        cancelVibrate();
    }

    @Override
    public void newOrderNotification(String id) {
        MediaPlayer player = getPlayer();
        if (player != null) {
            if (ringingNewOrders.isEmpty()) {
                player.reset();
                try {
                    cancelVibrate();
                    vibrate(vibrationPatternNew, REPEAT);
                    player.setDataSource(context, newOrderUri);
                    player.prepareAsync();
                    player.setLooping(true);
                    player.setOnPreparedListener(MediaPlayer::start);
                } catch (IOException e) {
                    // Timber.e(e);
                }
            }
            ringingNewOrders.add(id);
        }
    }

    @Override
    public void editOrderNotification() {
        if (ringingNewOrders.isEmpty() && !longNotificationActive) {
            MediaPlayer player = getPlayer();
            if (player != null) {
                player.reset();
                try {
                    cancelVibrate();
                    vibrate(vibrationPatternEdit, SINGLE);
                    player.setDataSource(context, editedOrderUri);
                    player.prepareAsync();
                    player.setLooping(false);
                    player.setOnPreparedListener(MediaPlayer::start);
                } catch (IOException e) {
                    // Timber.e(e);
                }
            }
        }
    }

    @Override
    public void cancelOrderNotification() {
        if (ringingNewOrders.isEmpty() && !longNotificationActive) {
            MediaPlayer player = getPlayer();
            if (player != null) {
                player.reset();
                try {
                    cancelVibrate();
                    vibrate(vibrationPatternCancel, SINGLE);
                    player.setDataSource(context, cancelledOrderUri);
                    player.prepareAsync();
                    player.setLooping(false);
                    player.setOnPreparedListener(MediaPlayer::start);
                } catch (IOException e) {
                    // Timber.e(e);
                }
            }
        }
    }

    @Override
    public void restartVibrateIfNeed() {
        if (isVibrating && lastVibrationPattern != null) {
            vibrate(lastVibrationPattern, REPEAT);
        }
    }

    private MediaPlayer getPlayer() {
        if (player == null) {
            player = MediaPlayer.create(context, getSoundId("new_order"));
            player.setLooping(true);
        }
        return player;
    }

    private void vibrate(long[] pattern, int repeat) {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, repeat));
            } else {
                vibrator.vibrate(pattern, repeat);
            }
            if (repeat == REPEAT) {
                lastVibrationPattern = pattern;
                isVibrating = true;
            }
        }
    }

    private void cancelVibrate() {
        if (vibrator != null && vibrator.hasVibrator()) {
            isVibrating = false;
            lastVibrationPattern = null;
            vibrator.cancel();
        }
    }
}