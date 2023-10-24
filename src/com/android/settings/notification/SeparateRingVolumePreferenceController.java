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

import android.content.Context;
import android.media.AudioManager;
import android.service.notification.NotificationListenerService;

import com.android.settings.R;

/**
 * This slider is used to represent ring volume when ring is separated from notification
 */
public class SeparateRingVolumePreferenceController extends
        RingerModeAffectedVolumePreferenceController {

    private static final String KEY_SEPARATE_RING_VOLUME = "separate_ring_volume";
    private static final String TAG = "SeparateRingVolumePreferenceController";

    public SeparateRingVolumePreferenceController(Context context) {
        this(context, KEY_SEPARATE_RING_VOLUME);
    }

    public SeparateRingVolumePreferenceController(Context context, String key) {
        super(context, key, TAG);

        mNormalIconId = R.drawable.ic_ring_volume;
        mVibrateIconId = R.drawable.ic_volume_ringer_vibrate;
        mSilentIconId = R.drawable.ic_ring_volume_off;
    }

    @Override
    public String getPreferenceKey() {
        return KEY_SEPARATE_RING_VOLUME;
    }

    @Override
    public int getAvailabilityStatus() {
        boolean separateNotification = isSeparateNotificationConfigEnabled();
        return separateNotification && !mHelper.isSingleVolume()
                ? AVAILABLE : CONDITIONALLY_UNAVAILABLE;
    }

    @Override
    public int getAudioStream() {
        return AudioManager.STREAM_RING;
    }

    @Override
    protected boolean hintsMatch(int hints) {
        return (hints & NotificationListenerService.HINT_HOST_DISABLE_CALL_EFFECTS) != 0
                || (hints & NotificationListenerService.HINT_HOST_DISABLE_EFFECTS) != 0;
    }

}
