package net.teknoraver.imageoptimizer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.Observable;
import java.util.Vector;

abstract class Optimizer extends Observable implements Serializable, Runnable {
	private static final long serialVersionUID = 2968942827262809844L;

	static final int CPU = Runtime.getRuntime().availableProcessors();

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
		App.debug("starting optimization on " + files.size() + " files");
		Thread t[] = new Thread[CPU];

		for(int i = 0; i < CPU; i++) {
			t[i] = new Thread(new Run(), "" + i);
			t[i].start();
		}

		for(int i = 0; i < CPU; i++)
			try {
				t[i].join();
			} catch (InterruptedException e) { }

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
				App.debug("optimizer " + (Thread.currentThread().getName()) + " " + file);
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
				App.debug("done " + file);
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

	abstract String getExt();
	abstract String version();
}
