package com.wsforeground.plugin;

import org.apache.cordova.CallbackContext;

public class Singleton {

    private static Singleton singleton;

    private CallbackContext callbackContext;
    private BaseAlarmHelper alarmHelper;

    private Singleton() {}

    public static Singleton getInstance() {
        if (singleton == null) {
            singleton = new Singleton();
        }

        return singleton;
    }

    public CallbackContext getCallbackContext() {
        return callbackContext;
    }

    public void setCallbackContext(CallbackContext callbackContext) {
        this.callbackContext = callbackContext;
    }

    public BaseAlarmHelper getAlarmHelper() {
        return alarmHelper;
    }

    public void setAlarmHelper(BaseAlarmHelper alarmHelper) {
        this.alarmHelper = alarmHelper;
    }
}
