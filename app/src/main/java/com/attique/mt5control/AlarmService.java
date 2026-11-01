package com.attique.mt5control;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.io.ByteArrayOutputStream;

/**
 * Foreground service: every few seconds asks the MT5 Control server for the phone's alarms.
 * When the server says an alarm touched its price, the phone rings (system alarm sound, loops) until Stop.
 */
public class AlarmService extends Service {
    static final String ACTION_STOP = "com.attique.mt5control.STOP";
    static final String ACTION_TEST = "com.attique.mt5control.TEST";
    private static final String CH_WATCH = "watch", CH_ALARM = "alarm";
    private static final int ID_WATCH = 1, ID_ALARM = 2;
    private static AlarmService inst;

    private volatile boolean running;
    private Thread worker;
    private MediaPlayer player;
    private PowerManager.WakeLock wake;
    private volatile String ringingId = "";
    private volatile long testUntil = 0;
    private volatile long silencedAt = 0;

    static void silence() {
        AlarmService s = inst;
        if (s != null) s.stopRing(true);
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        inst = this;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CH_WATCH, "Alarm watching", NotificationManager.IMPORTANCE_LOW));
            NotificationChannel a = new NotificationChannel(CH_ALARM, "Price alarm", NotificationManager.IMPORTANCE_HIGH);
            a.setSound(null, null);
            a.enableVibration(false);
            nm.createNotificationChannel(a);
        }
    }

    private Notification.Builder builder(String channel) {
        return Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, channel) : new Notification.Builder(this);
    }

    private PendingIntent openApp() {
        Intent i = new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification watchNotif() {
        return builder(CH_WATCH).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("MT5 Control").setContentText("Watching your price alarms")
                .setOngoing(true).setContentIntent(openApp()).build();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(ID_WATCH, watchNotif(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(ID_WATCH, watchNotif());
        }
        String act = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(act)) {
            stopRing(true);
        } else if (ACTION_TEST.equals(act)) {
            testUntil = System.currentTimeMillis() + 5000;
            ringNow("Test alarm", "This is how your alarm will sound", "test");
        }
        if (!running) {
            running = true;
            worker = new Thread(this::loop, "alarm-watch");
            worker.start();
        }
        return START_STICKY;
    }

    private void loop() {
        while (running) {
            try {
                poll();
            } catch (Exception ignored) { }
            if (testUntil != 0 && System.currentTimeMillis() > testUntil) {
                testUntil = 0;
                if ("test".equals(ringingId)) stopRing(false);
            }
            try { Thread.sleep(8000); } catch (InterruptedException e) { return; }
        }
    }

    private void poll() throws Exception {
        SharedPreferences p = getSharedPreferences("mt5", Context.MODE_PRIVATE);
        String base = p.getString("base", ""), token = p.getString("token", ""), dev = p.getString("dev", "");
        if (base.isEmpty() || dev.isEmpty()) return;
        HttpURLConnection c = (HttpURLConnection) new URL(base + "/public/alarm/list?dev=" + URLEncoder.encode(dev, "UTF-8")).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("x-token", token);
        int code = c.getResponseCode();
        if (code == 401 || code == 403) {
            notifyLoginNeeded();
            return;
        }
        if (code != 200) return;
        String body = read(c.getInputStream());
        JSONArray arr = new JSONObject(body).getJSONArray("alarms");
        String firstId = "", title = "", text = "";
        for (int i = 0; i < arr.length(); i++) {
            JSONObject a = arr.getJSONObject(i);
            if (a.optDouble("fired", 0) > 0 && !a.optBoolean("ack", true)) {
                firstId = a.getString("id");
                title = "Price alarm: " + a.optString("title");
                text = "Price touched " + a.optString("price");
                break;
            }
        }
        if (!firstId.isEmpty()) {
            if (!firstId.equals(ringingId) && !"test".equals(ringingId)) ringNow(title, text, firstId);
        } else if (!ringingId.isEmpty() && !"test".equals(ringingId)) {
            stopRing(false);
        }
    }

    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[4096];
        int n;
        while ((n = in.read(b)) > 0) o.write(b, 0, n);
        in.close();
        return o.toString("UTF-8");
    }

    private void notifyLoginNeeded() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Notification n = builder(CH_WATCH).setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("MT5 Control").setContentText("Login expired. Open the app and log in again to keep your alarms.")
                .setAutoCancel(true).setContentIntent(openApp()).build();
        nm.notify(3, n);
    }

    private synchronized void ringNow(String title, String text, String id) {
        stopRing(false);
        ringingId = id;
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mt5:alarm");
            wake.acquire(120000);
        } catch (Exception ignored) { }
        try {
            Uri u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            player = new MediaPlayer();
            player.setDataSource(this, u);
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            player.setLooping(true);
            player.prepare();
            player.start();
        } catch (Exception e) {
            player = null;
        }
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            long[] pat = {0, 600, 400, 600, 400};
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pat, 0));
            else v.vibrate(pat, 0);
        } catch (Exception ignored) { }
        Intent stop = new Intent(this, AlarmService.class).setAction(ACTION_STOP);
        PendingIntent sp = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = builder(CH_ALARM).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title).setContentText(text).setPriority(Notification.PRIORITY_MAX)
                .setCategory(Notification.CATEGORY_ALARM).setOngoing(true)
                .setContentIntent(openApp()).setFullScreenIntent(openApp(), true)
                .addAction(new Notification.Action.Builder(null, "STOP", sp).build()).build();
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(ID_ALARM, n);
    }

    /** silence the sound; with ack=true also tell the server so it stops repeating */
    private synchronized void stopRing(boolean ack) {
        final String id = ringingId;
        ringingId = "";
        try { if (player != null) { player.stop(); player.release(); } } catch (Exception ignored) { }
        player = null;
        try { ((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).cancel(); } catch (Exception ignored) { }
        try { if (wake != null && wake.isHeld()) wake.release(); } catch (Exception ignored) { }
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(ID_ALARM);
        if (ack && !id.isEmpty() && !"test".equals(id)) {
            new Thread(() -> {
                try {
                    SharedPreferences p = getSharedPreferences("mt5", Context.MODE_PRIVATE);
                    HttpURLConnection c = (HttpURLConnection) new URL(p.getString("base", "") + "/public/alarm/ack").openConnection();
                    c.setRequestMethod("POST");
                    c.setDoOutput(true);
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    c.setRequestProperty("x-token", p.getString("token", ""));
                    c.setRequestProperty("Content-Type", "application/json");
                    String js = "{\"dev\":\"" + p.getString("dev", "") + "\",\"id\":\"" + id + "\"}";
                    OutputStream o = c.getOutputStream();
                    o.write(js.getBytes("UTF-8"));
                    o.close();
                    c.getResponseCode();
                } catch (Exception ignored) { }
            }).start();
        }
    }

    @Override public void onDestroy() {
        running = false;
        if (worker != null) worker.interrupt();
        stopRing(false);
        inst = null;
        super.onDestroy();
    }
}
