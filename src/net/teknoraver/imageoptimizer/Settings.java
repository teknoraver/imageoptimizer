package net.teknoraver.imageoptimizer;

import java.util.List;

import com.lamerman.FileDialog;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.widget.Toast;

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
	static final String ASC = "ascendent";
	static final String MODE = "mode";
	static final String LOSSY = "lossy";
	static final String JPG = "dojpeg";
	static final String PNG = "dopng";
	static final String JPEGQ = "jpegquality";
	static final String TIMESTAMP = "timestamp";
	static final String THRESHOLD = "threshold";
	static final String PNGQ = "pngquality";
	static final String DEST = "destdir";
	static final String OUT = "savepath";
	static final String SEARCH = "search_path";

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
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);

		setupSimplePreferencesScreen();
	}

	/**
	 * Shows the simplified settings UI if the device configuration if the
	 * device configuration dictates that a simplified, single-pane UI
	 * should be shown.
	 */
	@SuppressWarnings("deprecation")
	private void setupSimplePreferencesScreen() {
		if (!isSimplePreferences(this))
			return;

		// In the simplified UI, fragments are not used at all and we
		// instead
		// use the older PreferenceActivity APIs.

		// Add 'general' preferences.
		PreferenceCategory fakeHeader;
		addPreferencesFromResource(R.xml.pref_general);
		bindPreferenceSummaryToValue(findPreference(TIMESTAMP));
		bindPreferenceSummaryToValue(findPreference(OUT));

		findPreference(SEARCH).setOnPreferenceClickListener(new Starter(this));
		findPreference(OUT).setOnPreferenceClickListener(new DirBrowser(this));

		// JPEG
		fakeHeader = new PreferenceCategory(this);
		fakeHeader.setTitle(R.string.pref_header_jpeg);
		getPreferenceScreen().addPreference(fakeHeader);
		addPreferencesFromResource(R.xml.pref_jpeg);
		bindPreferenceSummaryToValue(findPreference(JPG));
		bindPreferenceSummaryToValue(findPreference(LOSSY));
		bindPreferenceSummaryToValue(findPreference(JPEGQ));
		bindPreferenceSummaryToValue(findPreference(THRESHOLD));

		// PNG
		fakeHeader = new PreferenceCategory(this);
		fakeHeader.setTitle(R.string.pref_header_png);
		getPreferenceScreen().addPreference(fakeHeader);
		addPreferencesFromResource(R.xml.pref_png);
		bindPreferenceSummaryToValue(findPreference(PNGQ));
	}

	private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
		@Override
		public boolean onPreferenceChange(Preference preference, Object value) {
			if (preference.getKey().equals(OUT))
				preference.setSummary(value.toString());
			else if (preference.getKey().equals(JPEGQ))
				preference.setSummary(App.getContext().getString(R.string.jpegquality_summary, value));
			else if (preference.getKey().equals(LOSSY))
				if((Boolean)value)
					preference.setSummary(App.getContext().getString(R.string.pref_title_lossy));
				else
					preference.setSummary(App.getContext().getString(R.string.pref_title_lossless));
			else if (preference.getKey().equals(THRESHOLD))
				preference.setSummary(App.getContext().getString(R.string.threshold_summary, value));
			else if (preference.getKey().equals(PNGQ))
				preference.setSummary(App.getContext().getString(R.string.pngquality_summary, value));

			return true;
		}
	};

	private static void bindPreferenceSummaryToValue(Preference preference) {
		// Set the listener to watch for value changes.
		preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

		// Trigger the listener immediately with the preference's
		// current value.
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(preference.getContext());

		if(preference.getKey().equals(JPEGQ))
			sBindPreferenceSummaryToValueListener.onPreferenceChange(preference, sp.getInt(JPEGQ, 75));
		else if(preference.getKey().equals(OUT))
			sBindPreferenceSummaryToValueListener.onPreferenceChange(preference, sp.getString(OUT, ""));
		else if(preference.getKey().equals(LOSSY))
			sBindPreferenceSummaryToValueListener.onPreferenceChange(preference, sp.getBoolean(LOSSY, false));
		else if(preference.getKey().equals(THRESHOLD))
			sBindPreferenceSummaryToValueListener.onPreferenceChange(preference, sp.getInt(THRESHOLD, 10));
		else if(preference.getKey().equals(PNGQ))
			sBindPreferenceSummaryToValueListener.onPreferenceChange(preference, sp.getInt(PNGQ, 7));
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(requestCode != DirBrowser.ADDPATH) {
			super.onActivityResult(requestCode, resultCode, data);
			return;
		}
		String newPath = data.getStringExtra(FileDialog.RESULT_PATH);
		if(newPath == null)
			return;
		PreferenceManager.getDefaultSharedPreferences(this)
			.edit()
			.putString(OUT, newPath)
			.commit();
		findPreference(OUT).setSummary(newPath);
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

			bindPreferenceSummaryToValue(findPreference(TIMESTAMP));
			bindPreferenceSummaryToValue(findPreference(OUT));

			findPreference("search_path").setOnPreferenceClickListener(new Starter(null));
			findPreference(OUT).setOnPreferenceClickListener(new DirBrowser(null));
		}
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	static public class JpegPreferenceFragment extends PreferenceFragment {
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.pref_jpeg);

			bindPreferenceSummaryToValue(findPreference(LOSSY));
			bindPreferenceSummaryToValue(findPreference(JPEGQ));
			bindPreferenceSummaryToValue(findPreference(THRESHOLD));
		}
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	static public class PngPreferenceFragment extends PreferenceFragment {
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.pref_png);

			bindPreferenceSummaryToValue(findPreference(PNGQ));
		}
	}
}

class Starter implements OnPreferenceClickListener
{
	private Context context;

	public Starter(Context c) {
		context = c;
	}

	@Override
	public boolean onPreferenceClick(Preference preference) {
		Intent intent = new Intent(App.getContext(), PathSelector.class);
		if(context == null) {
			context = App.getContext();
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		}
		context.startActivity(intent);
		return false;
	}
}

class DirBrowser implements OnPreferenceClickListener
{
	static final int ADDPATH = 1;
	private Activity activity;

	public DirBrowser(Activity c) {
		activity = c;
	}

	@Override
	public boolean onPreferenceClick(Preference preference) {
		Intent intent = new Intent(App.getContext(), FileDialog.class);
/*		if(activity == null) {
			activity = App.getContext();
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		}*/
		intent	.putExtra(FileDialog.START_PATH, Environment.getExternalStorageDirectory().getPath())
			.putExtra(FileDialog.CAN_SELECT_DIR, true)
			.putExtra(FileDialog.SELECTION_MODE, FileDialog.MODE_OPEN)
			.putExtra(FileDialog.FORMAT_FILTER, new String[]{".jpg", ".png"});
		activity.startActivityForResult(intent, ADDPATH);
		return false;
	}
}
