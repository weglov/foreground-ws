package com.wsforeground.plugin;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import android.app.NotificationManager;
import android.content.Intent;
import android.util.Log;
import com.wsforeground.plugin.IncomingOrdersService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.os.Build;


public class SocketService extends CordovaPlugin {
    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {


        if (Singleton.getInstance().getAlarmHelper() == null) {
            Singleton.getInstance().setAlarmHelper(new AlarmHelper(cordova.getContext()));
        }

        if (action.equals("start")) {
            JSONObject settings = args.optJSONObject(0);
            String token = settings.getString("token");
            String wsUrl = settings.getString("url");
            Boolean isFastFood = settings.getBoolean("isFastFood");

            this.start(token, wsUrl, isFastFood, callbackContext);
            return true;
        }

        if (action.equals("stop")) {
            this.stop(callbackContext);
            return true;
        }

        if (action.equals("trigger")) {
            String orderID = args.getString(0);
            this.trigger(orderID);
            return true;
        }

        return false;
    }

    private void start(String token, String wsUrl, Boolean isFastFood, CallbackContext callbackContext) {
        Intent serviceIntent = new Intent(cordova.getActivity(), IncomingOrdersService.class);
        serviceIntent.putExtra("token", token);
        serviceIntent.putExtra("wsUrl", wsUrl);
        serviceIntent.putExtra("isFastFood", isFastFood);
        Singleton.getInstance().setCallbackContext(callbackContext);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            cordova.getActivity().startForegroundService(serviceIntent);
        } else {
            cordova.getActivity().startService(serviceIntent);
        }
    }

    private void stop(CallbackContext callbackContext) {
        Singleton.getInstance().setCallbackContext(null);

        Intent serviceIntent = new Intent(cordova.getActivity(), IncomingOrdersService.class);
        cordova.getActivity().stopService(serviceIntent);
    }

    private void trigger(String orderId) {
        NotificationManager manager = (NotificationManager)cordova.getActivity().getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(orderId.hashCode());
//        Singleton.getInstance().getAlarmHelper().stopNotification(orderId);
    }

    private Context getContext() {
        return cordova.getActivity().getApplicationContext();
    }
}