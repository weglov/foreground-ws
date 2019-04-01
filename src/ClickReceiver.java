package com.wsforeground.plugin;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import java.util.List;

import static android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT;
import static android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;

public class ClickReceiver extends Service {


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("ClickReceiver", "onHandleIntent");
        String orderId = intent.getStringExtra("orderId");
        Log.d("ClickReceiver", orderId);

        Context context = getApplicationContext();
        String pkgName  = context.getPackageName();

        Intent intentActivity = context
                .getPackageManager()
                .getLaunchIntentForPackage(pkgName);

        if (intentActivity == null)
            return START_NOT_STICKY;

        ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> tasks = activityManager.getRunningTasks(Integer.MAX_VALUE);


        intentActivity.addFlags(
                FLAG_ACTIVITY_REORDER_TO_FRONT
                        | FLAG_ACTIVITY_SINGLE_TOP);

        context.startActivity(intentActivity);
        Singleton.getInstance().getCallbackContext().success(orderId);
        stopSelf();

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}