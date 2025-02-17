/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2017-2022 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.audiofx.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.audiofx.AudioEffect;
import android.util.Log;

import org.lineageos.audiofx.service.AudioFxService;

public class ServiceDispatcher extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {

        final String action = intent.getAction();
        final String packageName = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME);
        final int audioSession = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION,
                AudioEffect.ERROR_BAD_VALUE);
        final int contentType = intent.getIntExtra(AudioEffect.EXTRA_CONTENT_TYPE,
                AudioEffect.CONTENT_TYPE_MUSIC);

        // check package name
        if (packageName == null) {
            return;
        }

        // check audio session
        if (audioSession < 0) {
            return;
        }

        // check if it's music
        if (contentType != AudioEffect.CONTENT_TYPE_MUSIC) {
            return;
        }

        Intent service = new Intent(context.getApplicationContext(), AudioFxService.class);
        service.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName);
        service.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSession);
        service.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, contentType);
        service.setAction(action);

        context.startService(service);
        if (AudioFxService.DEBUG) {
            Log.d("AudioFX-Dispatcher", "Received " + action);
        }

    }
}
