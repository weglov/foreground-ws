package com.wsforeground.plugin;


/**
 * Created by FromTheSeventhSky on 29.03.2018.
 */
public interface BaseAlarmHelper {
    void newOrderNotification(String id);

    void startAutoStopNotification();

    void editOrderNotification();

    void cancelOrderNotification();

    void stopNotification(String id);

    void stopNotifications();

    void restartVibrateIfNeed();
}