package net.teknoraver.imageoptimizer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
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
	private static final long serialVersionUID = 3209219570096678985L;
	private boolean compress;
	private int quality;
	private ArrayList<String> files;
	private boolean preserve;
	private int threshold;
	private boolean run = true;
	private static final String BIN = App.getContext().getFilesDir() + "/jpegoptim";
	private static final int SPLIT = 100;

	public Jpegoptim(ArrayList<String> checked, boolean l, int q, boolean p, int t) {
		compress = l;
		quality = q;
		files = checked;
		preserve = p;
		threshold = t;
	}

	@Override
	public void run() {
		try {
			System.out.println("starting jpegoptim1 on " + files.size() + " files");
			for(int i = 0; run && i < files.size(); i += SPLIT) {
				List<String> sublist = files.subList(0, Math.min(SPLIT, files.size()));
				ArrayList<String> args = new ArrayList<String>(sublist.size() + 4);
				args.add(BIN);
				args.add("-b");
				args.add("-T" + threshold);
				if(compress)
					args.add("-m" + quality);
				if(preserve)
					args.add("-p");
				args.addAll(sublist);
				System.out.println("starting jpegoptim2 on " + sublist.size() + " files");
				Process jpegoptim = Runtime.getRuntime().exec(args.toArray(new String[0]));
				BufferedReader stdout = new BufferedReader(new InputStreamReader(jpegoptim.getInputStream()), 1024);
				String line;
				while(run && (line = stdout.readLine()) != null)
					notifyObservers(line.split(","));
				stdout.close();
				jpegoptim.destroy();
				sublist.clear();
			}
		} catch(IOException ioe) {
			ioe.printStackTrace();
		}
		files.clear();
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

	static String version() {
		try {
			Process ver = Runtime.getRuntime().exec(new String[]{BIN, "-bV"});
			byte buf[] = new byte[256];
			InputStream in = ver.getInputStream();
			String line = new String(buf, 0, in.read(buf));
			in.close();
			ver.destroy();
			return line.subSequence(0, line.indexOf(',')).toString();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
}
