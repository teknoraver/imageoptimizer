package net.teknoraver.imageoptimizer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

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

public class Jpegoptim extends Optimizer {
	private static final long serialVersionUID = -2673614600679067178L;
	private static final String BIN = App.getContext().getFilesDir() + "/jpegoptim";
	private boolean compress;

	public Jpegoptim(ArrayList<String> f, int q, boolean p, int t) {
		super(f, q, p, t);
		if(quality >= 0)
			compress = true;
	}

	@Override
	public void compress(List<String> sublist) {
		try {
			ArrayList<String> args = new ArrayList<String>(sublist.size() + 4);
			args.add(BIN);
			args.add("-b");
			args.add("-T" + threshold);
			if(compress)
				args.add("-m" + quality);
			if(preserve)
				args.add("-p");
			args.addAll(sublist);
			App.debug("starting jpegoptim2 on " + sublist.size() + " files");
			Process jpegoptim = Runtime.getRuntime().exec(args.toArray(new String[0]));
			BufferedReader stdout = new BufferedReader(new InputStreamReader(jpegoptim.getInputStream()), 1024);
			String line;
			while(run && (line = stdout.readLine()) != null)
				notifyObservers(line.split(","));
			stdout.close();
			jpegoptim.destroy();
		} catch(IOException ioe) {
			ioe.printStackTrace();
		}
	}

	@Override
	String version() {
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

	@Override
	String getExt() {
		return "jpg";
	}
}
