/*
 * Copyright (C) 2012 Jonas Kalderstam
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

package com.nononsenseapps.notepad.prefs;

import com.nononsenseapps.notepad.FragmentLayout;
import com.nononsenseapps.notepad.NotePad;
import com.nononsenseapps.notepad.R;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.util.Log;

public class SyncPrefs extends PreferenceFragment implements
		OnSharedPreferenceChangeListener {

	public static final String KEY_SYNC_ENABLE = "syncEnablePref";
	public static final String KEY_ACCOUNT = "accountPref";
	public static final String KEY_SYNC_FREQ = "syncFreq";

	private Activity activity;

	private Preference prefAccount;
	private Preference prefSyncFreq;

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		this.activity = activity;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.app_pref_sync);

		prefAccount = findPreference(KEY_ACCOUNT);
		prefSyncFreq = findPreference(KEY_SYNC_FREQ);

		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(activity);
		// Set up a listener whenever a key changes
		sharedPrefs.registerOnSharedPreferenceChangeListener(this);

		// Set summaries

		setAccountTitle(sharedPrefs);
		setFreqSummary(sharedPrefs);

		prefAccount
				.setOnPreferenceClickListener(new OnPreferenceClickListener() {
					public boolean onPreferenceClick(Preference preference) {
						// Show dialog
						activity.showDialog(PrefsActivity.DIALOG_ACCOUNTS);
						return true;
					}
				});
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		PreferenceManager.getDefaultSharedPreferences(activity).unregisterOnSharedPreferenceChangeListener(this);
	}

	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		try {
			Log.d("syncPrefs", "onChanged");
			if (activity.isFinishing()) {
				if (FragmentLayout.UI_DEBUG_PRINTS)
					Log.d("settings",
							"isFinishing, should not update summaries");
				// Setting the summary now would crash it with
				// IllegalStateException since we are not attached to a view
			} else {
				if (KEY_SYNC_ENABLE.equals(key)) {
					toggleSync(sharedPreferences);
				} else if (KEY_SYNC_FREQ.equals(key)) {
					setSyncInterval(activity, sharedPreferences);
					setFreqSummary(sharedPreferences);
				} else if (KEY_ACCOUNT.equals(key)) {
					Log.d("syncPrefs", "account");
					prefAccount.setTitle(sharedPreferences.getString(
							KEY_ACCOUNT, ""));
				} else if (FragmentLayout.UI_DEBUG_PRINTS)
					Log.d("settings", "Somethign changed!");
			}
		} catch (IllegalStateException e) {
			// This is just in case the "isFinishing" wouldn't be enough
			// The isFinishing will try to prevent us from doing something
			// stupid
			// This catch prevents the app from crashing if we do something
			// stupid
			if (FragmentLayout.UI_DEBUG_PRINTS)
				Log.d("settings", "Exception was caught: " + e.getMessage());
		}
	}

	/**
	 * Finds and returns the account of the name given
	 * 
	 * @param accountName
	 * @return
	 */
	public static Account getAccount(AccountManager manager, String accountName) {
		Account[] accounts = manager.getAccountsByType("com.google");
		for (Account account : accounts) {
			if (account.name.equals(accountName)) {
				return account;
			}
		}
		return null;
	}

	public static void setSyncInterval(Context activity,
			SharedPreferences sharedPreferences) {
		String accountName = sharedPreferences.getString(KEY_ACCOUNT, "");
		String sFreqMins = sharedPreferences.getString(KEY_SYNC_FREQ, "0");
		int freqMins = 0;
		try {
			freqMins = Integer.parseInt(sFreqMins);
		} catch (NumberFormatException e) {
			// Debugging error because of a mistake...
		}
		if (accountName == "") {
			// Something is very wrong if this happens
		} else if (freqMins == 0) {
			// Disable periodic syncing
			ContentResolver.removePeriodicSync(
					getAccount(AccountManager.get(activity), accountName),
					NotePad.AUTHORITY, new Bundle());
		} else {
			// Convert from minutes to seconds
			long pollFrequency = freqMins * 60;
			// Set periodic syncing
			ContentResolver.addPeriodicSync(
					getAccount(AccountManager.get(activity), accountName),
					NotePad.AUTHORITY, new Bundle(), pollFrequency);
		}
	}

	private void toggleSync(SharedPreferences sharedPreferences) {
		boolean enabled = sharedPreferences.getBoolean(KEY_SYNC_ENABLE, false);
		String accountName = sharedPreferences.getString(KEY_ACCOUNT, "");
		if (accountName.equals("")) {
			// do nothing yet
		} else if (enabled) {
			// set syncable
			ContentResolver.setIsSyncable(
					getAccount(AccountManager.get(activity), accountName),
					NotePad.AUTHORITY, 1);
			// Also set sync frequency
			setSyncInterval(activity, sharedPreferences);
		} else {
			// set unsyncable
			ContentResolver.setIsSyncable(
					getAccount(AccountManager.get(activity), accountName),
					NotePad.AUTHORITY, 0);
		}
	}

	private void setAccountTitle(SharedPreferences sharedPreferences) {
		prefAccount.setTitle(sharedPreferences.getString(KEY_ACCOUNT, ""));
		prefAccount.setSummary(R.string.settings_account_summary);
	}

	private void setFreqSummary(SharedPreferences sharedPreferences) {
		String sFreqMins = sharedPreferences.getString(KEY_SYNC_FREQ, "0");
		int freq = 0;
		try {
			freq = Integer.parseInt(sFreqMins);
		} catch (NumberFormatException e) {
			// Debugging error because of a mistake...
		}
		switch (freq) {
		case 0:
			prefSyncFreq.setSummary(R.string.manual);
			break;
		default:
			prefSyncFreq.setSummary(R.string.automatic);
			break;
		}
		// else if (freq == 60)
		// prefSyncFreq.setSummary(R.string.onehour);
		// else if (freq == 1440)
		// prefSyncFreq.setSummary(R.string.oneday);
		// else if (freq > 60)
		// prefSyncFreq.setSummary("" + freq/60 + " " +
		// getText(R.string.hours).toString());
		// else
		// prefSyncFreq.setSummary("" + freq + " " +
		// getText(R.string.minutes).toString());
	}

}