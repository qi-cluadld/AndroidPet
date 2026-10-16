package com.qi.desktoppets;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private TextView stOverlay, stNotif;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (getResources().getDisplayMetrics().density * 20);
        root.setPadding(pad, pad, pad, pad);

        TextView t = new TextView(this);
        t.setText(R.string.title);
        t.setTextSize(22);
        root.addView(t);

        TextView s = new TextView(this);
        s.setText(R.string.subtitle);
        s.setTextSize(14);
        root.addView(s);

        stOverlay = new TextView(this);
        stOverlay.setTextSize(14);
        root.addView(stOverlay);

        stNotif = new TextView(this);
        stNotif.setTextSize(14);
        root.addView(stNotif);

        root.addView(btn(R.string.btn_overlay, v -> askOverlay()));
        root.addView(btn(R.string.btn_notif, v -> askNotif()));
        root.addView(btn(R.string.btn_battery, v -> askBattery()));
        root.addView(btn(R.string.btn_start, v -> startPets()));
        root.addView(btn(R.string.btn_stop, v -> stopPets()));

        TextView tip = new TextView(this);
        tip.setText(R.string.tip);
        tip.setTextSize(13);
        tip.setPadding(0, pad / 2, 0, 0);
        root.addView(tip);

        setContentView(root);
    }

    private Button btn(int textId, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(textId);
        b.setOnClickListener(l);
        return b;
    }

    private void askOverlay() {
        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        try { startActivity(i); } catch (Exception e) { toast(e.getMessage()); }
    }

    private void askNotif() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[] { "android.permission.POST_NOTIFICATIONS" }, 1);
        } else {
            toast("Android 13 以下不需要单独授权");
        }
    }

    private void askBattery() {
        Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + getPackageName()));
        try { startActivity(i); } catch (Exception e) { toast(e.getMessage()); }
    }

    private void startPets() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            toast(getString(R.string.toast_no_overlay));
            askOverlay();
            return;
        }
        Intent i = new Intent(this, PetService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        toast(getString(R.string.toast_started));
    }

    private void stopPets() {
        stopService(new Intent(this, PetService.class));
        toast(getString(R.string.toast_stopped));
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean ov = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
        stOverlay.setText(ov ? R.string.status_overlay_ok : R.string.status_overlay_no);
    }
}