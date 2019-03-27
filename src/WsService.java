package com.wsforeground.plugin;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.Nullable;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.net.UnknownHostException;

import javax.inject.Inject;
import javax.inject.Named;

import io.reactivex.Completable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import ru.foodfox.vendor.App;
import ru.foodfox.vendor.BuildConfig;
import ru.foodfox.vendor.R;
import ru.foodfox.vendor.base.BaseAlarmHelper;
import ru.foodfox.vendor.di.injectors.ServiceInjector;
import ru.foodfox.vendor.logging.Log;
import ru.foodfox.vendor.ui.activities.main.MainActivity;
import ru.foodfox.vendor.utils.NetworkChecker;
import ru.foodfox.vendor.utils.NotifyUtils;

import static android.content.Intent.ACTION_SCREEN_OFF;
import static ru.foodfox.vendor.params.Params.LogType.LOCK_ACQUIRED;
import static ru.foodfox.vendor.params.Params.LogType.LOCK_RELEASED;
import static ru.foodfox.vendor.params.Params.MetricaEvent.EventTitles.EVENT_NEW_ORDER;
import static ru.foodfox.vendor.params.Params.Service.FOREGROUND_NOTIFICATION_CHANNEL;
import static ru.foodfox.vendor.params.Params.Service.FOREGROUND_NOTIFICATION_ID;
import static ru.foodfox.vendor.params.Params.Service.FOREGROUND_NOTIFICATION_REQUEST_CODE;
import static ru.foodfox.vendor.params.Params.SubjectNames.SUBJECT_NOTIFIER;
import static ru.foodfox.vendor.service.ServiceEvent.Event.GET_ORDERS_FROM_CACHE;
import static ru.foodfox.vendor.service.ServiceEvent.Event.ON_ADD_ORDER;
import static ru.foodfox.vendor.service.ServiceEvent.Event.ON_CHANGE_MENU_FAIL;
import static ru.foodfox.vendor.service.ServiceEvent.Event.ON_CHANGE_ORDER;
import static ru.foodfox.vendor.service.ServiceEvent.Event.ON_REFUSE_ORDER;
import static ru.foodfox.vendor.service.ServiceEvent.Event.ON_STATUS_CHANGED;
import static ru.foodfox.vendor.service.ServiceEvent.Event.ON_UPDATE_TIMER;
import static ru.foodfox.vendor.service.ServiceEvent.Event.RELOAD_ORDERS;
import static ru.foodfox.vendor.ui.features.orders.list.models.StatusMapper.OrderState.ACCEPTED;
import static ru.foodfox.vendor.ui.features.orders.list.models.StatusMapper.OrderState.DELIVERED;
import static ru.foodfox.vendor.ui.features.orders.list.models.StatusMapper.OrderState.NO_SHOW;
import static ru.foodfox.vendor.ui.features.orders.list.models.StatusMapper.OrderState.REFUSED;
import static ru.foodfox.vendor.ui.features.orders.list.models.StatusMapper.OrderState.RELEASED;
import static ru.foodfox.vendor.utils.DateUtils.restoreRestaurantTimezone;
import static ru.foodfox.vendor.utils.DateUtils.setTimeDiff;
import static ru.foodfox.vendor.utils.NetworkChecker.getNetworkClass;
import static ru.foodfox.vendor.utils.NotifyUtils.buildNotification;

/**
 * Created by FromTheSeventhSky on 25.02.2018.
 */

public class IncomingOrdersService extends Service {
    SocketInterface socket;

    @Inject
    @Named(SUBJECT_NOTIFIER)
    PublishSubject<ServiceEvent> serviceCallbacks;

    @Inject
    ServiceContract.Interactor interactor;

    @Inject
    BaseAlarmHelper alarmHelper;

    CompositeDisposable compositeDisposable;

    WifiManager.WifiLock wifiLock;
    PowerManager.WakeLock powerLock;

