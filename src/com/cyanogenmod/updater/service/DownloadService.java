/*
 * Copyright (C) 2012 The CyanogenMod Project
 * Copyright (C) 2016 nAOSProm
 *
 * * Licensed under the GNU GPLv2 license
 *
 * The text of the license can be found in the LICENSE file
 * or at https://www.gnu.org/licenses/gpl-2.0.txt
 */

package com.cyanogenmod.updater.service;

import android.app.DownloadManager;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Parcelable;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;

import com.cyanogenmod.updater.R;
import com.cyanogenmod.updater.UpdateApplication;
import com.cyanogenmod.updater.misc.Constants;
import com.cyanogenmod.updater.misc.UpdateInfo;
import com.cyanogenmod.updater.receiver.DownloadReceiver;
import com.cyanogenmod.updater.requests.UpdatesJsonObjectRequest;
import com.cyanogenmod.updater.utils.Utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URI;

public class DownloadService extends IntentService
        implements Response.ErrorListener {
    private static final String TAG = DownloadService.class.getSimpleName();

    private static final String EXTRA_UPDATE_INFO = "update_info";

    private SharedPreferences mPrefs;
    private UpdateInfo mInfo = null;

    public static void start(Context context, UpdateInfo ui) {
        Intent intent = new Intent(context, DownloadService.class);
        intent.putExtra(EXTRA_UPDATE_INFO, (Parcelable) ui);
        context.startService(intent);
    }

    public DownloadService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mInfo = intent.getParcelableExtra(EXTRA_UPDATE_INFO);

        if (mInfo == null) {
            Log.e(TAG, "Intent UpdateInfo extras were null");
            return;
        }

        if(getResources().getBoolean(R.bool.conf_support_incremental)) {
            try {
                getIncremental();
            } catch (IOException e) {
                downloadFullZip();
            }
        } else {
            downloadFullZip();
        }
    }

    private void getIncremental() throws IOException {
        String sourceIncremental = Utils.getIncremental();
        Log.d(TAG, "Looking for incremental ota for source=" + sourceIncremental + ", target="
                + mInfo.getIncremental());

        Request request = null;

        String withMethod = getString(R.string.conf_request_with_method);
        if (withMethod.equals("cmrest")) {
            request = getIncrementalRequestWithCMrest(sourceIncremental);
        } else {
            Log.e(TAG, "No valid method to get incremental updates. please check conf_request_with_method");
            return;
        }

        if (request == null)
            return;

        ((UpdateApplication) getApplicationContext()).getQueue().add(request);
    }

    private String getServerUri() {
        String propertyUri = SystemProperties.get("persist.rom.updater.uri");
        if (!TextUtils.isEmpty(propertyUri)) {
            return propertyUri;
        }

        return getString(R.string.conf_update_server_url_def);
    }

    private Request getIncrementalRequestWithCMrest(String sourceIncremental) {
        UpdatesJsonObjectRequest request;

        URI requestUri = URI.create(getServerUri() + "/v1/build/get_delta");

        // Set request body
        try {
            JSONObject body = new JSONObject();
            body.put("source_incremental", sourceIncremental);
            body.put("target_incremental", mInfo.getIncremental());

            request = new UpdatesJsonObjectRequest(requestUri.toASCIIString(),
                    Utils.getUserAgentString(this), body,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            VolleyLog.v("Response:%n %s", response);
                            UpdateInfo incrementalUpdateInfo = null;
                    
                            incrementalUpdateInfo = jsonToInfo(response);
                    
                            if (incrementalUpdateInfo == null) {
                                downloadFullZip();
                            } else {
                                downloadIncremental(incrementalUpdateInfo);
                            }
                        }
                    },
                    this);

        } catch (JSONException e) {
            Log.e(TAG, "JSONException", e);
            return null;
        }

        return request;
    }

    private UpdateInfo jsonToInfo(JSONObject obj) {
        try {
            if (obj == null || obj.has("errors")) {
                return null;
            }

            return new UpdateInfo.Builder()
                    .setFileName(obj.getString("filename"))
                    .setDownloadUrl(obj.getString("download_url"))
                    .setMD5Sum(obj.getString("md5sum"))
                    .setApiLevel(mInfo.getApiLevel())
                    .setBuildDate(obj.getLong("date_created_unix"))
                    .setType(UpdateInfo.Type.INCREMENTAL)
                    .setIncremental(obj.getString("incremental"))
                    .setWipeCache(obj.has("wipe_cache") ? obj.getBoolean("wipe_cache") : true)
                    .setPostFlash(obj.has("post_flash") ? obj.getBoolean("post_flash") : true)
                    .setDirectDownload(obj.has("direct_download") ? obj.getBoolean("direct_download") : true)
                    .setDepends(obj.has("depends") ? obj.getString("depends") : null)
                    .build();
        } catch (JSONException e) {
            Log.e(TAG, "JSONException", e);
            return null;
        }
    }

    private long enqueueDownload(String downloadUrl, String localFilePath) {
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
        String userAgent = Utils.getUserAgentString(this);
        if (userAgent != null) {
            request.addRequestHeader("User-Agent", userAgent);
        }
        request.setTitle(getString(R.string.app_name));
        request.setDestinationUri(Uri.parse(localFilePath));
        request.setAllowedOverRoaming(false);
        request.setVisibleInDownloadsUi(false);

        // TODO: this could/should be made configurable
        request.setAllowedOverMetered(true);

        final DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        return dm.enqueue(request);
    }

    private void downloadIncremental(UpdateInfo incrementalUpdateInfo) {
        Log.v(TAG, "Downloading incremental zip: " + incrementalUpdateInfo.getDownloadUrl());
        // Build the name of the file to download, adding .partial at the end.  It will get
        // stripped off when the download completes
        String sourceIncremental = Utils.getIncremental();
        String targetIncremental = mInfo.getIncremental();
        String fileName = "incremental-" + sourceIncremental + "-" + targetIncremental + ".zip";
        String incrementalFilePath = "file://" + getUpdateDirectory().getAbsolutePath() + "/" + fileName + ".partial";

        long downloadId = enqueueDownload(incrementalUpdateInfo.getDownloadUrl(), incrementalFilePath);

        // Store in shared preferences
        mPrefs.edit()
                .putLong(Constants.DOWNLOAD_ID, downloadId)
                .putString(Constants.DOWNLOAD_MD5, incrementalUpdateInfo.getMD5Sum())
                .putString(Constants.DOWNLOAD_INCREMENTAL_FOR, mInfo.getFileName())
                .apply();

        Utils.cancelNotification(this);

        Intent intent = new Intent(DownloadReceiver.ACTION_DOWNLOAD_STARTED);
        intent.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, downloadId);
        sendBroadcast(intent);
    }

    private void downloadFullZip() {
        Log.v(TAG, "Downloading full zip");

        // Build the name of the file to download, adding .partial at the end.  It will get
        // stripped off when the download completes
        String fullFilePath = "file://" + getUpdateDirectory().getAbsolutePath() +
                "/" + mInfo.getFileName() + ".partial";

        long downloadId = enqueueDownload(mInfo.getDownloadUrl(), fullFilePath);

        // Store in shared preferences
        mPrefs.edit()
                .putLong(Constants.DOWNLOAD_ID, downloadId)
                .putString(Constants.DOWNLOAD_MD5, mInfo.getMD5Sum())
                .apply();

        Utils.cancelNotification(this);

        Intent intent = new Intent(DownloadReceiver.ACTION_DOWNLOAD_STARTED);
        intent.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, downloadId);
        sendBroadcast(intent);
    }

    private File getUpdateDirectory() {
        // If directory doesn't exist, create it
        File directory = Utils.makeUpdateFolder();
        if (!directory.exists()) {
            directory.mkdirs();
            Log.d(TAG, "UpdateFolder created");
        }

        return directory;
    }

    @Override
    public void onErrorResponse(VolleyError error) {
        VolleyLog.e("Error: ", error.getMessage());
    }
}
