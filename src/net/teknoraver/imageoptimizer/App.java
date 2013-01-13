package net.teknoraver.imageoptimizer;

//import com.nullwire.trace.ExceptionHandler;

import android.app.Application;

public final class App extends Application {
	private static App a;

	public App() {
		a = this;
	}

/*	@Override
	public void onCreate() {
		super.onCreate();
		if(BuildConfig.DEBUG)
			ExceptionHandler.register(this);
	}*/

	public static App getContext() {
		return a;
	}

	public static void debug(String txt) {
		if(BuildConfig.DEBUG)
			System.out.println(txt);
	}
}
