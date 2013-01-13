package net.teknoraver.imageoptimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;

abstract class Optimizer extends Observable implements Serializable, Runnable {
	private static final long serialVersionUID = 2968942827262809844L;

	protected static final int SPLIT = 100;

	protected int quality;
	protected ArrayList<String> files;
	protected boolean preserve;
	protected boolean run = true;

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

	Optimizer(ArrayList<String> f, int q, boolean p) {
		quality = q;
		files = f;
		preserve = p;
	}

	@Override
	public boolean hasChanged() {
		return true;
	}

	@Override
	public void run() {
		App.debug("starting optimization on " + files.size() + " files");
		while(!files.isEmpty()) {
			List<String> sublist;
			if(files.size() < SPLIT)
				sublist = files;
			else
				sublist = files.subList(0, SPLIT);

			App.debug("optimization on " + sublist.size() + "/" + files.size());
			compress(sublist);

			sublist.clear();
		}
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
