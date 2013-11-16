package net.teknoraver.imageoptimizer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Observable;

abstract class Optimizer extends Observable implements Serializable, Runnable {
	private static final long serialVersionUID = 2968942827262809844L;

	protected static final int SPLIT = 100;

	protected int quality;
	protected ArrayList<String> files;
	protected boolean preserve;
	protected boolean run = true;
	protected String outdir;
	protected String params[];

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

	Optimizer(ArrayList<String> f, int q, boolean p, String o) {
		quality = q;
		files = f;
		preserve = p;
		outdir = o;
	}

	@Override
	public boolean hasChanged() {
		return true;
	}

	@Override
	public void run() {
		App.debug("starting optimization on " + files.size() + " files");
		for(String s: files) {
			if(!run)
				break;
			App.debug("optimizing " + s);
			compress(s);
		}
		files.clear();
		files = null;
		notifyObservers(null);
	}

	protected void compress(String file) {
		try {
			params[params.length - 1] = file;
			App.debug("starting optipng on " + file);
			Process optimizer = Runtime.getRuntime().exec(params);
			BufferedReader stdout = new BufferedReader(new InputStreamReader(optimizer.getInputStream()), 1024);
			try {
				optimizer.waitFor();
				String line = stdout.readLine();
//				App.debug(line);
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
	}

	protected abstract void parseOutput(String string);

	void abort() {
		run = false;
	}

	int count() {
		return files.size();
	}

	abstract String getExt();
	abstract String version();
}
