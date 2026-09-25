package br.com.fluxo.fixture;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Base64;
import java.nio.charset.StandardCharsets;

/** Emulator-only fixture, protected by a shell-level permission. No network or bank SDK. */
public final class EmitterReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!("ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE))) {
            setResultCode(2);
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        String action = intent.getStringExtra("operation");
        int id = intent.getIntExtra("id", 1);
        if ("cancelAll".equals(action)) {
            manager.cancelAll();
        } else if ("cancel".equals(action)) {
            manager.cancel("fluxo-synthetic", id);
        } else if ("post".equals(action)) {
            manager.createNotificationChannel(new NotificationChannel("synthetic", "Synthetic only",
                NotificationManager.IMPORTANCE_DEFAULT));
            if (!manager.areNotificationsEnabled()) { setResultCode(3); return; }
            Notification.Builder builder = new Notification.Builder(context, "synthetic")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("")
                .setContentText(decode(intent, "text64"))
                .setWhen(intent.getLongExtra("when", System.currentTimeMillis()))
                .setOnlyAlertOnce(true);
            if (intent.hasExtra("big64")) {
                builder.setStyle(new Notification.BigTextStyle().bigText(decode(intent, "big64")));
            }
            if (intent.getBooleanExtra("summary", false)) {
                builder.setGroup("synthetic-group").setGroupSummary(true);
            }
            manager.notify("fluxo-synthetic", id, builder.build());
        } else { setResultCode(4); return; }
        setResultCode(1);
    }
    private static String decode(Intent intent, String key) {
        String value = intent.getStringExtra(key);
        return value == null ? "" : new String(Base64.decode(value, Base64.NO_WRAP), StandardCharsets.UTF_8);
    }
}
