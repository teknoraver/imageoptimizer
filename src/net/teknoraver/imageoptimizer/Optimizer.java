package net.teknoraver.imageoptimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Observable;

abstract class Optimizer extends Observable implements Serializable, Runnable {
	private static final long serialVersionUID = 2968942827262809844L;

	static String EXT; 

	protected int quality;
	protected ArrayList<String> files;
	protected boolean preserve;
	protected int threshold;
	protected boolean run = true;


	public Optimizer(ArrayList<String> f, int q, boolean p, int t) {
		quality = q;
		files = f;
		preserve = p;
		threshold = t;
	}

	@Override
	public boolean hasChanged() {
		return true;
	}

	void abort() {
		run = false;
	}

	int count() {
		return files.size();
	}

	abstract String version();
}
