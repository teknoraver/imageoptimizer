package net.teknoraver.imageoptimizer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Observable;
import java.util.Vector;

import android.content.ContentResolver;
import android.net.Uri;

abstract class Optimizer extends Observable implements Serializable, Runnable {
	private static final long serialVersionUID = 2968942827262809844L;

	protected int quality;
	protected Vector<String> files;
	protected boolean preserve;
	protected boolean run = true;
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

	Optimizer(Vector<String> f, int q, boolean p) {
		quality = q;
		files = f;
		preserve = p;
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
					// params is shared by all the worker threads, never modify it in place
					String cmd[] = params.clone();
					cmd[cmd.length - 1] = cacheFile.getAbsolutePath();
					Process optimizer = Runtime.getRuntime().exec(cmd);
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
							copyFileToUri(cacheFile, uri);
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

	/** Copies the optimized cache file back over the original image. */
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

	abstract String getExt();
	abstract String version();
}
