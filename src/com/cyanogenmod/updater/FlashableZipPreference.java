package com.cyanogenmod.updater;

import android.content.Context;
import android.preference.Preference;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.cyanogenmod.updater.misc.FlashableZip;

public class FlashableZipPreference extends Preference {
    public interface OnActionListener {
        void onZipRemove(FlashableZipPreference pref);
        void onZipEdit(FlashableZipPreference pref);
    }

    private OnActionListener mOnActionListener;

    private int index;
    private FlashableZip flashableZip;

    private TextView mTitleText;
    private ImageView mCancelButton;

    public FlashableZipPreference(Context context, int index, FlashableZip flashableZip) {
        super(context, null, R.style.FlashableZipPreferenceStyle);
        setLayoutResource(R.layout.preference_flashable_zip);

        this.index = index;
        this.flashableZip = flashableZip;
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);

        mTitleText = (TextView)view.findViewById(android.R.id.title);
        mCancelButton = (ImageView)view.findViewById(R.id.cancel_button);

        mTitleText.setText(flashableZip.getFileName());
        mTitleText.setVisibility(View.VISIBLE);

        mTitleText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mOnActionListener != null) {
                    mOnActionListener.onZipEdit(FlashableZipPreference.this);
                }
            }
        });
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

    public int getIndex() {
        return index;
    }

    public FlashableZip getFlashableZip() {
        return flashableZip;
    }
}

