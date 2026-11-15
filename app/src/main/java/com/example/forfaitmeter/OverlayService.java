package com.example.forfaitmeter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.net.TrafficStats;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

public class OverlayService extends Service {
    private static final long MB = 1024L * 1024L;
    private static final long HOUR_MS = 3600000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private TextView view;
    private SharedPreferences prefs;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            update();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }
        prefs = getSharedPreferences("forfait", MODE_PRIVATE);
        startInForeground();

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        view = new TextView(this);
        view.setTextSize(13);
        view.setTextColor(0xFFFFFFFF);
        view.setBackgroundColor(0xB0000000);
        view.setPadding(20, 10, 20, 10);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = 16;
        lp.y = 80;
        wm.addView(view, lp);

        handler.post(tick);
    }

    private void startInForeground() {
        String channelId = "forfait";
        Notification n;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel c = new NotificationChannel(
                    channelId, "Compteur data", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(c);
            n = new Notification.Builder(this, channelId)
                    .setContentTitle("Compteur data actif")
                    .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                    .build();
        } else {
            n = new Notification.Builder(this)
                    .setContentTitle("Compteur data actif")
                    .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                    .build();
        }
        startForeground(1, n);
    }

    // Same counting logic as MainActivity: adds bytes since the last read.
    private long syncUsed() {
        long rx = TrafficStats.getMobileRxBytes();
        long tx = TrafficStats.getMobileTxBytes();
        if (rx == TrafficStats.UNSUPPORTED || tx == TrafficStats.UNSUPPORTED) return -1;
        long raw = rx + tx;
        long last = prefs.getLong("lastRaw", -1);
        long used = prefs.getLong("used", 0);
        long delta;
        if (last < 0) delta = 0;
        else if (raw >= last) delta = raw - last;
        else delta = raw;
        used += delta;
        prefs.edit().putLong("lastRaw", raw).putLong("used", used).apply();
        return used;
    }

    private void update() {
        if (view == null) return;
        long bundleBytes = prefs.getLong("bundleBytes", 0);
        long used = syncUsed();
        if (bundleBytes == 0 || used < 0) {
            view.setText("Forfait non démarré");
            return;
        }
        long left = Math.max(0, bundleBytes - used);
        long expires = prefs.getLong("startMs", 0) + prefs.getLong("hours", 24) * HOUR_MS;
        long msLeft = expires - System.currentTimeMillis();
        String time = msLeft <= 0 ? "expiré"
                : (msLeft / HOUR_MS) + " h " + ((msLeft % HOUR_MS) / 60000) + " min";
        String warn = (left == 0 || msLeft <= 0) ? "  ⚠"
                : (left * 5 < bundleBytes) ? "  ⚠ 20 %"
                : (msLeft < 3 * HOUR_MS) ? "  ⚠ 3 h" : "";
        view.setText((left / MB) + " Mo · " + time + warn);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(tick);
        if (view != null && wm != null) wm.removeView(view);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
                                                                                  }
