/*
 * SPDX-FileCopyrightText: 2014 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2017-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.audiofx.knobs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.media.AudioDeviceInfo;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.lineageos.audiofx.R;
import org.lineageos.audiofx.activity.MasterConfigControl;
import org.lineageos.audiofx.activity.StateCallbacks;

public class KnobContainer extends LinearLayout
        implements StateCallbacks.DeviceChangedCallback {

    private static final String TAG = KnobContainer.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static final int NOTIFY_DISABLE_DELAY = 5000;

    private static final int MSG_EXPAND = 0;
    private static final int MSG_CONTRACT = 1;

    private ViewGroup mBassContainer;
    private ViewGroup mVirtualizerContainer;
    private RadialKnob mBassKnob;
    private RadialKnob mVirtualizerKnob;

    private H mHandler;

    private KnobCommander mKnobCommander;

    private long mLastDisabledNotifyTime = -1;
    private Context mContext;

    public KnobContainer(Context context) {
        super(context);
        mContext = context;
    }

    public KnobContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    public KnobContainer(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private void init() {
        mKnobCommander = KnobCommander.getInstance(mContext);
        mHandler = new H();

        // we must add the proper knobs dynamically.
        if (mKnobCommander.hasBassBoost()) {
            mBassContainer = addKnob(KnobCommander.KNOB_BASS);
        }
        if (mKnobCommander.hasVirtualizer()) {
            mVirtualizerContainer = addKnob(KnobCommander.KNOB_VIRTUALIZER);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        init();

        if (DEBUG) Log.d(TAG, "onFinishInflate()");

        OnTouchListener knobTouchListener = (v, event) -> {
            Message message;
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    message = mHandler.obtainMessage(MSG_EXPAND, v.getTag());
                    mHandler.sendMessageDelayed(message, 0);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mHandler.removeMessages(MSG_EXPAND);
                    message = mHandler.obtainMessage(MSG_CONTRACT, v.getTag());
                    mHandler.sendMessageDelayed(message, 10);
                    break;
            }
            if (!v.isEnabled()) {
                notifyDisabled();
                return true;
            }
            return false;
        };

        if (mBassContainer != null) {
            mBassKnob = mBassContainer.findViewById(R.id.knob);
            mBassKnob.setTag(new KnobInfo(KnobCommander.KNOB_BASS, mBassKnob,
                    mBassContainer.findViewById(R.id.label)));
            mBassKnob.setOnTouchListener(knobTouchListener);
            mBassKnob.setOnKnobChangeListener(
                    KnobCommander.getInstance(getContext()).getRadialKnobCallback(
                            KnobCommander.KNOB_BASS
                    )
            );
            mBassKnob.setMax(100);


        }
        if (mVirtualizerContainer != null) {
            mVirtualizerKnob = mVirtualizerContainer.findViewById(R.id.knob);
            mVirtualizerKnob.setTag(new KnobInfo(KnobCommander.KNOB_VIRTUALIZER, mVirtualizerKnob,
                    mVirtualizerContainer.findViewById(R.id.label)));
            mVirtualizerKnob.setOnTouchListener(knobTouchListener);
            mVirtualizerKnob.setOnKnobChangeListener(
                    KnobCommander.getInstance(getContext()).getRadialKnobCallback(
                            KnobCommander.KNOB_VIRTUALIZER
                    )
            );
            mVirtualizerKnob.setMax(100);
        }
        updateKnobs(MasterConfigControl.getInstance(mContext).getCurrentDevice());

        setLayoutTransition(null);
    }

    private ViewGroup addKnob(int whichKnob) {
        ViewGroup knobContainer = (ViewGroup) LayoutInflater.from(mContext)
                .inflate(R.layout.generic_knob_control, this, false);
        TextView label = knobContainer.findViewById(R.id.label);

        int knobLabelRes;
        switch (whichKnob) {
            case KnobCommander.KNOB_BASS:
                knobLabelRes = R.string.bass;
                break;

            case KnobCommander.KNOB_VIRTUALIZER:
                knobLabelRes = R.string.virtualizer;
                break;

            default:
                return null;
        }

        label.setText(knobLabelRes);

        addView(knobContainer, getKnobParams());
        return knobContainer;
    }

    private LayoutParams getKnobParams() {
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        } else {
            return new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        }
    }

    public void setKnobVisible(int knob, boolean visible) {
        final int newMode = visible ? View.VISIBLE : View.GONE;
        ViewGroup v = null;
        switch (knob) {
            case KnobCommander.KNOB_VIRTUALIZER:
                v = mVirtualizerContainer;
                break;
            case KnobCommander.KNOB_BASS:
                v = mBassContainer;
                break;
        }
        if (v == null && visible) {
            throw new UnsupportedOperationException("no knob container for knob: " + knob);
        }

        if (newMode == v.getVisibility()) {
            return;
        }
        Log.d(TAG, "setKnobVisible() knob=" + knob + " visible=" + visible);
        v.setVisibility(newMode);
    }

    public void updateKnobHighlights(int color) {
        if (mBassKnob != null) {
            mBassKnob.setHighlightColor(color);
        }
        if (mVirtualizerKnob != null) {
            mVirtualizerKnob.setHighlightColor(color);
        }
    }

    private void notifyDisabled() {
        final long now = System.currentTimeMillis();
        if (mLastDisabledNotifyTime == -1 || now - mLastDisabledNotifyTime > NOTIFY_DISABLE_DELAY) {
            mLastDisabledNotifyTime = now;
            Toast.makeText(mContext, R.string.effect_unavalable_for_speaker,
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return false;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void resize(RadialKnob knob, View label, boolean makeBig) {
        if (knob.isEnabled()) {
            label.animate()
                    .alpha(makeBig ? 0 : 1)
                    .setInterpolator(new AccelerateInterpolator())
                    .setDuration(100);
            knob.resize(makeBig);
        }
    }

    @Override
    public void onDeviceChanged(AudioDeviceInfo device, boolean userChange) {
        if (device != null) {
            updateKnobs(device);
        }
    }

    @Override
    public void onGlobalDeviceToggle(boolean on) {

    }

    private void updateKnobs(AudioDeviceInfo device) {
        if (device == null) {
            return;
        }
        final boolean speaker = device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;

        mKnobCommander.updateBassKnob(mBassKnob, !speaker);
        mKnobCommander.updateVirtualizerKnob(mVirtualizerKnob, !speaker);
        setKnobVisible(KnobCommander.KNOB_VIRTUALIZER, true);
    }

    public static class KnobInfo {
        int whichKnob;
        RadialKnob knob;
        View label;

        public KnobInfo(int whichKnob, RadialKnob knob, View label) {
            this.knob = knob;
            this.label = label;
            this.whichKnob = whichKnob;
        }
    }

    private class H extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_EXPAND:
                case MSG_CONTRACT:
                    RadialKnob knob = ((KnobInfo) msg.obj).knob;
                    View label = ((KnobInfo) msg.obj).label;
                    boolean expand = msg.what == MSG_EXPAND;
                    resize(knob, label, expand);
                    break;
            }
        }
    }
}
