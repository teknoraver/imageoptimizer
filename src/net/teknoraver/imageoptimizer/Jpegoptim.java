package net.teknoraver.imageoptimizer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Observable;

public class Jpegoptim extends Observable implements Runnable {
	private boolean lossy;
	private int quality;
	private ArrayList<String> files;
	private String bin;

	public Jpegoptim(String b, ArrayList<String> checked, boolean l, int q) {
		lossy = l;
		quality = q;
		files = checked;
		bin = b;
	}

	@Override
	public void run() {
		String args[];
		if(lossy) {
			System.out.println("starting lossless jpegoptim");
			args = new String[]{bin, null};
		} else {
			System.out.println("starting lossy jpegoptim with quality " + quality);
			args = new String[]{bin, null, "-T10", "-m" + quality};
		}
		for(String file : files) {
			notifyObservers(file);
			try {
				Thread.sleep(1000);
				args[1] = file;
				Runtime.getRuntime().exec(args);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		notifyObservers(null);
	}

	@Override
	public boolean hasChanged() {
		return true;
	}
}
