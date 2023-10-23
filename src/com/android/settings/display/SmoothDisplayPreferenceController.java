/*
 * Copyright (C) 2023 Paranoid Android
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
import android.os.PowerManager;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.TogglePreferenceController;
import com.android.settingslib.PrimarySwitchPreference;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;

import java.util.Collections;
import java.util.Set;

public class SmoothDisplayPreferenceController extends TogglePreferenceController implements
        LifecycleObserver, OnStart, OnStop {

    private static final String TAG = "SmoothDisplayPreferenceController";
    private static final int DEFAULT_REFRESH_RATE = 60;

    private Set<Integer> mHighRefreshRates;
    private int mLeastHighRefreshRate, mMaxRefreshRate;
    private PrimarySwitchPreference mPreference;

    private final PowerManager mPowerManager;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mPreference != null)
                updateState(mPreference);
        }
    };

    public SmoothDisplayPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
        mHighRefreshRates = SmoothDisplayFragment.getHighRefreshRates(context);
        mMaxRefreshRate = Collections.max(mHighRefreshRates);
        mLeastHighRefreshRate = Collections.min(mHighRefreshRates);
        mPowerManager = context.getSystemService(PowerManager.class);
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
        mPreference = (PrimarySwitchPreference) screen.findPreference(getPreferenceKey());
    }

    @Override
    public CharSequence getSummary() {
        if (mPowerManager.isPowerSaveMode()) {
            return mContext.getString(R.string.dark_ui_mode_disabled_summary_dark_theme_on);
        }
        final boolean checked = isChecked();
        final String status = mContext.getString(checked
                ? R.string.switch_on_text : R.string.switch_off_text);
        final int refreshRate = checked ? getPeakRefreshRate() : DEFAULT_REFRESH_RATE;
        return String.format("%s (%d Hz)", status, refreshRate);
    }

    @Override
    public int getAvailabilityStatus() {
        if (mContext.getResources().getBoolean(R.bool.config_show_smooth_display)) {
            return mHighRefreshRates.size() > 1 ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
        } else {
            return UNSUPPORTED_ON_DEVICE;
        }
    }

    @Override
    public boolean isChecked() {
        return getPeakRefreshRate() >= mLeastHighRefreshRate;
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        Settings.System.putFloat(mContext.getContentResolver(),
                Settings.System.PEAK_REFRESH_RATE,
                (float) (isChecked ? getSmoothRefreshRate() : DEFAULT_REFRESH_RATE));
        refreshSummary(mPreference);
        return true;
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        refreshSummary(preference);

        final boolean enabled = !mPowerManager.isPowerSaveMode();
        mPreference.setEnabled(enabled);
        mPreference.setSwitchEnabled(enabled);
    }

    @Override
    public int getSliceHighlightMenuRes() {
        return R.string.menu_key_display;
    }

    private int roundToNearestHighRefreshRate(int refreshRate) {
        if (mHighRefreshRates.contains(refreshRate)) return refreshRate;
        int findRefreshRate = mLeastHighRefreshRate;
        for (Integer highRefreshRate : mHighRefreshRates) {
            if (highRefreshRate > refreshRate) break;
            findRefreshRate = highRefreshRate;
        }
        return findRefreshRate;
    }

    private int getDefaultPeakRefreshRate() {
        return mContext.getResources().getInteger(
                com.android.internal.R.integer.config_defaultPeakRefreshRate);
    }

    private int getPeakRefreshRate() {
        final int peakRefreshRate = Math.round(Settings.System.getFloat(
                mContext.getContentResolver(),
                Settings.System.PEAK_REFRESH_RATE, getDefaultPeakRefreshRate()));
        if (peakRefreshRate < DEFAULT_REFRESH_RATE) {
            return mMaxRefreshRate;
        } else if (peakRefreshRate < mLeastHighRefreshRate) {
            return DEFAULT_REFRESH_RATE;
        }
        return roundToNearestHighRefreshRate(peakRefreshRate);
    }

    private int getSmoothRefreshRate() {
        final int smoothRefreshRate = Math.round(Settings.System.getFloat(
                mContext.getContentResolver(),
                Settings.System.SMOOTH_REFRESH_RATE, (float) getPeakRefreshRate()));
        if (smoothRefreshRate == DEFAULT_REFRESH_RATE) {
            return Math.max(mLeastHighRefreshRate, getDefaultPeakRefreshRate());
        }
        return roundToNearestHighRefreshRate(smoothRefreshRate);
    }
}
