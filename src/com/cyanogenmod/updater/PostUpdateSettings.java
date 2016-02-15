package com.cyanogenmod.updater;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.cyanogenmod.updater.misc.Constants;
import com.cyanogenmod.updater.utils.FileUtils;
import com.cyanogenmod.updater.utils.Utils;

public class PostUpdateSettings extends PreferenceActivity  implements FlashableZipPreference.OnActionListener {
    private static String TAG = "PostUpdateSettings";
    
    public static final String KEY_FLASHABLE_ZIP_FILES = "flashable_zip_files";
    public static final String KEY_ADD_ZIP = "add_zip";
    
    private static final int REQUEST_GET_ZIP = 1001;
    
    private SharedPreferences mPrefs;    
    private PreferenceCategory mZipFiles;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        addPreferencesFromResource(R.xml.post_update);
        
        final ActionBar bar = getActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
        }
        
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mZipFiles = (PreferenceCategory) findPreference(KEY_FLASHABLE_ZIP_FILES);
        
        restoreZipFilesPreference();
    }
    
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (KEY_ADD_ZIP.equals(preference.getKey())) {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("file/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip"});
            startActivityForResult(intent, REQUEST_GET_ZIP);
            return true;
        } 
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQUEST_GET_ZIP && resultCode == RESULT_OK) {
            Uri uri = intent.getData();
            String filePath = FileUtils.getPath(this, uri);
            FlashableZipPreference flashableZipPreference = new FlashableZipPreference(this, filePath);
            flashableZipPreference.setOnActionListener(this);
            mZipFiles.addPreference(flashableZipPreference);
            updateZipFilesSharedPreference();
        }
    }

    @Override
    public void onZipRemove(final FlashableZipPreference pref) {
        String message = String.format(getResources().getString(R.string.remove_zip_file), 
                                       pref.getFileName());
        new AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    mZipFiles.removePreference(pref);
                    updateZipFilesSharedPreference();
                }})
            .setNegativeButton(android.R.string.no, null).show();
    }

    private void updateZipFilesSharedPreference() {
        int cnt = mZipFiles.getPreferenceCount();
        StringBuilder sb = new StringBuilder();
        for(int i=0; i<cnt; i++) {
            Preference pref = mZipFiles.getPreference(i);
            if (pref instanceof FlashableZipPreference) {
                sb.append((sb.length() > 0)? "|": "");
                sb.append(((FlashableZipPreference)pref).getFilePath());
            }
        }
        mPrefs.edit().putString(Constants.ZIP_FILES_PREF, sb.toString()).apply();;
    }

    private void restoreZipFilesPreference() {
        mZipFiles.removeAll();

        String zipFiles = mPrefs.getString(Constants.ZIP_FILES_PREF, null);
        if (zipFiles != null && !zipFiles.isEmpty()) {
            String [] filePaths =  zipFiles.split("\\|");
            for(String filePath: filePaths) {
                FlashableZipPreference flashableZipPreference = new FlashableZipPreference(this, filePath);
                flashableZipPreference.setOnActionListener(this);
                mZipFiles.addPreference(flashableZipPreference);
            }
        }
    }
}

