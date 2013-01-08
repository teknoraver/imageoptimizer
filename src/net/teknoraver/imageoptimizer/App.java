package net.teknoraver.imageoptimizer;

import android.app.Application;
import android.content.Context;

public final class App extends Application {
	private static Context c;

	@Override
	public void onCreate() {
		super.onCreate();
		c = this;
	}

	public static Context getContext() {
		return c;
	}

	public static void debug(String txt) {
		if(BuildConfig.DEBUG)
			System.out.println(txt);
	}
}
