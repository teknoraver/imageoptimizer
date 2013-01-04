package net.teknoraver.imageoptimizer;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.view.MenuItem;
import android.support.v4.app.NavUtils;
import java.util.List;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class Settings extends PreferenceActivity {
	/**
	 * Determines whether to always show the simplified settings UI, where
	 * settings are presented in a single list. When false, settings are
	 * shown as a master/detail two-pane view on tablets. When true, a
	 * single pane is shown on tablets.
	 */
	private static final boolean ALWAYS_SIMPLE_PREFS = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setupActionBar();
	}

	/**
	 * Set up the {@link android.app.ActionBar}, if the API is available.
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void setupActionBar() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
			getActionBar().setDisplayHomeAsUpEnabled(true);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			NavUtils.navigateUpFromSameTask(this);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);

		setupSimplePreferencesScreen();
	}

	/**
	 * Shows the simplified settings UI if the device configuration if the
	 * device configuration dictates that a simplified, single-pane UI
	 * should be shown.
	 */
	private void setupSimplePreferencesScreen() {
		if (!isSimplePreferences(this))
			return;

		// In the simplified UI, fragments are not used at all and we
		// instead
		// use the older PreferenceActivity APIs.

		// Add 'general' preferences.
		PreferenceCategory fakeHeader;
		addPreferencesFromResource(R.xml.pref_general);
		bindPreferenceSummaryToValue(findPreference("timestamp"));

		// JPEG
		fakeHeader = new PreferenceCategory(this);
		fakeHeader.setTitle(R.string.pref_header_jpeg);
		getPreferenceScreen().addPreference(fakeHeader);
		addPreferencesFromResource(R.xml.pref_jpeg);
		bindPreferenceSummaryToValue(findPreference("dojpeg"));
		bindPreferenceSummaryToValue(findPreference("lossy"));
		bindPreferenceSummaryToValue(findPreference("jpegquality"));
		bindPreferenceSummaryToValue(findPreference("threshold"));

/*		// PNG
		fakeHeader = new PreferenceCategory(this);
		fakeHeader.setTitle(R.string.pref_header_png);
		getPreferenceScreen().addPreference(fakeHeader);
		addPreferencesFromResource(R.xml.pref_png);
		bindPreferenceSummaryToValue(findPreference("pngquality"));*/
	}

	private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
		@Override
		public boolean onPreferenceChange(Preference preference, Object value) {
			if (preference.getKey().equals("jpegquality"))
				preference.setSummary(App.getContext().getString(R.string.jpegquality_summary) + " " + value + "%");
			else if (preference.getKey().equals("lossy"))
				if((Boolean)value)
					preference.setSummary(App.getContext().getString(R.string.pref_title_lossy));
				else
					preference.setSummary(App.getContext().getString(R.string.pref_title_lossless));
			else if (preference.getKey().equals("threshold"))
				preference.setSummary(App.getContext().getString(R.string.threshold_summary) + " " + value + "%");
			else if (preference.getKey().equals("pngquality"))
				preference.setSummary("Compression level " + value);

			return true;
		}
	};

	private static void bindPreferenceSummaryToValue(Preference preference) {
		// Set the listener to watch for value changes.
		preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

		// Trigger the listener immediately with the preference's
		// current value.
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(preference.getContext());

		if(preference.getKey().equals("jpegquality"))
			sBindPreferenceSummaryToValueListener.onPreferenceChange(preference, sp.getInt(preference.getKey(), 75));
		else if(preference.getKey().equals("lossy"))
			sBindPreferenceSummaryToValueListener.onPreferenceChange(preference, sp.getBoolean(preference.getKey(), false));
		else if(preference.getKey().equals("threshold"))
			sBindPreferenceSummaryToValueListener.onPreferenceChange(preference, sp.getInt(preference.getKey(), 10));
		else if(preference.getKey().equals("dopng"))
			sBindPreferenceSummaryToValueListener.onPreferenceChange(preference, sp.getBoolean(preference.getKey(), true));
		else if(preference.getKey().equals("pngquality"))
			sBindPreferenceSummaryToValueListener.onPreferenceChange(preference, sp.getInt(preference.getKey(), 7));
	}

	/** {@inheritDoc} */
	@Override
	public boolean onIsMultiPane() {
		return isXLargeTablet(this) && !isSimplePreferences(this);
	}

	/**
	 * Helper method to determine if the device has an extra-large screen.
	 * For example, 10" tablets are extra-large.
	 */
	private static boolean isXLargeTablet(Context context) {
		return (context.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
	}

	/**
	 * Determines whether the simplified settings UI should be shown. This
	 * is true if this is forced via {@link #ALWAYS_SIMPLE_PREFS}, or the
	 * device doesn't have newer APIs like {@link PreferenceFragment}, or
	 * the device doesn't have an extra-large screen. In these cases, a
	 * single-pane "simplified" settings UI should be shown.
	 */
	private static boolean isSimplePreferences(Context context) {
		return ALWAYS_SIMPLE_PREFS
			|| Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB
			|| !isXLargeTablet(context);
	}

	/** {@inheritDoc} */
	@Override
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public void onBuildHeaders(List<Header> target) {
		if (!isSimplePreferences(this))
			loadHeadersFromResource(R.xml.pref_headers, target);
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	static public class GeneralPreferenceFragment extends PreferenceFragment {
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.pref_general);

			bindPreferenceSummaryToValue(findPreference("timestamp"));
		}
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	static public class JpegPreferenceFragment extends PreferenceFragment {
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.pref_jpeg);

			bindPreferenceSummaryToValue(findPreference("lossy"));
			bindPreferenceSummaryToValue(findPreference("jpegquality"));
			bindPreferenceSummaryToValue(findPreference("threshold"));
		}
	}

/*	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public class PngPreferenceFragment extends PreferenceFragment {
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.pref_png);

			bindPreferenceSummaryToValue(findPreference("pngquality"));
		}
	}*/
}
