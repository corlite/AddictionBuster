package com.addictionbuster;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class AppCatalog {
    private AppCatalog() {
    }

    static List<AppInfo> loadLaunchableApps(Context context) {
        PackageManager packageManager = context.getPackageManager();
        Intent launchIntent = new Intent(Intent.ACTION_MAIN);
        launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolved;
        if (Build.VERSION.SDK_INT >= 33) {
            resolved = packageManager.queryIntentActivities(
                    launchIntent,
                    PackageManager.ResolveInfoFlags.of(0)
            );
        } else {
            resolved = packageManager.queryIntentActivities(launchIntent, 0);
        }

        List<AppInfo> apps = new ArrayList<>();
        for (ResolveInfo info : resolved) {
            if (info.activityInfo == null || info.activityInfo.packageName == null) {
                continue;
            }
            String packageName = info.activityInfo.packageName;
            if (context.getPackageName().equals(packageName)) {
                continue;
            }
            CharSequence label = info.loadLabel(packageManager);
            apps.add(new AppInfo(label == null ? packageName : label.toString(), packageName));
        }

        Collator collator = Collator.getInstance(Locale.CHINA);
        apps.sort((a, b) -> collator.compare(a.label, b.label));
        return apps;
    }

    static String loadLabel(Context context, String packageName) {
        PackageManager packageManager = context.getPackageManager();
        try {
            return packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(packageName, 0)
            ).toString();
        } catch (PackageManager.NameNotFoundException ignored) {
            return packageName;
        }
    }

    static Drawable loadIcon(Context context, String packageName) {
        PackageManager packageManager = context.getPackageManager();
        try {
            return packageManager.getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException ignored) {
            return packageManager.getDefaultActivityIcon();
        }
    }
}
