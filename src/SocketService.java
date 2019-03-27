package com.wsforeground.plugin;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import android.content.Intent;
import com.wsforeground.plugin.IncomingOrdersService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.os.Build;


public class WsForeground extends CordovaPlugin {
    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

        if (action.equals("start")) {
            String token = args.getString(0);
            String wsUrl = args.getString(1);
            Boolean isFastFood = args.getBoolean(2);
            this.start(token, callbackContext);
            return true;
        }

        if (action.equals("stop")) {
            this.stop(callbackContext);
            return true;
        }

        return false;
    }

    private void start(String token, String wsUrl, Boolean isFastFood, isCallbackContext callbackContext) {
      Intent serviceIntent = new Intent(cordova.getActivity(), IncomingOrdersService.class);
      serviceIntent.putExtra("token", token);
      serviceIntent.putExtra("wsUrl", wsUrl);
      serviceIntent.putExtra("isFastFood", isFastFood);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          cordova.getActivity().startForegroundService(serviceIntent);
      } else {
          cordova.getActivity().startService(serviceIntent);
      }
    }

    private void stop(CallbackContext callbackContext) {
      Intent serviceIntent = new Intent(cordova.getActivity(), IncomingOrdersService.class);
      cordova.getActivity().stopService(serviceIntent);
    }

    private Context getContext() {
        return cordova.getActivity().getApplicationContext();
    }
}