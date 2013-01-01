package net.teknoraver.imageoptimizer;

import android.app.Application;
import android.content.Context;

public class App extends Application {
	private static Context c;

	@Override
	public void onCreate() {
		super.onCreate();
		c = this;
	}

	public static Context getContext() {
		return c;
	}
}
