package com.wcsn.photogallery;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.util.ArrayList;


public class PollService extends IntentService {
    private static final String TAG = "PollService";

    private static final int POLL_INTERVAL = 1000 * 15;

    public PollService() {
        super("PollService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        //检查网络
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        boolean isNetWorkAvailable = cm.getBackgroundDataSetting() && cm.getActiveNetworkInfo() != null;

        Log.i(TAG, "Receive an intent:" + intent);
        if (!isNetWorkAvailable) return;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String query = sharedPreferences.getString(FlickrFetchr.PREF_SEARCH_QUERY, null);
        String lastResultId = sharedPreferences.getString(FlickrFetchr.PREF_LAST_RESULT_ID, null);

        //使用FlickrFetcher获取最新结果集合，如果有结果返回，获取第一条，确认不同于上一次的结果，则保存结果
        ArrayList<GalleryItem> items;
        if (query != null) {
            items = new FlickrFetchr().search(query);
        } else {
            items = new FlickrFetchr().fetchItems();
        }

        if (items.size() == 0) return;

        String resultId = items.get(0).getId();

        if (!resultId.equals(lastResultId)) {
            Log.i(TAG, "got a new result:" + resultId);

            Resources r = getResources();
            PendingIntent pi = PendingIntent
                    .getActivity(this, 0, new Intent(this, PhotoGalleryActivity.class), 0);

            Notification notification = new NotificationCompat.Builder(this)
                    .setTicker(r.getString(R.string.new_pictures_title))
                    .setSmallIcon(android.R.drawable.ic_menu_report_image)
                    .setContentTitle(r.getString(R.string.new_pictures_title))
                    .setContentText(r.getString(R.string.new_pictures_text))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();

            NotificationManager notificationManager = (NotificationManager)
                    getSystemService(NOTIFICATION_SERVICE);

            notificationManager.notify(0, notification);
            sharedPreferences.edit()
                    .putString(FlickrFetchr.PREF_LAST_RESULT_ID, resultId)
                    .apply();
        } else {
            Log.i(TAG, "got an old result:" + resultId);
        }

    }

    public static void setServiceAlarm(Context context, boolean isOn) {
        Intent i  = new Intent(context, PollService.class);
        //创建用于启动PollService的PendingIntent
        PendingIntent pi = PendingIntent.getService(context, 0, i, 0);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        if (isOn) {
            alarmManager.setRepeating(AlarmManager.RTC, System.currentTimeMillis(), POLL_INTERVAL, pi);
        } else {
            alarmManager.cancel(pi);
            pi.cancel();
        }
    }

    public static boolean isServiceAlarmOn(Context context) {
        Intent i = new Intent(context, PollService.class);
        //FLAG_NO_CREATE表示如果PendingIntent不存在，则返回null，而不是创建它
        PendingIntent pi = PendingIntent.getService(context, 0, i ,PendingIntent.FLAG_NO_CREATE);
        return pi != null;
    }
}
