package net.teknoraver.imageoptimizer;

import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationManagerCompat;

public final class App extends Application {
	static final String CHANNEL = "progress";
	private static App a;

	public App() {
		a = this;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		dropOutputDir();
		// since API 26 notifications posted without a channel are silently dropped;
		// low importance, as progress updates shouldn't make any sound
		NotificationManagerCompat.from(this).createNotificationChannel(
			new NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
				.setName(getString(R.string.notify_channel))
				.build());
	}

	/**
	 * Saving to a separate output directory was removed in 1.9.0: forget the directory
	 * and release its persisted grant, unless it is also one of the search paths.
	 */
	private void dropOutputDir() {
		SharedPreferences pm = PreferenceManager.getDefaultSharedPreferences(this);
		String out = pm.getString("savepath", null);
		if(out != null && !PathSelector.getPaths().contains(out))
			try {
				getContentResolver().releasePersistableUriPermission(Uri.parse(out),
					Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
			} catch (SecurityException e) {
				e.printStackTrace();
			}
		if(pm.contains("savepath") || pm.contains("destdir"))
			pm.edit().remove("savepath").remove("destdir").apply();
	}

	public static App getContext() {
		return a;
	}

	public static void debug(String txt) {
		if(BuildConfig.DEBUG)
			System.out.println(txt);
	}
}
