package net.teknoraver.imageoptimizer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Observable;

/*
 * Jpegoptim output sample is CSV format
 * foo.jpg,2048x1536,24bit,1000,200,80.00,optimized
 * bar.jpg,2048x1536,24bit,200,200,0.00,optimized
 * 
 * 0	filename
 * 1	resolution
 * 2	color depth
 * 3	original size
 * 4	optimized size
 * 5	compression ratio
 * 6	optimized/skipped/error
 */

public class Jpegoptim extends Observable implements Serializable, Runnable {
	private boolean compress;
	private int quality;
	private ArrayList<String> files;
	private String bin;
	private boolean preserve;
	private int threshold;
	private Process jpegoptim;
	private boolean run;

	public Jpegoptim(String b, ArrayList<String> checked, boolean l, int q, boolean p, int t) {
		compress = l;
		quality = q;
		files = checked;
		bin = b;
		preserve = p;
		threshold = t;
	}

	@Override
	public void run() {
		try {
			ArrayList<String> args = new ArrayList<String>(files.size() + 4);
			args.add(bin);
			args.add("-b");
			args.add("-T" + threshold);
			if(compress)
				args.add("-m" + quality);
			if(preserve)
				args.add("-p");
			args.addAll(files);
			System.out.println("starting jpegoptim");
			run = true;
			jpegoptim = Runtime.getRuntime().exec(args.toArray(new String[0]));
			BufferedReader stdout = new BufferedReader(new InputStreamReader(jpegoptim.getInputStream()));
			String line;
			while(run && (line = stdout.readLine()) != null)
				notifyObservers(line.split(","));
			stdout.close();
			jpegoptim.destroy();
		} catch(IOException ioe) {
			ioe.printStackTrace();
		}
		jpegoptim = null;
		files = null;
		notifyObservers(null);
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
}
