package com.igotu.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.List;

public class SentinelService extends Service implements LocationListener {

    public static final String ACTION_START = "ACTION_START";
    public static final String ACTION_STOP = "ACTION_STOP";
    public static final String ACTION_NEUTRALIZE = "ACTION_NEUTRALIZE";

    public static final String SILENT_CHANNEL_ID = "igotu_silent_monitor";
    public static final String ALERT_CHANNEL_ID = "igotu_active_alert";

    public static final int SERVICE_NOTIFICATION_ID = 2026;
    public static final int ALERT_NOTIFICATION_ID = 2027;

    private LocationManager locationManager;
    private NotificationManager notificationManager;
    private IncidentDbHelper dbHelper;

    private Location lastKnownRealLocation = null;
    private boolean isCurrentlyMocked = false;
    private long lastIncidentLogTime = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        dbHelper = IncidentDbHelper.getInstance(this);
        createChannels();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (ACTION_STOP.equals(intent.getAction())) {
                stopSelf();
                return START_NOT_STICKY;
            } else if (ACTION_NEUTRALIZE.equals(intent.getAction())) {
                AppNeutralizer.neutralize(this);
                AppNeutralizer.openDeveloperSettings(this);
            }
        }

        Notification silentNotification = buildSilentNotification();
        startForeground(SERVICE_NOTIFICATION_ID, silentNotification);

        // Neutralize on startup
        AppNeutralizer.neutralize(this);

        startMonitoring();
        return START_STICKY;
    }

    private void startMonitoring() {
        try {
            if (locationManager != null) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0, this);

                Location last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (last != null) onLocationChanged(last);
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        boolean isMock = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            isMock = location.isMock();
        } else {
            isMock = location.isFromMockProvider();
        }

        boolean hasHook = SecurityEngine.isHookFrameworkDetected(this);

        if (isMock || (hasHook && location.getAccuracy() == 0)) {
            isCurrentlyMocked = true;

            // Automatically attempt to dismantle fake GPS test providers and terminate background process
            AppNeutralizer.neutralize(this);

            long now = System.currentTimeMillis();
            if (now - lastIncidentLogTime > 30000) {
                lastIncidentLogTime = now;
                String activeApp = findFirstMockApp();
                String threat = isMock ? "Android Mock Location Injected" : "Stealth Root/Hook Spoof";

                double realLat = lastKnownRealLocation != null ? lastKnownRealLocation.getLatitude() : 0.0;
                double realLon = lastKnownRealLocation != null ? lastKnownRealLocation.getLongitude() : 0.0;

                dbHelper.logIncident(
                        location.getLatitude(),
                        location.getLongitude(),
                        realLat,
                        realLon,
                        location.getAccuracy(),
                        activeApp,
                        threat
                );
            }

            String alertMsg = String.format("Fake GPS active (%.4f, %.4f)! Tap to neutralize.",
                    location.getLatitude(), location.getLongitude());
            showFakeGpsAlert(alertMsg);
        } else {
            lastKnownRealLocation = new Location(location);

            if (isCurrentlyMocked) {
                isCurrentlyMocked = false;
                dismissFakeGpsAlert();
            }
        }
    }

    private String findFirstMockApp() {
        PackageManager pm = getPackageManager();
        try {
            List<PackageInfo> packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);
            for (PackageInfo pkg : packages) {
                boolean isSystem = (pkg.applicationInfo != null && (pkg.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
                if (isSystem) continue;

                if (pkg.requestedPermissions != null) {
                    for (String perm : pkg.requestedPermissions) {
                        if ("android.permission.ACCESS_MOCK_LOCATION".equals(perm)) {
                            return pkg.applicationInfo.loadLabel(pm).toString();
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return "Unknown Spoofer";
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel silentChannel = new NotificationChannel(
                    SILENT_CHANNEL_ID,
                    "IgotU Silent Guard",
                    NotificationManager.IMPORTANCE_MIN
            );
            silentChannel.setShowBadge(false);
            silentChannel.enableVibration(false);
            silentChannel.setSound(null, null);

            NotificationChannel alertChannel = new NotificationChannel(
                    ALERT_CHANNEL_ID,
                    "IgotU Fake GPS Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            alertChannel.enableVibration(true);
            alertChannel.setShowBadge(true);

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(silentChannel);
                notificationManager.createNotificationChannel(alertChannel);
            }
        }
    }

    private Notification buildSilentNotification() {
        Intent openAppIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, SILENT_CHANNEL_ID)
                .setContentTitle("IgotU Running")
                .setContentText("Background sensor protection active")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .build();
    }

    private void showFakeGpsAlert(String message) {
        Intent openAppIntent = new Intent(this, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        // Action intent to neutralize directly from notification
        Intent neutralizeIntent = new Intent(this, SentinelService.class);
        neutralizeIntent.setAction(ACTION_NEUTRALIZE);
        PendingIntent pNeutralize = PendingIntent.getService(
                this, 1, neutralizeIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification alertNotification = new NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setContentTitle("⚠️ ALERT: FAKE GPS ACTIVE!")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.ic_delete, "Disable Mock", pNeutralize)
                .setOngoing(false)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setColor(0xFFFF3366)
                .setColorized(true)
                .setVibrate(new long[]{0, 300, 150, 300})
                .build();

        if (notificationManager != null) {
            notificationManager.notify(ALERT_NOTIFICATION_ID, alertNotification);
        }
    }

    private void dismissFakeGpsAlert() {
        if (notificationManager != null) {
            notificationManager.cancel(ALERT_NOTIFICATION_ID);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        dismissFakeGpsAlert();
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
