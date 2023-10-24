/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.notification;

import android.app.INotificationManager;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ServiceManager;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;

import androidx.lifecycle.OnLifecycleEvent;
import androidx.preference.PreferenceScreen;

import com.android.internal.annotations.VisibleForTesting;
import com.android.settingslib.core.lifecycle.Lifecycle;

import java.util.Objects;

/**
 * Shared functionality and interfaces for volume controllers whose state can change by ringer mode
 */
public abstract class RingerModeAffectedVolumePreferenceController extends
        VolumeSeekBarPreferenceController {

    private final String mTag;

    protected int mNormalIconId;
    protected int mVibrateIconId;
    protected int mSilentIconId;
    protected int mMuteIcon;

    protected Vibrator mVibrator;
    protected int mRingerMode = AudioManager.RINGER_MODE_NORMAL;
    protected ComponentName mSuppressor;
    protected boolean mSeparateNotification;
    protected INotificationManager mNoMan;

    private final RingReceiver mReceiver = new RingReceiver();
    private final H mHandler = new H();

    private final ContentObserver mSettingObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            boolean valueUpdated = readSeparateNotificationVolumeConfig();
            if (valueUpdated) {
                updateEffectsSuppressor();
                updateRingerMode();
                updateVisibility();
            }
        }
    };

    public RingerModeAffectedVolumePreferenceController(Context context, String key, String tag) {
        super(context, key);
        mTag = tag;
        mVibrator = mContext.getSystemService(Vibrator.class);
        if (mVibrator != null && !mVibrator.hasVibrator()) {
            mVibrator = null;
        }
        mSeparateNotification = isSeparateNotificationConfigEnabled();
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        if (mPreference == null) {
            setupVolPreference(screen);
        }

        readSeparateNotificationVolumeConfig();
        updateEffectsSuppressor();
        updateRingerMode();
        updateVisibility();
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    @Override
    public void onResume() {
        super.onResume();
        mReceiver.register(true);
        mContext.getContentResolver().registerContentObserver(Settings.System.getUriFor(
                Settings.System.VOLUME_SEPARATE_NOTIFICATION), false, mSettingObserver);
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    @Override
    public void onPause() {
        super.onPause();
        mReceiver.register(false);
        mContext.getContentResolver().unregisterContentObserver(mSettingObserver);
    }

    protected void updateEffectsSuppressor() {
        final ComponentName suppressor = NotificationManager.from(mContext).getEffectsSuppressor();
        if (Objects.equals(suppressor, mSuppressor)) return;

        if (mNoMan == null) {
            mNoMan = INotificationManager.Stub.asInterface(
                    ServiceManager.getService(Context.NOTIFICATION_SERVICE));
        }

        final int hints;
        try {
            hints = mNoMan.getHintsFromListenerNoToken();
        } catch (android.os.RemoteException ex) {
            Log.w(mTag, "updateEffectsSuppressor: " + ex.getMessage());
            return;
        }

        if (hintsMatch(hints)) {
            mSuppressor = suppressor;
            if (mPreference != null) {
                final String text = SuppressorHelper.getSuppressionText(mContext, suppressor);
                mPreference.setSuppressionText(text);
            }
        }
    }

    @VisibleForTesting
    void setPreference(VolumeSeekBarPreference volumeSeekBarPreference) {
        mPreference = volumeSeekBarPreference;
    }

    @VisibleForTesting
    void setVibrator(Vibrator vibrator) {
        mVibrator = vibrator;
    }

    @Override
    public boolean isSliceable() {
        return true;
    }

    @Override
    public boolean isPublicSlice() {
        return true;
    }

    @Override
    public boolean useDynamicSliceSummary() {
        return true;
    }

    @Override
    public int getMuteIcon() {
        return mMuteIcon;
    }

    protected void updateVisibility() {
        if (mPreference != null) {
            int status = getAvailabilityStatus();
            mPreference.setVisible(status == AVAILABLE);
            mPreference.setEnabled(mRingerMode == AudioManager.RINGER_MODE_NORMAL);
        }
    }

    protected boolean isSeparateNotificationConfigEnabled() {
        return Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.VOLUME_SEPARATE_NOTIFICATION, 0) == 1;
    }

    /**
     * side effect: updates the cached value of the config
     * @return has the config changed?
     */
    protected boolean readSeparateNotificationVolumeConfig() {
        boolean newVal = isSeparateNotificationConfigEnabled();

        boolean valueUpdated = newVal != mSeparateNotification;
        if (valueUpdated) {
            mSeparateNotification = newVal;
        }

        return valueUpdated;
    }

    /**
     * Updates UI Icon in response to ringer mode changes.
     * @return whether the ringer mode has changed.
     */
    protected boolean updateRingerMode() {
        final int ringerMode = mHelper.getRingerModeInternal();
        if (mRingerMode == ringerMode) {
            return false;
        }
        mRingerMode = ringerMode;
        selectPreferenceIconState();
        return true;
    }

    /**
     * Switching among normal/mute/vibrate
     */
    protected void selectPreferenceIconState() {
        if (mPreference != null) {
            if (mVibrator != null && mRingerMode == AudioManager.RINGER_MODE_VIBRATE) {
                mMuteIcon = mVibrateIconId;
                mPreference.showIcon(mVibrateIconId);
            } else if (mRingerMode == AudioManager.RINGER_MODE_SILENT
                    || mVibrator == null && mRingerMode == AudioManager.RINGER_MODE_VIBRATE) {
                mMuteIcon = mSilentIconId;
                mPreference.showIcon(mSilentIconId);
            } else { // ringmode normal: could be that we are still silent
                if (mHelper.getStreamVolume(getAudioStream()) == 0) {
                    // ring is in normal, but volume is in silent
                    mMuteIcon = mSilentIconId;
                    mPreference.showIcon(mSilentIconId);
                } else {
                    mPreference.showIcon(mNormalIconId);
                }
            }
        }
    }

    protected abstract boolean hintsMatch(int hints);

    private final class H extends Handler {
        private static final int UPDATE_EFFECTS_SUPPRESSOR = 1;
        private static final int UPDATE_RINGER_MODE = 2;
        private static final int VOLUME_CHANGED = 3;

        private H() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UPDATE_EFFECTS_SUPPRESSOR:
                    updateEffectsSuppressor();
                    break;
                case UPDATE_RINGER_MODE:
                    updateRingerMode();
                    updateVisibility();
                    break;
                case VOLUME_CHANGED:
                    selectPreferenceIconState();
                    updateVisibility();
                    break;
            }
        }
    }

    /**
     * For volume icon to be accurate, we need to listen to volume change as well.
     * That is because the icon can change from mute/vibrate to normal without ringer mode changing.
     */
    private class RingReceiver extends BroadcastReceiver {
        private boolean mRegistered;

        public void register(boolean register) {
            if (mRegistered == register) return;
            if (register) {
                final IntentFilter filter = new IntentFilter();
                filter.addAction(NotificationManager.ACTION_EFFECTS_SUPPRESSOR_CHANGED);
                filter.addAction(AudioManager.INTERNAL_RINGER_MODE_CHANGED_ACTION);
                filter.addAction(AudioManager.VOLUME_CHANGED_ACTION);
                mContext.registerReceiver(this, filter);
            } else {
                mContext.unregisterReceiver(this);
            }
            mRegistered = register;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (NotificationManager.ACTION_EFFECTS_SUPPRESSOR_CHANGED.equals(action)) {
                mHandler.sendEmptyMessage(H.UPDATE_EFFECTS_SUPPRESSOR);
            } else if (AudioManager.INTERNAL_RINGER_MODE_CHANGED_ACTION.equals(action)) {
                mHandler.sendEmptyMessage(H.UPDATE_RINGER_MODE);
            } else if (AudioManager.VOLUME_CHANGED_ACTION.equals(action)) {
                int streamType = intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1);
                if (streamType == getAudioStream()) {
                    int streamValue = intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_VALUE,
                            -1);
                    mHandler.obtainMessage(H.VOLUME_CHANGED, streamValue, 0)
                            .sendToTarget();
                }
            }
        }
    }

}
