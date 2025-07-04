/*
 * Copyright (C) 2014 The Android Open Source Project
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

package org.dslul.openboard.inputmethod.latin;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Process;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import org.dslul.openboard.inputmethod.keyboard.KeyboardLayoutSet;
import org.dslul.openboard.inputmethod.latin.settings.Settings;
import org.dslul.openboard.inputmethod.latin.setup.SetupActivity;
import org.dslul.openboard.inputmethod.latin.utils.DeviceProtectedUtils;
import org.dslul.openboard.inputmethod.latin.utils.UncachedInputMethodManagerUtils;

/**
 * This class detects the {@link Intent#ACTION_MY_PACKAGE_REPLACED} broadcast intent when this IME
 * package has been replaced by a newer version of the same package. This class also detects
 * {@link Intent#ACTION_BOOT_COMPLETED} and {@link Intent#ACTION_USER_INITIALIZE} broadcast intent.
 *
 * If this IME has already been installed in the system image and a new version of this IME has
 * been installed, {@link Intent#ACTION_MY_PACKAGE_REPLACED} is received by this receiver and it
 * will hide the setup wizard's icon.
 *
 * If this IME has already been installed in the data partition and a new version of this IME has
 * been installed, {@link Intent#ACTION_MY_PACKAGE_REPLACED} is received by this receiver but it
 * will not hide the setup wizard's icon, and the icon will appear on the launcher.
 *
 * If this IME hasn't been installed yet and has been newly installed, no
 * {@link Intent#ACTION_MY_PACKAGE_REPLACED} will be sent and the setup wizard's icon will appear
 * on the launcher.
 *
 * When the device has been booted, {@link Intent#ACTION_BOOT_COMPLETED} is received by this
 * receiver and it checks whether the setup wizard's icon should be appeared or not on the launcher
 * depending on which partition this IME is installed.
 *
 * When the system locale has been changed, {@link Intent#ACTION_LOCALE_CHANGED} is received by
 * this receiver and the {@link KeyboardLayoutSet}'s cache is cleared.
 */
public final class SystemBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = SystemBroadcastReceiver.class.getSimpleName();

    @Override
    public void onReceive(final Context context, final Intent intent) {
        final String intentAction = intent.getAction();
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intentAction)) {
            Log.i(TAG, "Package has been replaced: " + context.getPackageName());
            // Need to restore additional subtypes because system always clears additional
            // subtypes when the package is replaced.
            RichInputMethodManager.init(context);
            final RichInputMethodManager richImm = RichInputMethodManager.getInstance();
            final InputMethodSubtype[] additionalSubtypes = richImm.getAdditionalSubtypes();
            richImm.setAdditionalInputMethodSubtypes(additionalSubtypes);
            toggleAppIcon(context);
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(intentAction)) {
            Log.i(TAG, "Boot has been completed");
            toggleAppIcon(context);
        } else if (Intent.ACTION_LOCALE_CHANGED.equals(intentAction)) {
            Log.i(TAG, "System locale changed");
            KeyboardLayoutSet.onSystemLocaleChanged();
        }

        // The process that hosts this broadcast receiver is invoked and remains alive even after
        // 1) the package has been re-installed,
        // 2) the device has just booted,
        // 3) a new user has been created.
        // There is no good reason to keep the process alive if this IME isn't a current IME.
        final InputMethodManager imm = (InputMethodManager)
                context.getSystemService(Context.INPUT_METHOD_SERVICE);
        // Called to check whether this IME has been triggered by the current user or not
        final boolean isInputMethodManagerValidForUserOfThisProcess =
                !imm.getInputMethodList().isEmpty();
        final boolean isCurrentImeOfCurrentUser = isInputMethodManagerValidForUserOfThisProcess
                && UncachedInputMethodManagerUtils.isThisImeCurrent(context, imm);
        if (!isCurrentImeOfCurrentUser) {
            final int myPid = Process.myPid();
            Log.i(TAG, "Killing my process: pid=" + myPid);
            Process.killProcess(myPid);
        }
    }

    public static void toggleAppIcon(final Context context) {
        final int appInfoFlags = context.getApplicationInfo().flags;
        final boolean isSystemApp = (appInfoFlags & ApplicationInfo.FLAG_SYSTEM) > 0;
        if (Log.isLoggable(TAG, Log.INFO)) {
            Log.i(TAG, "toggleAppIcon() : FLAG_SYSTEM = " + isSystemApp);
        }
        final SharedPreferences prefs = DeviceProtectedUtils.getSharedPreferences(context);
        context.getPackageManager().setComponentEnabledSetting(
                new ComponentName(context, SetupActivity.class),
                Settings.readShowSetupWizardIcon(prefs, context)
                        ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }
}
