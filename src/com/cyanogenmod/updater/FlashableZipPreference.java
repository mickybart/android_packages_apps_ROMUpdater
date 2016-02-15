package com.cyanogenmod.updater;

import android.content.Context;
import android.preference.Preference;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

public class FlashableZipPreference extends Preference {
	
	public interface OnActionListener {
		void onZipRemove(FlashableZipPreference pref);
	}
	
	private OnActionListener mOnActionListener;
	
	private String filePath;
	private String fileName;
	private TextView mTitleText;
	private ImageView mCancelButton;
	
	public FlashableZipPreference(Context context, String filePath) {
        super(context, null, R.style.FlashableZipPreferenceStyle);
        setLayoutResource(R.layout.preference_flashable_zip);
        this.filePath = filePath;
        this.fileName = filePath.substring(filePath.lastIndexOf('/')+1);
    }
	
	@Override
    protected void onBindView(View view) {
        super.onBindView(view);
        
        mTitleText = (TextView)view.findViewById(android.R.id.title);
        mCancelButton = (ImageView)view.findViewById(R.id.cancel_button);
        
        mTitleText.setText(fileName);
        mTitleText.setVisibility(View.VISIBLE);
        mCancelButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mOnActionListener != null) {
					mOnActionListener.onZipRemove(FlashableZipPreference.this);
				}
			}
		});
	}
	
	public void setOnActionListener(OnActionListener listener) {
        mOnActionListener = listener;
    }
	
	public String getFilePath() {
		return filePath;
	}
	
	public String getFileName() {
		return fileName;
	}
}
