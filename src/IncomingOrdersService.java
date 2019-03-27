package com.wsforeground.plugin;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.Nullable;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import okhttp3.OkHttpClient;
import com.wsforeground.plugin.BuildConfig;
import com.wsforeground.plugin.R;
import com.wsforeground.plugin.BaseAlarmHelper;
import com.wsforeground.plugin.MainActivity;
import com.wsforeground.plugin.AlarmHelper;


public class IncomingOrdersService extends Service {
    int FOREGROUND_NOTIFICATION_REQUEST_CODE = 93871;
    int FOREGROUND_NOTIFICATION_ID = 93872;
    String FOREGROUND_NOTIFICATION_CHANNEL = "Yandex.Vendor.Notification.Channel.Foreground";

    SocketInterface socket;

    BaseAlarmHelper alarmHelper;

    CompositeDisposable compositeDisposable;

    WifiManager.WifiLock wifiLock;
    PowerManager.WakeLock powerLock;

    @Override
    public void onCreate() {
        super.onCreate();
        createForegroundNotification(getString(R.string.service_foreground_wait));
        alarmHelper = new AlarmHelper(getBaseContext());

        compositeDisposable = new CompositeDisposable();

        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        PowerManager pm = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);

        if (wifiManager != null && pm != null) {
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "YandexVendorWiFiLock");
            powerLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "YandexVendorPowerLock");
        }

    }

    private void toggleLock(boolean isAppForeground) {
        if (isAppForeground) {
            releaseLock();
        } else {
            acquireLock();
        }
    }

    private void acquireLock() {
        if (wifiLock != null && !wifiLock.isHeld()) {
            wifiLock.acquire();
        }

        if (powerLock != null && !powerLock.isHeld()) {
            powerLock.acquire();
        }
    }

    private void releaseLock() {
        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
        }

        if (powerLock != null && powerLock.isHeld()) {
            powerLock.release();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        socket.done();
        if (compositeDisposable != null && !compositeDisposable.isDisposed()) {
            compositeDisposable.dispose();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String token = intent.getStringExtra("token");
        String wsUrl = intent.getStringExtra("wsUrl");
        boolean isFastFood = intent.getBooleanExtra("isFastFood",false);
        OkHttpClient client = new OkHttpClient.Builder().build();
        socket = new SocketIO(wsUrl, token, client);


        String ver = "Android version: " + Build.VERSION.SDK_INT + " (" + Build.VERSION.RELEASE + ")";
        String query = "";
        try {
            query = "&" + "version=" + URLEncoder.encode(BuildConfig.VERSION_NAME, "UTF-8") +
                    "&" + "versionCode=" + URLEncoder.encode(String.valueOf(BuildConfig.VERSION_CODE), "UTF-8") +
                    "&" + "deviceType=" + URLEncoder.encode("android", "UTF-8") +
                    "&" + "deviceModel=" + URLEncoder.encode(Build.MODEL, "UTF-8") +
                    "&" + "deviceBrand=" + URLEncoder.encode(Build.MANUFACTURER, "UTF-8") +
                    "&" + "deviceOSVersion=" + URLEncoder.encode(ver, "UTF-8") +
                    "&" + "networkClass=" + URLEncoder.encode("plugin", "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        socket.init(query);

        socket.getUpdates().doOnSubscribe(disposer()).subscribe(socketEvent -> {
            switch (socketEvent.event) {
                case NEW:
                    if (isFastFood) {
                        alarmHelper.startAutoStopNotification();
                    } else {
                        alarmHelper.newOrderNotification(socketEvent.id);
                    }
                    /**
                     * TODO
                     */
                    break;
                case CHANGED:
                    alarmHelper.editOrderNotification();
                    /**
                     * TODO
                     */
                    break;
                case STATUS_CHANGED:
                    /**
                     * TODO
                     */
                    break;

            }
        });


        return super.onStartCommand(intent, flags, startId);
    }

    protected Consumer<Disposable> disposer() {
        return d -> compositeDisposable.add(d);
    }


    private void createForegroundNotification(String text) {
        Intent contentIntent = new Intent(this, MainActivity.class);
        contentIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                FOREGROUND_NOTIFICATION_REQUEST_CODE,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, FOREGROUND_NOTIFICATION_CHANNEL);
        } else {
            builder = new Notification.Builder(this);
        }
        builder.setContentTitle(getString(R.string.notification_title))
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.foodfox_launcher))
                .setSmallIcon(R.drawable.logo_monochrome)
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);
        startForeground(FOREGROUND_NOTIFICATION_ID, builder.build());
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
