/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * Update notification volume icon in Settings in response to user adjusting volume.
 */
public class NotificationVolumePreferenceController extends
        RingerModeAffectedVolumePreferenceController {

    private static final String KEY_NOTIFICATION_VOLUME = "notification_volume";
    private static final String TAG = "NotificationVolumePreferenceController";

    public NotificationVolumePreferenceController(Context context) {
        this(context, KEY_NOTIFICATION_VOLUME);
    }

    public NotificationVolumePreferenceController(Context context, String key) {
        super(context, key, TAG);

        mNormalIconId =  R.drawable.ic_notifications;
        mVibrateIconId = R.drawable.ic_volume_ringer_vibrate;
        mSilentIconId = R.drawable.ic_notifications_off_24dp;
    }

    @Override
    public int getAvailabilityStatus() {
        boolean separateNotification = isSeparateNotificationConfigEnabled();
        return mContext.getResources().getBoolean(R.bool.config_show_notification_volume)
                && !mHelper.isSingleVolume() && separateNotification
                ? AVAILABLE : CONDITIONALLY_UNAVAILABLE;
    }

    @Override
    public String getPreferenceKey() {
        return KEY_NOTIFICATION_VOLUME;
    }

    @Override
    public int getAudioStream() {
        return AudioManager.STREAM_NOTIFICATION;
    }

    @Override
    protected boolean hintsMatch(int hints) {
        boolean allEffectsDisabled =
                (hints & NotificationListenerService.HINT_HOST_DISABLE_EFFECTS) != 0;
        boolean notificationEffectsDisabled =
                (hints & NotificationListenerService.HINT_HOST_DISABLE_NOTIFICATION_EFFECTS) != 0;

        return allEffectsDisabled || notificationEffectsDisabled;
    }
}
