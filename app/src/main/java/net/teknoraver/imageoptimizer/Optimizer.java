package net.teknoraver.imageoptimizer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Locale;
import java.util.Observable;
import java.util.Vector;

import androidx.documentfile.provider.DocumentFile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
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
	/** Destination SAF tree Uri (as a string) to copy optimized files into, or null to overwrite in place. */
	protected String outdir;
	protected String params[];
	private int count;

	static class Result {
		/** The original content:// Uri (as a string) of the image, not the local cache file. */
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
			return ContentUtil.displayName(App.getContext().getContentResolver(), Uri.parse(path));
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
				String uriStr = files.remove(0);
				Uri uri = Uri.parse(uriStr);
				File cacheFile = null;
				try {
					String name = ContentUtil.displayName(App.getContext().getContentResolver(), uri);
					cacheFile = ContentUtil.copyToCache(App.getContext(), uri, name);
					params[params.length - 1] = cacheFile.getAbsolutePath();
					Process optimizer = Runtime.getRuntime().exec(params);
					BufferedReader stdout = new BufferedReader(new InputStreamReader(optimizer.getInputStream()), 1024);
					Result result;
					try {
						optimizer.waitFor();
						String line = stdout.readLine();
						result = parseOutput(uriStr, line);
					} catch(Exception e) {
						e.printStackTrace();
						result = new Result();
					}
					stdout.close();
					optimizer.destroy();

					if(!result.error && result.compressed) {
						try {
							writeBack(uri, cacheFile);
						} catch (IOException | SecurityException e) {
							e.printStackTrace();
							result = new Result();
						}
					}
					notifyObservers(result);
				} catch(IOException ioe) {
					ioe.printStackTrace();
					notifyObservers(new Result());
				} finally {
					if(cacheFile != null)
						ContentUtil.deleteCacheDir(cacheFile);
				}
			}
		}
	}

	/** Copies the optimized cache file back to its real destination: either the original
	 *  image (in place) or a file of the same name inside the configured destination tree. */
	private void writeBack(Uri original, File optimized) throws IOException {
		if(outdir != null) {
			DocumentFile destDir = DocumentFile.fromTreeUri(App.getContext(), Uri.parse(outdir));
			if(destDir == null)
				throw new IOException("cannot open destination " + outdir);
			String name = optimized.getName();
			DocumentFile existing = destDir.findFile(name);
			if(existing != null)
				existing.delete();
			String mime = name.toLowerCase(Locale.US).endsWith(".png") ? "image/png" : "image/jpeg";
			DocumentFile dest = destDir.createFile(mime, name);
			if(dest == null)
				throw new IOException("cannot create " + name + " in " + outdir);
			copyFileToUri(optimized, dest.getUri());
		} else
			copyFileToUri(optimized, original);
	}

	private void copyFileToUri(File src, Uri dst) throws IOException {
		ContentResolver cr = App.getContext().getContentResolver();
		try (OutputStream out = cr.openOutputStream(dst, "wt")) {
			if(out == null)
				throw new IOException("cannot open " + dst + " for writing");
			try (InputStream in = new FileInputStream(src)) {
				byte[] buf = new byte[65536];
				int len;
				while((len = in.read(buf)) > 0)
					out.write(buf, 0, len);
			}
		}
	}

	protected abstract Result parseOutput(String originalUri, String line);

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
		if(Build.SUPPORTED_ABIS.length > 0) {
			for(String abi : Build.SUPPORTED_ABIS) {
				if(abi.startsWith("arm64") || abi.startsWith("aarch64"))
					return "arm64";
				if(abi.startsWith("x86_64"))
					return "x86_64";
				if(abi.startsWith("armeabi") || abi.startsWith("arm"))
					return "arm";
				if(abi.startsWith("x86"))
					return "x86";
			}
		}

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
