/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.settings.display;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.android.settings.R;
import com.android.settings.core.PreferenceControllerMixin;
import com.android.settings.core.TogglePreferenceController;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;

public class ForcePeakRefreshRatePreferenceController extends TogglePreferenceController
        implements Preference.OnPreferenceChangeListener, PreferenceControllerMixin,
        LifecycleObserver, OnStart, OnStop {

    @VisibleForTesting
    static float DEFAULT_REFRESH_RATE = 60f;

    @VisibleForTesting
    static float NO_CONFIG = 0f;

    @VisibleForTesting
    float mPeakRefreshRate;

    private static final String TAG = "ForcePeakRefreshRateCtr";

    private Preference mPreference;
    private final PowerManager mPowerManager;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mPreference != null)
                updateState(mPreference);
        }
    };

    public ForcePeakRefreshRatePreferenceController(Context context, String key) {
        super(context, key);
        mPowerManager = context.getSystemService(PowerManager.class);

        final DisplayManager dm = context.getSystemService(DisplayManager.class);
        final Display display = dm.getDisplay(Display.DEFAULT_DISPLAY);

        if (display == null) {
            Log.w(TAG, "No valid default display device");
            mPeakRefreshRate = DEFAULT_REFRESH_RATE;
        } else {
            mPeakRefreshRate = findPeakRefreshRate(display.getSupportedModes());
        }

        Log.d(TAG, "DEFAULT_REFRESH_RATE : " + DEFAULT_REFRESH_RATE
            + " mPeakRefreshRate : " + mPeakRefreshRate);
    }

    @Override
    public void onStart() {
        mContext.registerReceiver(mReceiver,
                new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED));
    }

    @Override
    public void onStop() {
        mContext.unregisterReceiver(mReceiver);
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
    }

    @Override
    public CharSequence getSummary() {
        return mContext.getString(mPowerManager.isPowerSaveMode()
                ? R.string.dark_ui_mode_disabled_summary_dark_theme_on
                : R.string.force_high_refresh_rate_desc);
    }

    @Override
    public int getAvailabilityStatus() {
        if (mContext.getResources().getBoolean(R.bool.config_show_smooth_display)) {
            return mPeakRefreshRate > DEFAULT_REFRESH_RATE ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
        } else {
            return UNSUPPORTED_ON_DEVICE;
        }
    }

    @Override
    public boolean isChecked() {
        return isForcePeakRefreshRateEnabled();
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        forcePeakRefreshRate(isChecked);
        return true;
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        refreshSummary(preference);
        preference.setEnabled(!mPowerManager.isPowerSaveMode());
    }

    @Override
    public int getSliceHighlightMenuRes() {
        return R.string.menu_key_display;
    }

    @VisibleForTesting
    void forcePeakRefreshRate(boolean enable) {
        final float peakRefreshRate = enable ? mPeakRefreshRate : NO_CONFIG;
        Settings.System.putFloat(mContext.getContentResolver(),
            Settings.System.MIN_REFRESH_RATE, peakRefreshRate);
    }

    boolean isForcePeakRefreshRateEnabled() {
        final float peakRefreshRate = Settings.System.getFloat(mContext.getContentResolver(),
            Settings.System.MIN_REFRESH_RATE, NO_CONFIG);

        return peakRefreshRate >= mPeakRefreshRate;
    }

    private float findPeakRefreshRate(Display.Mode[] modes) {
        float peakRefreshRate = DEFAULT_REFRESH_RATE;
        float defaultPeakRefreshRate = (float) mContext.getResources().getInteger(
                    com.android.internal.R.integer.config_defaultPeakRefreshRate);
        for (Display.Mode mode : modes) {
            float refreshRate = Math.round(mode.getRefreshRate());
            if (refreshRate > peakRefreshRate
                    && (defaultPeakRefreshRate == 0 || refreshRate <= defaultPeakRefreshRate)) {
                peakRefreshRate = refreshRate;
            }
        }

        return peakRefreshRate;
    }
}
