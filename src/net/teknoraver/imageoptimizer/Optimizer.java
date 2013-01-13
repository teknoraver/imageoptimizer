package net.teknoraver.imageoptimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;

abstract class Optimizer extends Observable implements Serializable, Runnable {
	private static final long serialVersionUID = 2968942827262809844L;

	static String EXT;

	protected static final int SPLIT = 100;

	protected int quality;
	protected ArrayList<String> files;
	protected boolean preserve;
	protected int threshold;
	protected boolean run = true;

	class Result {
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
	}

	Optimizer(ArrayList<String> f, int q, boolean p, int t) {
		quality = q;
		files = f;
		preserve = p;
		threshold = t;
	}

	@Override
	public boolean hasChanged() {
		return true;
	}

	@Override
	public void run() {
		App.debug("starting jpegoptim1 on " + files.size() + " files");
		for(int i = 0; run && i < files.size(); i += SPLIT) {
			List<String> sublist = files.subList(0, Math.min(SPLIT, files.size()));

			compress(sublist);

			sublist.clear();
		}
		files.clear();
		files = null;
		notifyObservers(null);
	}

	protected abstract void compress(List<String> sublist);

	void abort() {
		run = false;
	}

	int count() {
		return files.size();
	}

	abstract String getExt();
	abstract String version();
}
