package com.igotu.app;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class SecurityEngine {

    private static final String[] SU_PATHS = {
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
    };

    private static final String[] KNOWN_ROOT_PACKAGES = {
            "com.topjohnwu.magisk",
            "com.noshufou.android.su",
            "com.thirdparty.superuser",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.zachspong.temprootremovejb",
            "com.ramdroid.appquarantine"
    };

    private static final String[] KNOWN_HOOK_PACKAGES = {
            "org.meowcat.edxposed.manager",
            "de.robv.android.xposed.installer",
            "org.lsposed.manager"
    };

    /**
     * Checks if device is rooted (su binaries, test-keys, Magisk packages).
     */
    public static boolean isRooted(Context context) {
        return checkBuildTags() || checkSuFiles() || checkSuExecution() || checkRootPackages(context);
    }

    public static List<String> getRootThreats(Context context) {
        List<String> threats = new ArrayList<>();
        if (checkBuildTags()) threats.add("Test-Keys OS Build Detected");
        for (String path : SU_PATHS) {
            if (new File(path).exists()) {
                threats.add("SU Binary: " + path);
            }
        }
        if (checkSuExecution()) threats.add("Active 'su' Shell Execution Available");
        for (String pkg : KNOWN_ROOT_PACKAGES) {
            try {
                context.getPackageManager().getPackageInfo(pkg, 0);
                threats.add("Root Manager Installed: " + pkg);
            } catch (PackageManager.NameNotFoundException ignored) {}
        }
        return threats;
    }

    private static boolean checkBuildTags() {
        String buildTags = Build.TAGS;
        return buildTags != null && buildTags.contains("test-keys");
    }

    private static boolean checkSuFiles() {
        for (String path : SU_PATHS) {
            if (new File(path).exists()) return true;
        }
        return false;
    }

    private static boolean checkSuExecution() {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"/system/xbin/which", "su"});
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            return in.readLine() != null;
        } catch (Throwable t) {
            return false;
        } finally {
            if (process != null) process.destroy();
        }
    }

    private static boolean checkRootPackages(Context context) {
        PackageManager pm = context.getPackageManager();
        for (String pkg : KNOWN_ROOT_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0);
                return true;
            } catch (PackageManager.NameNotFoundException ignored) {}
        }
        return false;
    }

    /**
     * Checks for stealth hooking frameworks (Xposed, LSPosed, Frida, Cydia Substrate)
     * which are commonly used to hide mock location flags from apps.
     */
    public static boolean isHookFrameworkDetected(Context context) {
        return checkXposedClasses() || checkStackTraceHooks() || checkProcMapsForFrida() || checkHookPackages(context);
    }

    public static List<String> getHookThreats(Context context) {
        List<String> threats = new ArrayList<>();
        if (checkXposedClasses()) threats.add("Xposed/LSPosed Bridge Class Loaded");
        if (checkStackTraceHooks()) threats.add("Stack Trace Hook Interception Detected");
        if (checkProcMapsForFrida()) threats.add("Frida Dynamic Instrumentation Active");
        for (String pkg : KNOWN_HOOK_PACKAGES) {
            try {
                context.getPackageManager().getPackageInfo(pkg, 0);
                threats.add("Hook Manager App Installed: " + pkg);
            } catch (PackageManager.NameNotFoundException ignored) {}
        }
        return threats;
    }

    private static boolean checkXposedClasses() {
        try {
            ClassLoader.getSystemClassLoader().loadClass("de.robv.android.xposed.XposedBridge");
            return true;
        } catch (ClassNotFoundException ignored) {}

        try {
            ClassLoader.getSystemClassLoader().loadClass("org.lsposed.lspd.core.Lspd");
            return true;
        } catch (ClassNotFoundException ignored) {}

        return false;
    }

    private static boolean checkStackTraceHooks() {
        try {
            throw new Exception("Hook Check");
        } catch (Exception e) {
            for (StackTraceElement element : e.getStackTrace()) {
                String className = element.getClassName();
                if (className.contains("de.robv.android.xposed") ||
                    className.contains("me.weishu.exposed") ||
                    className.contains("com.saurik.substrate")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean checkProcMapsForFrida() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/self/maps"));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.toLowerCase().contains("frida") || line.toLowerCase().contains("gadget")) {
                    reader.close();
                    return true;
                }
            }
            reader.close();
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean checkHookPackages(Context context) {
        PackageManager pm = context.getPackageManager();
        for (String pkg : KNOWN_HOOK_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0);
                return true;
            } catch (PackageManager.NameNotFoundException ignored) {}
        }
        return false;
    }
}
