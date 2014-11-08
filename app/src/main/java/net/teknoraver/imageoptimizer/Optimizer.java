package net.teknoraver.imageoptimizer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.Observable;
import java.util.Vector;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.widget.Toast;

abstract class Optimizer extends Observable implements Serializable, Runnable {
	private static final long serialVersionUID = 2968942827262809844L;

	private static final String CPUINFO = "/proc/cpuinfo";
	private static final String LASTVER = "last_version";

	protected int quality;
	protected Vector<String> files;
	protected boolean preserve;
	protected boolean run = true;
	protected String outdir;
	protected String params[];
	private int count;

	static class Result {
		String path;
		long origsize;
		long newsize;
		boolean compressed;
		boolean error;

		public Result() {
			error = true;
		}

		public Result(String p, long o, long n, boolean c) {
			path = p;
			origsize = o;
			newsize = n;
			compressed = c;
		}

		String getName() {
			return path.substring(path.lastIndexOf('/') + 1);
		}
	}

	Optimizer(Vector<String> f, int q, boolean p, String o) {
		quality = q;
		files = f;
		preserve = p;
		outdir = o;
		count = files.size();
	}

	@Override
	public boolean hasChanged() {
		return true;
	}

	@Override
	public void run() {
		final int CPU = Math.min(Runtime.getRuntime().availableProcessors(), files.size());
		App.debug("starting optimization on " + files.size() + " files with " + CPU + " threads");

		if(CPU == 1)
			new Run().run();
		else {
			Thread t[] = new Thread[CPU];

			for(int i = 0; i < CPU; i++) {
				t[i] = new Thread(new Run(), "" + i);
				t[i].start();
			}

			for(int i = 0; i < CPU; i++)
				try {
					t[i].join();
				} catch (InterruptedException e) { }
		}

		files.clear();
		files = null;
		notifyObservers(null);
	}

	private class Run implements Runnable {
		@Override
		public void run() {
			while(!files.isEmpty()) {
				if(!run)
					break;
				String file = files.remove(0);
//				App.debug("optimizer " + (Thread.currentThread().getName()) + " " + file);
				try {
					params[params.length - 1] = file;
					Process optimizer = Runtime.getRuntime().exec(params);
					BufferedReader stdout = new BufferedReader(new InputStreamReader(optimizer.getInputStream()), 1024);
					try {
						optimizer.waitFor();
						String line = stdout.readLine();
//						App.debug(line);
						parseOutput(line);
					} catch(Exception e) {
						e.printStackTrace();
						notifyObservers(new Result());
					}
					stdout.close();
					optimizer.destroy();
				} catch(IOException ioe) {
					ioe.printStackTrace();
				}
//				App.debug("done " + file);
			}
		}
	}

	protected abstract void parseOutput(String string);

	void abort() {
		run = false;
	}

	int count() {
		return count;
	}


	static void setupBinaries(Activity context) {
		try {
			boolean update = true;
			final int ver = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
			SharedPreferences pm = PreferenceManager.getDefaultSharedPreferences(context);
			if(pm.getInt(LASTVER, 0) == ver)
				update = false;
			else {
				App.debug("Updating binaries from " + pm.getInt(LASTVER, 0) + " to " + ver);
				Editor ed = pm.edit();
				ed.putInt(LASTVER, ver);
				ed.commit();
			}
			getFile(context, "jpegoptim", update);
			getFile(context, "optipng", update);
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		} catch (IOException ioe) {
			ioe.printStackTrace();
			Toast.makeText(context, R.string.ioerror, Toast.LENGTH_LONG).show();
			context.finish();
		}
	}

	private static String detectCpu() {
		if(Build.CPU_ABI.startsWith("arm"))
			return "arm";
		if(Build.CPU_ABI.startsWith("x86"))
			return "x86";
		if(Build.CPU_ABI.startsWith("mips"))
			return "mips";

		return null;
	}

	private static void getFile(Activity context, final String name, boolean update) throws IOException {
		String binary = detectCpu();
		if(binary == null) {
			new AlertDialog.Builder(context)
				.setTitle(context.getString(R.string.arch))
				.setMessage(context.getString(R.string.sendmail))
				.setNegativeButton(android.R.string.no, new DialogHandler(context))
				.setPositiveButton(android.R.string.yes, new DialogHandler(context))
				.setIcon(android.R.drawable.ic_dialog_email)
				.create().show();
		} else {
			final File file = new File(context.getFilesDir(), name);
			if (update || !file.exists())
				try {
					App.debug("extracting " + name);
					context.getFilesDir().mkdirs();
					final InputStream in = context.getAssets().open(name + '/' + binary);
					final FileOutputStream out = new FileOutputStream(file);
					final byte[] buf = new byte[65536];
					int len;
					while ((len = in.read(buf)) > 0)
						out.write(buf, 0, len);
					in.close();
					out.close();
					Runtime.getRuntime().exec(new String[]{"chmod", "755", file.getAbsolutePath()});
				} catch (final IOException ex) {
					ex.printStackTrace();
					return;
				}
		}
	}
	abstract String getExt();
	abstract String version();

	private static class DialogHandler implements OnClickListener {
		private Activity context;

		public DialogHandler(Activity a) {
			context = a;
		}

		@Override
		public void onClick(DialogInterface dialog, int which) {
			if(which == DialogInterface.BUTTON_POSITIVE) {
				Intent intent = new Intent(Intent.ACTION_SEND);
				intent.setType("message/rfc822");
				intent.putExtra(Intent.EXTRA_EMAIL, new String[] {context.getString(R.string.email)});
				intent.putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.support_sub));
				intent.putExtra(Intent.EXTRA_TEXT, context.getString(R.string.support_msg));
				File file = new File(CPUINFO);
				if (!file.exists() || !file.canRead()) {
					Toast.makeText(context, context.getString(R.string.support_atterror), Toast.LENGTH_LONG).show();
					context.finish();
					return;
				}
				intent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + CPUINFO));
				context.startActivity(Intent.createChooser(intent, context.getString(R.string.support_intent)));
			}
			context.finish();
		}
	}
}
