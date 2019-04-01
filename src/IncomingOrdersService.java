package com.wsforeground.plugin;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.content.res.Resources;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import okhttp3.OkHttpClient;
import ru.yandex.vendor.dev.BuildConfig;
import ru.yandex.vendor.dev.MainActivity;


public class IncomingOrdersService extends Service {
    int FOREGROUND_NOTIFICATION_REQUEST_CODE = 93871;
    int FOREGROUND_NOTIFICATION_ID = 93872;
    String FOREGROUND_NOTIFICATION_CHANNEL = "Yandex.Vendor.Notification.Channel.Foreground";
    boolean WS_INIT = false;
    String NOTIFICATION_CHANNEL = "Yandex.Vendor.Notification.Channel";

    SocketIO socket;

    BaseAlarmHelper alarmHelper;

    CompositeDisposable compositeDisposable;

    WifiManager.WifiLock wifiLock;
    PowerManager.WakeLock powerLock;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("message", "onCreate");
        createForegroundNotification("Приложение работает в фоновом режиме", "Связь с сервисом установлена");
        alarmHelper = new AlarmHelper(getBaseContext());

        compositeDisposable = new CompositeDisposable();

        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        PowerManager pm = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);

        if (wifiManager != null && pm != null) {
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "YandexVendorWiFiLock");
            powerLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "YandexVendorPowerLock");
        }
        acquireLock();

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
        releaseLock();
        socket.done();
        WS_INIT = false;
        alarmHelper.stopNotifications();
        if (compositeDisposable != null && !compositeDisposable.isDisposed()) {
            compositeDisposable.dispose();
        }
    }

    @SuppressLint("CheckResult")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!WS_INIT) {
            return Service.START_NOT_STICKY;
        }
        WS_INIT = true;
        Log.d("WS", "" + startId);
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
                    createNotification(socketEvent.id, "", "Новый заказ!");
                    break;
                case CHANGED:
                    alarmHelper.stopNotification(socketEvent.id);
                    alarmHelper.editOrderNotification();
                    createNotification(socketEvent.id, "", "Заказ изменен");
                case STATUS_CHANGED:
                    alarmHelper.stopNotification(socketEvent.id);
                    switch (socketEvent.status) {
                        case "accepted":
                        case "released":
                        case "delivered":
                            alarmHelper.stopNotification(socketEvent.id);
                            break;
                        case "refused":
                            alarmHelper.cancelOrderNotification();
                            createNotification(socketEvent.id, "", "Заказ отменен");
                            break;
                    }
                    break;

            }
        });


        return super.onStartCommand(intent, flags, startId);
    }

    private int getIconResId ()
    {
        Resources res  = getResources();
        String pkgName = getPackageName();

        int resId =  res.getIdentifier("monochrome", "drawable", pkgName);
        return resId;
    }

    protected Consumer<Disposable> disposer() {
        return d -> compositeDisposable.add(d);
    }

    private void createForegroundNotification(String text, String title) {
        Intent contentIntent = new Intent(this, MainActivity.class);
        NotificationManager manager = (NotificationManager)getBaseContext().getSystemService(NOTIFICATION_SERVICE);
        contentIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                FOREGROUND_NOTIFICATION_REQUEST_CODE,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Yandex.eda ожидание заказа";

            NotificationChannel mChannel = new NotificationChannel(FOREGROUND_NOTIFICATION_CHANNEL, name,  NotificationManager.IMPORTANCE_DEFAULT);
            manager.createNotificationChannel(mChannel);
            builder = new Notification.Builder(this, FOREGROUND_NOTIFICATION_CHANNEL);
        } else {
            builder = new Notification.Builder(this);
        }
        builder.setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setAutoCancel(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setSmallIcon(getIconResId());
        } else {
            builder.setSmallIcon(android.R.drawable.btn_star);
        }
        startForeground(FOREGROUND_NOTIFICATION_ID, builder.build());
    }

    private void createNotification(String orderId, String text, String title) {

        NotificationManager manager = (NotificationManager)getBaseContext().getSystemService(NOTIFICATION_SERVICE);
        Intent contentIntent = new Intent(this, ClickReceiver.class);
        contentIntent.putExtra("orderId", orderId);
        PendingIntent pendingIntent = PendingIntent.getService(
                this,
                1112,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Yandex.eda новые заказы";

            NotificationChannel mChannel = new NotificationChannel(NOTIFICATION_CHANNEL, name,  NotificationManager.IMPORTANCE_DEFAULT);
            manager.createNotificationChannel(mChannel);

            builder = new Notification.Builder(this, NOTIFICATION_CHANNEL);
        } else {
            builder = new Notification.Builder(this);
        }
        builder.setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setSmallIcon(getIconResId());
        } else {
            builder.setSmallIcon(android.R.drawable.btn_star);
        }

        manager.notify(orderId.hashCode(), builder.build());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
