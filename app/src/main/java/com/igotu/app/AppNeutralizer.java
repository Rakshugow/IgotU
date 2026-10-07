package com.igotu.app;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

public class AppNeutralizer {

    public static class NeutralizeResult {
        public int killedCount = 0;
        public List<String> killedApps = new ArrayList<>();
        public boolean providersCleared = false;
        public String lastPackage = null;
    }

    /**
     * Actively neutralizes any running fake GPS apps and dismantles injected test providers.
     */
    public static NeutralizeResult neutralize(Context context) {
        NeutralizeResult result = new NeutralizeResult();

        // 1. Terminate background processes of all known mock location apps
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        PackageManager pm = context.getPackageManager();

        try {
            List<PackageInfo> packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);
            for (PackageInfo pkg : packages) {
                boolean isSystem = (pkg.applicationInfo != null && (pkg.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
                if (isSystem || pkg.packageName.equals(context.getPackageName())) continue;

                if (pkg.requestedPermissions != null) {
                    for (String perm : pkg.requestedPermissions) {
                        if ("android.permission.ACCESS_MOCK_LOCATION".equals(perm)) {
                            String appName = pkg.applicationInfo.loadLabel(pm).toString();
                            if (am != null) {
                                am.killBackgroundProcesses(pkg.packageName);
                                result.killedCount++;
                                result.killedApps.add(appName);
                                result.lastPackage = pkg.packageName;
                            }
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 2. Remove injected test providers from Android LocationManager
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm != null) {
            String[] testProviders = {
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER,
                    "fused",
                    "test",
                    "mock"
            };

            for (String provider : testProviders) {
                try {
                    lm.removeTestProvider(provider);
                    result.providersCleared = true;
                } catch (Exception ignored) {
                    // Test provider was not active or not permitted
                }
            }
        }

        return result;
    }

    /**
     * Opens Developer Options directly to disable Mock Location.
     */
    public static void openDeveloperSettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            openAppSettings(context, context.getPackageName());
        }
    }

    /**
     * Opens App Info page of the specific Fake GPS app to Force Stop it.
     */
    public static void openAppSettings(Context context, String packageName) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception ignored) {}
    }
}
