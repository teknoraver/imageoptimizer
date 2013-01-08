package net.teknoraver.imageoptimizer;

import android.app.Application;

public final class App extends Application {
	private static App a;

	@Override
	public void onCreate() {
		super.onCreate();
		a = this;
	}

	public static App getContext() {
		return a;
	}

	public static void debug(String txt) {
		if(BuildConfig.DEBUG)
			System.out.println(txt);
	}
}
