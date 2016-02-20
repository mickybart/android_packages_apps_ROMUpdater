package com.cyanogenmod.updater;

import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.cyanogenmod.updater.misc.FlashableZip;
import com.cyanogenmod.updater.utils.FileUtils;
import com.cyanogenmod.updater.utils.Utils;

public class ZipInstallSettings extends Activity implements View.OnClickListener {
    private static final String TAG = "ZipInstallSettings";
    private static final int REQUEST_GET_FILE = 1001;
    
    private SharedPreferences mPrefs;
    
    private List<FlashableZip> mZipList;
    private FlashableZip mZip;
    int mIndex;
    
    private TextView mZipFileNameText;
    private ImageView mOpenFile;
    private ImageView mUpButton;
    private ImageView mDownButton;
    private View mOKButton;
    
    private EditText mBeforeInstall;
    private EditText mAfterInstall;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.zip_install_settings);
        
        mIndex = -1;
        try {
            mPrefs = PreferenceManager.getDefaultSharedPreferences(this); 
            mZipList = Utils.loadZipFiles(mPrefs);
                
            Bundle extras = getIntent().getExtras();
            mIndex = extras.getInt("index");
            mZip = mZipList.get(mIndex);
            
            mZipFileNameText = (TextView)findViewById(R.id.zipFileName);
            mZipFileNameText.setText(mZip.getFileName());
            
            mOpenFile = (ImageView)findViewById(R.id.openFile);
            mUpButton = (ImageView)findViewById(R.id.upButton);
            mDownButton = (ImageView)findViewById(R.id.downButton);
            mOKButton = findViewById(R.id.okButton);
            
            mOpenFile.setOnClickListener(this);
            mUpButton.setOnClickListener(this);
            mDownButton.setOnClickListener(this);
            mOKButton.setOnClickListener(this);
            enableDisableButtons();
            
            mBeforeInstall = (EditText)findViewById(R.id.beforeInstall);
            mAfterInstall = (EditText)findViewById(R.id.afterInstall);
            mBeforeInstall.setText(mZip.getBeforeInstall());
            mAfterInstall.setText(mZip.getAfterInstall());
        } catch (Exception e) {
            Log.e(TAG, "Error starting InstallZipSettings", e);
            finish();
        }
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (MotionEvent.ACTION_OUTSIDE == event.getAction()) {
            return true;
        }
        // Delegate everything else to Activity.
        return super.onTouchEvent(event);
    }

    @Override
    public void onClick(View v) {
        try {
            if (v == mOpenFile) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("file/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.addCategory(Intent.CATEGORY_DEFAULT);
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip"});
                startActivityForResult(intent, REQUEST_GET_FILE);
            } else if (v == mUpButton) {
                if (mIndex > 0) {
                    mIndex--;
                    mZipList.set(mIndex+1, mZipList.get(mIndex));
                    mZipList.set(mIndex, mZip);
                    enableDisableButtons();
                    Utils.storeZipFiles(mPrefs, mZipList);
                }
            } else if (v == mDownButton) {
                if (mIndex < mZipList.size() - 1) {
                    mIndex++;
                    mZipList.set(mIndex-1, mZipList.get(mIndex));
                    mZipList.set(mIndex, mZip);
                    enableDisableButtons();
                    Utils.storeZipFiles(mPrefs, mZipList);
                }
            } else if (v == mOKButton) {
                finish();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling onClick", e);
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        // Check which request we're responding to
        if (requestCode == REQUEST_GET_FILE && resultCode == RESULT_OK) {
            try {
                Uri uri = intent.getData();
                String filePath = FileUtils.getPath(this, uri);
                mZip.setFilePath(filePath);
                mZipFileNameText.setText(mZip.getFileName());
                Utils.storeZipFiles(mPrefs, mZipList);
            } catch (Exception e) {
                Log.e(TAG, "Error updating zip context", e);
            }
        }
    }
    
    private void enableDisableButtons() {
        mUpButton.setImageResource((mIndex > 0) ? R.drawable.ic_up : R.drawable.ic_up_disabled);
        mDownButton.setImageResource((mIndex < mZipList.size() - 1) ? R.drawable.ic_down : R.drawable.ic_down_disabled);
    }
    
    @Override
    public void finish() {
        try {
            mZip.setBeforeInstall(mBeforeInstall.getText().toString().trim());
            mZip.setAfterInstall(mAfterInstall.getText().toString().trim());
            Utils.storeZipFiles(mPrefs, mZipList);
        } catch (Exception e) {
            Log.e(TAG, "Error updating zip context", e);
        }
        super.finish();
    }
}