    @Override
    public void onCreate() {
        super.onCreate();
        socket = new SocketIO()
        createForegroundNotification(getString(R.string.service_foreground_wait));
        ServiceInjector.getComponent().inject(this);

        compositeDisposable = new CompositeDisposable();

        App.getInstance().getLifecycle().doOnSubscribe(disposer()).subscribe(this::toggleLock);

        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        PowerManager pm = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);

        if (wifiManager != null && pm != null) {
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "YandexVendorWiFiLock");
            powerLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "YandexVendorPowerLock");
        }

        String ver = "Android version: " + Build.VERSION.SDK_INT + " (" + Build.VERSION.RELEASE + ")";
        String query = "";
        try {
            query = "&" + "version=" + URLEncoder.encode(BuildConfig.VERSION_NAME, "UTF-8") +
                    "&" + "versionCode=" + URLEncoder.encode(String.valueOf(BuildConfig.VERSION_CODE), "UTF-8") +
                    "&" + "deviceType=" + URLEncoder.encode("android", "UTF-8") +
                    "&" + "deviceModel=" + URLEncoder.encode(Build.MODEL, "UTF-8") +
                    "&" + "deviceBrand=" + URLEncoder.encode(Build.MANUFACTURER, "UTF-8") +
                    "&" + "deviceOSVersion=" + URLEncoder.encode(ver, "UTF-8") +
                    "&" + "networkClass=" + URLEncoder.encode(getNetworkClass().toLowerCase(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        socket.init(query);

        NetworkChecker.getInstance().startPing();

        interactor
                .startUpdateDate()
                .doOnSubscribe(disposer())
                .subscribe(timerTime -> serviceCallbacks.onNext(new ServiceEvent(ON_UPDATE_TIMER)));

        socket.getUpdates().doOnSubscribe(disposer()).subscribe(socketEvent -> {
            switch (socketEvent.event) {
                case RESTAURANT_TOGGLE:
                    interactor.reloadRestaurants()
                            .doOnSubscribe(disposer())
                            .subscribe(response -> {});
                    break;
                case CHANGE_MENU_FAIL:
                    serviceCallbacks.onNext(new ServiceEvent(ON_CHANGE_MENU_FAIL));
                    break;
                case CONNECTED:
                    createForegroundNotification(getString(R.string.service_foreground_active));
                    break;
                case DISCONNECTED:
                    createForegroundNotification(getString(R.string.service_foreground_inactive));
                    break;
                case NEW:
                    interactor.isFastFood().subscribe(isFastFood -> {
                        if (isFastFood) {
                            alarmHelper.startAutoStopNotification();
                        } else {
                            alarmHelper.newOrderNotification(socketEvent.id);
                        }
                    });
                    interactor.loadOrder(socketEvent.id).subscribe(order -> {
                        serviceCallbacks.onNext(new ServiceEvent(GET_ORDERS_FROM_CACHE));
                        String message = getString(R.string.order_notification_new, order.getSum());
                        NotifyUtils.showNotify(order.hashCode(), buildNotification(getString(R.string.notification_new_order_title), message, socketEvent.id, R.raw.new_order, this));
                        serviceCallbacks.onNext(new ServiceEvent(ON_ADD_ORDER).setId(order.getId()).setMessage(message));
                    }, throwable -> {
                        serviceCallbacks.onNext(new ServiceEvent(RELOAD_ORDERS));
                        String message = getString(R.string.order_notification_new_empty);
                        NotifyUtils.showNotify(9000, buildNotification(getString(R.string.notification_new_order_title), message, null, R.raw.new_order, this));
                        serviceCallbacks.onNext(new ServiceEvent(ON_ADD_ORDER).setMessage(message));
                    });
                    break;
                case CHANGED:
                    alarmHelper.editOrderNotification();
                    interactor.changeOrderEvent(socketEvent.id).subscribe(order -> {
                        serviceCallbacks.onNext(new ServiceEvent(GET_ORDERS_FROM_CACHE));
                        String message = getString(R.string.order_notification_changed, order.getExternalId());
                        NotifyUtils.showNotify(order.hashCode(),
                                buildNotification(getString(R.string.notification_new_order_title),
                                        message,
                                        socketEvent.id,
                                        R.raw.edited_order,
                                        this));
                        serviceCallbacks.onNext(new ServiceEvent(ON_CHANGE_ORDER).setId(order.getId()).setMessage(message));
                    }, throwable -> {
                        serviceCallbacks.onNext(new ServiceEvent(RELOAD_ORDERS));
                        String message = getString(R.string.order_notification_changed_empty);
                        NotifyUtils.showNotify(9100, buildNotification(getString(R.string.notification_new_order_title), message, null, R.raw.edited_order, this));
                        serviceCallbacks.onNext(new ServiceEvent(ON_CHANGE_ORDER).setMessage(message));
                    });
                    break;
                case STATUS_CHANGED:
                    switch (socketEvent.status) {
                        case ACCEPTED:
                        case RELEASED:
                        case DELIVERED:
                            alarmHelper.stopNotification(socketEvent.id);
                            interactor.changeStatusEvent(socketEvent).subscribe(result -> {
                                if (result) {
                                    serviceCallbacks.onNext(new ServiceEvent(GET_ORDERS_FROM_CACHE));
                                    serviceCallbacks.onNext(new ServiceEvent(ON_STATUS_CHANGED).setId(socketEvent.id));
                                } else {
                                    serviceCallbacks.onNext(new ServiceEvent(RELOAD_ORDERS));
                                }
                            });
                            break;
                        case NO_SHOW:
                        case REFUSED:
                            alarmHelper.stopNotification(socketEvent.id);
                            alarmHelper.cancelOrderNotification();
                            interactor.cancelOrderEvent(socketEvent).subscribe(order -> {
                                serviceCallbacks.onNext(new ServiceEvent(GET_ORDERS_FROM_CACHE));
                                String message = getString(R.string.order_notification_refused, order.getExternalId(), order.getSum());
                                NotifyUtils.showNotify(order.hashCode(), buildNotification(getString(R.string.notification_new_order_title), message, socketEvent.id, R.raw.canceled_order, this));
                                serviceCallbacks.onNext(new ServiceEvent(ON_REFUSE_ORDER).setId(order.getId()).setMessage(message));
                                serviceCallbacks.onNext(new ServiceEvent(ON_STATUS_CHANGED).setId(socketEvent.id));
                            }, throwable -> {
                                serviceCallbacks.onNext(new ServiceEvent(RELOAD_ORDERS));
                                String message = getString(R.string.order_notification_refused_empty);
                                NotifyUtils.showNotify(9200, buildNotification(getString(R.string.notification_new_order_title), message, null, R.raw.canceled_order, this));
                                serviceCallbacks.onNext(new ServiceEvent(ON_REFUSE_ORDER).setMessage(message));
                            });
                            break;
                        default:
                            interactor.changeStatusEvent(socketEvent).subscribe(result -> {
                                if (result) {
                                    serviceCallbacks.onNext(new ServiceEvent(GET_ORDERS_FROM_CACHE));
                                    serviceCallbacks.onNext(new ServiceEvent(ON_STATUS_CHANGED).setId(socketEvent.id));
                                } else {
                                    serviceCallbacks.onNext(new ServiceEvent(RELOAD_ORDERS));
                                }
                            });
                            break;
                    }
                    break;

            }
        });
    }

    private void toggleLock(boolean isAppForeground) {
        if (isAppForeground) {
            releaseLock();
        } else {
            acquireLock();
        }
    }

    private void acquireLock() {
        Log.chop(LOCK_ACQUIRED);
        if (wifiLock != null && !wifiLock.isHeld()) {
            wifiLock.acquire();
        }

        if (powerLock != null && !powerLock.isHeld()) {
            powerLock.acquire();
        }
    }

    private void releaseLock() {
        Log.chop(LOCK_RELEASED);
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
        ServiceInjector.clearComponent();
        releaseLock();
        socket.done();
        NetworkChecker.getInstance().stopPing();
        if (compositeDisposable != null && !compositeDisposable.isDisposed()) {
            compositeDisposable.dispose();
        }
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