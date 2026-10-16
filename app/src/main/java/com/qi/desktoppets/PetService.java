package com.qi.desktoppets;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/** 前台服务：把十只桌宠作为悬浮窗放出来，并用 30fps 的心跳驱动它们。 */
public class PetService extends Service implements PetView.Listener {

    public static final String[] KEYS = {
            "gemini", "deepseek", "chatgpt", "claude", "doubao",
            "qwen", "copilot", "glm", "grok", "apple"
    };

    private WindowManager wm;
    private final List<PetView> pets = new ArrayList<>();
    private final List<WindowManager.LayoutParams> params = new ArrayList<>();
    private final Handler h = new Handler(Looper.getMainLooper());
    private long lastGreet;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        startForegroundCompat();
        int screenW = getResources().getDisplayMetrics().widthPixels;
        float d = getResources().getDisplayMetrics().density;
        for (int i = 0; i < KEYS.length; i++) {
            String key = KEYS[i];
            Bitmap[] frames = loadFrames(key);
            if (frames == null) continue;
            String[] lines = loadLines("lines_" + key);
            int startX = (int) ((40 + (i % 5) * 120) * d);
            int startY = (int) ((120 + (i / 5) * 180) * d);
            PetView v = new PetView(this, frames, lines, startX, startY, this);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    Build.VERSION.SDK_INT >= 26
                            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    android.graphics.PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.x = startX;
            lp.y = startY;
            try {
                wm.addView(v, lp);
                pets.add(v);
                params.add(lp);
            } catch (Exception e) {
                Toast.makeText(this, "addView failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
        tick();
    }

    private void startForegroundCompat() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(
                    "pets", getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW));
        }
        Intent stop = new Intent(this, PetService.class);
        stop.setAction("STOP");
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getService(this, 0, stop, flags);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, "pets")
                : new Notification.Builder(this);
        b.setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text))
                .setSmallIcon(android.R.drawable.btn_star)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                        getString(R.string.action_stop), pi).build());
        Notification n = b.build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, n);
        }
    }

    private int rawId(String name) {
        return getResources().getIdentifier(name, "raw", getPackageName());
    }

    private static final String[] POSE_NAMES = {
            "01idle", "02blink", "03think", "04ping", "05bounce", "06spin",
            "07wobble", "08pat", "09sleep", "10stretch", "11look", "12slide"
    };

    /** 载入一只宠物的 12 帧姿势图；缺的帧留 null，PetView 会自动回退到待机图。 */
    private Bitmap[] loadFrames(String key) {
        Bitmap[] out = new Bitmap[POSE_NAMES.length];
        int found = 0;
        for (int i = 0; i < POSE_NAMES.length; i++) {
            int id = rawId("pet_" + key + "_" + POSE_NAMES[i]);
            if (id == 0) id = rawId(key + "_" + POSE_NAMES[i]);
            if (id == 0) continue;
            try (InputStream in = getResources().openRawResource(id)) {
                out[i] = BitmapFactory.decodeStream(in);
                if (out[i] != null) found++;
            } catch (Exception ignored) { }
        }
        return found > 0 ? out : null;
    }

    private Bitmap loadBitmap(String name) {
        int id = rawId(name);
        if (id == 0) return null;
        try (InputStream in = getResources().openRawResource(id)) {
            return BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            return null;
        }
    }

    private String[] loadLines(String name) {
        int id = rawId(name);
        if (id == 0) return new String[0];
        try (InputStream in = getResources().openRawResource(id)) {
            BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String ln;
            while ((ln = r.readLine()) != null) sb.append(ln);
            JSONObject o = new JSONObject(sb.toString());
            JSONObject lines = o.optJSONObject("lines");
            if (lines == null) return new String[0];
            List<String> out = new ArrayList<>();
            JSONArray names = lines.names();
            for (int i = 0; names != null && i < names.length(); i++) {
                JSONArray arr = lines.optJSONArray(names.optString(i));
                for (int j = 0; arr != null && j < arr.length(); j++) out.add(arr.optString(j));
            }
            return out.toArray(new String[0]);
        } catch (Exception e) {
            return new String[0];
        }
    }

    private void tick() {
        int screenW = getResources().getDisplayMetrics().widthPixels;
        for (int i = 0; i < pets.size(); i++) {
            PetView v = pets.get(i);
            WindowManager.LayoutParams lp = params.get(i);
            v.tick(screenW);
            v.wx += v.dragDx;
            v.wy += v.dragDy;
            v.dragDx = 0;
            v.dragDy = 0;
            lp.x = (int) v.wx;
            lp.y = (int) (v.wy + v.bob());
            try { wm.updateViewLayout(v, lp); } catch (Exception ignored) { }
        }
        // 两只靠得近就打个招呼（本地实现，不需要网络）
        for (int a = 0; a < pets.size(); a++) {
            for (int b = a + 1; b < pets.size(); b++) {
                PetView pa = pets.get(a), pb = pets.get(b);
                if (Math.abs(pa.getX() - pb.getX()) < 200 * getResources().getDisplayMetrics().density
                        && System.currentTimeMillis() - lastGreet > 4000) {
                    pa.greet();
                    pb.greet();
                    lastGreet = System.currentTimeMillis();
                }
            }
        }
        h.postDelayed(this::tick, 33);
    }

    @Override
    public void onLongPress(PetView v) {
        int i = pets.indexOf(v);
        if (i >= 0) {
            try { wm.removeView(v); } catch (Exception ignored) { }
            pets.remove(i);
            params.remove(i);
            Toast.makeText(this, getString(R.string.toast_pet_gone), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        h.removeCallbacksAndMessages(null);
        for (PetView v : pets) {
            try { wm.removeView(v); } catch (Exception ignored) { }
        }
        pets.clear();
        params.clear();
        super.onDestroy();
    }
}