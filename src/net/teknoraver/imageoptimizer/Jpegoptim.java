package net.teknoraver.imageoptimizer;

import java.io.File;
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
		new Thread(this).start();
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
			try {
				args[1] = file;
				Runtime.getRuntime().exec(args);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
