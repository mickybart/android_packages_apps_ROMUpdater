/*
 * Copyright (C) 2013 The CyanogenMod Project
 * Copyright (C) 2016 nAOSProm
 *
 * * Licensed under the GNU GPLv2 license
 *
 * The text of the license can be found in the LICENSE file
 * or at https://www.gnu.org/licenses/gpl-2.0.txt
 */

package com.cyanogenmod.updater.utils;

import java.util.ArrayList;
import java.util.List;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.cyanogenmod.updater.R;
import com.cyanogenmod.updater.misc.Constants;
import com.cyanogenmod.updater.misc.FlashableZip;
import com.cyanogenmod.updater.service.UpdateCheckService;
import com.cyanogenmod.updater.misc.UpdateInfo;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import org.json.JSONArray;
import org.json.JSONException;

public class Utils {
    private Utils() {
        // this class is not supposed to be instantiated
    }

    public static File makeUpdateFolder() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    }
    
    public static String getRecoveryPath(String primaryStoragePath) {
        if (Environment.isExternalStorageEmulated())
            // data media rewrite the path to bypass the sd card fuse layer and trigger uncrypt
            return Environment.maybeTranslateEmulatedPathToInternal(
                new File(primaryStoragePath)).getAbsolutePath();
        else
            return primaryStoragePath;
    }

    public static void cancelNotification(Context context) {
        final NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(R.string.not_new_updates_found_title);
        nm.cancel(R.string.not_download_success);
    }

    public static String getDeviceType() {
        return SystemProperties.get("ro.product.device");
    }

    public static String getInstalledVersion() {
        String version;

        version = SystemProperties.get("ro.cm.version", "");
        if (!version.isEmpty())
            return version;

        version = SystemProperties.get("ro.build.version.updater", "");
        if (!version.isEmpty())
            return version;

        return "";
    }

    public static String getInstalledZipFile() {
        String version = SystemProperties.get("ro.cm.version", "");

        if (!version.isEmpty()) {
            // Should be a CyanogenMod version
            return "cm-" + version + ".zip";
        }

        return SystemProperties.get("ro.build.version.updater", "unknown") + ".zip";
    }

    public static int getInstalledApiLevel() {
        return SystemProperties.getInt("ro.build.version.sdk", 0);
    }

    public static long getInstalledBuildDate() {
        return SystemProperties.getLong("ro.build.date.utc", 0);
    }

    public static String getIncremental() {
        return SystemProperties.get("ro.build.version.incremental");
    }

    public static String getUserAgentString(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
            return pi.packageName + "/" + pi.versionName;
        } catch (PackageManager.NameNotFoundException nnfe) {
            return null;
        }
    }

    public static boolean isOnline(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo != null && netInfo.isConnected()) {
            return true;
        }
        return false;
    }

    public static void scheduleUpdateService(Context context, int updateFrequency) {
        // Load the required settings from preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        long lastCheck = prefs.getLong(Constants.LAST_UPDATE_CHECK_PREF, 0);

        // Get the intent ready
        Intent i = new Intent(context, UpdateCheckService.class);
        i.setAction(UpdateCheckService.ACTION_CHECK);
        PendingIntent pi = PendingIntent.getService(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);

        // Clear any old alarms and schedule the new alarm
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pi);

        if (updateFrequency != Constants.UPDATE_FREQ_NONE) {
            am.setRepeating(AlarmManager.RTC_WAKEUP, lastCheck + updateFrequency, updateFrequency, pi);
        }
    }

    public static void triggerUpdate(Context context, UpdateInfo ui) throws IOException {
        /* 
         * Should perform the following steps
         * 1.- check recovery functionality
         * 2.- prepare recovery script or command line
         * 3.- reboot recovery
         */

        /* OpenRecoveryScript */
        boolean isUseOpenRecoveryScript = context.getResources().getBoolean(R.bool.conf_use_openrecoveryscript);

        /* Define update path */
        String updatePath = Utils.getRecoveryPath(Utils.makeUpdateFolder().getAbsolutePath());
        String zipPath = updatePath + "/" + ui.getFileName();

        /* Backup, Custom Recovery ... */
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean needBackup = prefs.getBoolean(Constants.BACKUP_PREF, true);
        String customRecovery = getPostUpdateScript(prefs);

        /* Generate the script */

        if (isUseOpenRecoveryScript) {
            updateWithOpenRecoveryScript(zipPath, ui, needBackup, customRecovery);
        } else {
            updateByDefault(zipPath, needBackup);
        }

        // Trigger the reboot
        SystemProperties.set(Constants.REBOOT_FLASH_PROPERTY, "true");
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        powerManager.reboot("recovery");
    }

    private static void updateByDefault(String zipPath, boolean needBackup) throws IOException {
        /*
         * Should perform the following steps.
         * 1.- mkdir -p /cache/recovery
         * 2.- echo 'boot-recovery' > /cache/recovery/command
         * 3.- if(mBackup) echo '--nandroid'  >> /cache/recovery/command
         * 4.- echo '--update_package=SDCARD:update.zip' >> /cache/recovery/command
         */

        // Set the 'boot recovery' command
        Process p = Runtime.getRuntime().exec("sh");
        OutputStream os = p.getOutputStream();
        os.write("mkdir -p /cache/recovery/\n".getBytes());
        os.write("echo 'boot-recovery' >/cache/recovery/command\n".getBytes());

        // See if backups are enabled and add the nandroid flag
        /* TODO: add this back once we have a way of doing backups that is not recovery specific
           if (needBackup) {
           os.write("echo '--nandroid'  >> /cache/recovery/command\n".getBytes());
           }
           */

        String cmd = "echo '--update_package=" + zipPath + "' >> /cache/recovery/command\n";
        os.write(cmd.getBytes());
        os.flush();
    }

    private static void updateWithOpenRecoveryScript(String zipPath, UpdateInfo ui, boolean needBackup, String customRecovery) throws IOException {
        /*
         * Should perform the following steps.
         * 1.- mkdir -p /cache/recovery
         * 2.- if(mBackup) backup
         * 3.- install zip
         * 4.- wipe cache and dalvik
         * 5.- if(custom instruction) perform custom instructions
         */

        // Set the 'boot recovery' command
        Process p = Runtime.getRuntime().exec("sh");
        OutputStream os = p.getOutputStream();
        os.write("mkdir -p /cache/recovery/\n".getBytes());
        os.write("> /cache/recovery/openrecoveryscript\n".getBytes());

        if (needBackup) {
           os.write(("echo 'backup SDB before-" + ui.getFileName() + "'  >> /cache/recovery/openrecoveryscript\n").getBytes());
        }

        os.write(("echo 'install " + zipPath + "' >> /cache/recovery/openrecoveryscript\n").getBytes());

        if (ui.getWipeCache()) {
            os.write(("echo 'wipe cache' >> /cache/recovery/openrecoveryscript\n").getBytes());
            os.write(("echo 'wipe dalvik' >> /cache/recovery/openrecoveryscript\n").getBytes());
        }

        /* 
         * Support custom commands to flash for example :
         * SuperSU, GApps ...
         */
        if (ui.getPostFlash() && customRecovery != null && !customRecovery.isEmpty()) {
            os.write(("echo '" + customRecovery + "' >> /cache/recovery/openrecoveryscript\n").getBytes());
        }

        os.flush();
    }

    public static int getUpdateType() {
        int updateType = Constants.UPDATE_TYPE_NIGHTLY;
        try {
            String releaseType = SystemProperties.get("ro.cm.releasetype", "");
            if (releaseType.isEmpty())
                releaseType = SystemProperties.get("ro.build.version.channel", "");

            // Treat anything that is not SNAPSHOT as NIGHTLY
            if (!releaseType.isEmpty()) {
                if (TextUtils.equals(releaseType,
                        Constants.RELEASETYPE_SNAPSHOT)) {
                    updateType = Constants.UPDATE_TYPE_SNAPSHOT;
                }
            }
        } catch (RuntimeException ignored) {
        }

        return updateType;
    }

    public static boolean hasLeanback(Context context) {
        PackageManager packageManager = context.getPackageManager();
        return packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }

    public static String getPostUpdateScript(SharedPreferences prefs) {
        StringBuilder sb = new StringBuilder();
        try {
            List<FlashableZip> zipFiles = loadZipFiles(prefs);
            for(FlashableZip zipFile: zipFiles) {
                if (!zipFile.getBeforeInstall().isEmpty()) {
                    sb.append(zipFile.getBeforeInstall()).append('\n');
                }
                sb.append("install ").append(Utils.getRecoveryPath(zipFile.getFilePath())).append('\n');
                if (!zipFile.getAfterInstall().isEmpty()) {
                    sb.append(zipFile.getAfterInstall()).append('\n');
                }
            }
            sb.append(prefs.getString(Constants.CUSTOM_RECOVERY_PREF, ""));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

    public static void storeZipFiles(SharedPreferences prefs, List<FlashableZip> zipList) throws JSONException {
        JSONArray zipFilesArray = new JSONArray();
        for(FlashableZip zip: zipList) {
            zipFilesArray.put(zip.toJSON());
        }
        prefs.edit().putString(Constants.ZIP_FILES_PREF, zipFilesArray.toString()).apply();
    }

    public static List<FlashableZip> loadZipFiles(SharedPreferences prefs) throws JSONException {
        List<FlashableZip> zipList = new ArrayList<FlashableZip>();
        String zipFiles = prefs.getString(Constants.ZIP_FILES_PREF, null);
        if (zipFiles != null) {
            JSONArray zipFilesArray = new JSONArray(zipFiles);
            for(int i=0; i<zipFilesArray.length(); i++) {
                zipList.add(new FlashableZip(zipFilesArray.getJSONObject(i)));
            }
        }
        return zipList;
    }
}

