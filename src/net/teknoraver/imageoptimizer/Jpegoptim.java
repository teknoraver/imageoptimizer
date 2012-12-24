package net.teknoraver.imageoptimizer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Observable;

public class Jpegoptim extends Observable implements Runnable {
	private boolean compress;
	private int quality;
	private ArrayList<String> files;
	private String bin;

	public Jpegoptim(String b, ArrayList<String> checked, boolean l, int q) {
		compress = l;
		quality = q;
		files = checked;
		bin = b;
	}

	@Override
	public void run() {
		try {
			ArrayList<String> args = new ArrayList<String>(files.size() + 4);
			args.add(bin);
			args.add("-b");
			args.add("-T10");
			if(compress) {
				System.out.println("starting lossy jpegoptim with quality " + quality);
				args.add("-m" + quality);
			} else
				System.out.println("starting lossless jpegoptim");
			args.addAll(files);
			Process jpegoptim;
			jpegoptim = Runtime.getRuntime().exec(args.toArray(new String[0]));
			BufferedReader stdout = new BufferedReader(new InputStreamReader(jpegoptim.getInputStream()));
			String line;
			while((line = stdout.readLine()) != null) {
				String res[] = line.split(",");
				notifyObservers(res[0].substring(res[0].lastIndexOf('/') + 1));
			}
		} catch(IOException ioe) {
			ioe.printStackTrace();
		}
		notifyObservers(null);
	}

	@Override
	public boolean hasChanged() {
		return true;
	}
}
