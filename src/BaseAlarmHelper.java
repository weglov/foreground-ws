package com.wsforeground.plugin;


public interface BaseAlarmHelper {
    void newOrderNotification(String id);

    void startAutoStopNotification();

    void editOrderNotification();

    void cancelOrderNotification();

    void stopNotification(String id);

    void stopNotifications();

    void restartVibrateIfNeed();
}