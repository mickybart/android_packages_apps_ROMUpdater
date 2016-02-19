package com.cyanogenmod.updater;

import java.util.ArrayList;
import java.util.List;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.util.TypedValue;
import android.widget.TextView;

import com.cyanogenmod.updater.misc.Constants;
import com.cyanogenmod.updater.misc.FlashableZip;
import com.cyanogenmod.updater.utils.FileUtils;
import com.cyanogenmod.updater.utils.Utils;

public class PostUpdateSettings extends PreferenceActivity  implements FlashableZipPreference.OnActionListener, OnSharedPreferenceChangeListener {
    private static final String TAG = "PostUpdateSettings";
    
    public static final String KEY_FLASHABLE_ZIP_FILES = "flashable_zip_files";
    public static final String KEY_ADD_ZIP = "add_zip";
    public static final String KEY_SCRIPT_PREVIEW = "script_preview";
    
    private static final int REQUEST_GET_ZIP = 1001;
    private static final int REQUEST_ZIP_SETTINGS = 1002;

    private SharedPreferences mPrefs;

    private PreferenceCategory mZipFiles;
    private List<FlashableZip> mZipList;

    boolean mUpdateZipFilesOnPrefChange = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        addPreferencesFromResource(R.xml.post_update);
        
        final ActionBar bar = getActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
        }
        
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mPrefs.registerOnSharedPreferenceChangeListener(this);

        mZipFiles = (PreferenceCategory) findPreference(KEY_FLASHABLE_ZIP_FILES);
        
        restoreZipFilesPreference();
    }
    
    @Override
    protected void onDestroy() {
        mPrefs.unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        Log.d(TAG, "onPreferenceTreeClick: " + preference.getKey() + ", " + preference.getTitle());

        String preferenceKey = preference.getKey();
        if (KEY_ADD_ZIP.equals(preferenceKey)) {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("file/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip"});
            startActivityForResult(intent, REQUEST_GET_ZIP);
            return true;
        } else if (KEY_SCRIPT_PREVIEW.equals(preferenceKey)) {
            previewPostUpdate();
            return true;
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQUEST_GET_ZIP && resultCode == RESULT_OK) {
            try {
                Uri uri = intent.getData();
                String filePath = FileUtils.getPath(this, uri);
                FlashableZip zip = new FlashableZip(filePath);
                mZipList.add(zip);
                FlashableZipPreference flashableZipPreference = new FlashableZipPreference(this, mZipList.size() - 1, zip);
                flashableZipPreference.setOnActionListener(this);
                mZipFiles.addPreference(flashableZipPreference);
                Utils.storeZipFiles(mPrefs, mZipList);
                displayZipSettings(flashableZipPreference);
            } catch (Exception e) {
                Log.e(TAG, "Error adding file", e);
            }
        } else if (requestCode == REQUEST_ZIP_SETTINGS) {
            Log.d(TAG, "Zip Settings finished");
            mUpdateZipFilesOnPrefChange = false;
        }
    }

    @Override
    public void onZipRemove(final FlashableZipPreference pref) {
        String message = String.format(getResources().getString(R.string.remove_zip_file), 
                                       pref.getFlashableZip().getFileName());
        new AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    mZipList.remove(pref.getIndex());
                    mZipFiles.removePreference(pref);
                    try {
                        Utils.storeZipFiles(mPrefs, mZipList);
                    } catch (Exception e) {
                        Log.e(TAG, "Error removing file", e);
                    }
                }})
            .setNegativeButton(android.R.string.no, null).show();
    }

    @Override
    public void onZipEdit(final FlashableZipPreference pref) {
        displayZipSettings(pref);
            }

    private void displayZipSettings(final FlashableZipPreference pref) {
        mUpdateZipFilesOnPrefChange = true;
        Log.d(TAG, "About to invoke Zip Settings");
        Intent intent = new Intent(this, ZipInstallSettings.class);
        intent.putExtra("index", pref.getIndex());
        startActivityForResult(intent, REQUEST_ZIP_SETTINGS);
    }

    private void restoreZipFilesPreference() {
        mZipFiles.removeAll();
        try {
            mZipList = Utils.loadZipFiles(mPrefs);
            for (int i = 0; i < mZipList.size(); i++) {
                FlashableZipPreference flashableZipPreference = new FlashableZipPreference(this, i, mZipList.get(i));
                flashableZipPreference.setOnActionListener(this);
                mZipFiles.addPreference(flashableZipPreference);
            }
        } catch (Exception e) {
            mZipList = new ArrayList<FlashableZip>();
            Log.e(TAG, "Error while restoring zip files", e);
        }
    }

    private void previewPostUpdate() {
        String script = Utils.getPostUpdateScript(mPrefs);
        script = script.replaceAll("\\n", "\n\n");
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.script_preview_dlg_title)
            .setMessage(script)
            .setPositiveButton(android.R.string.ok, null)
            .show();
        TextView textView = (TextView) dialog.findViewById(android.R.id.message);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        textView.setTypeface(Typeface.MONOSPACE);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (mUpdateZipFilesOnPrefChange && Constants.ZIP_FILES_PREF.equals(key)) {
            restoreZipFilesPreference();
        }
    }
}
