package net.teknoraver.imageoptimizer;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Vector;

/*
 * Jpegoptim output sample is CSV format (RFC 4180, so the filename may be quoted and contain commas)
 * foo.jpg,2048x1536,24bit,N,1000,200,80.00,optimized
 * "b,ar.jpg",2048x1536,24bit,P,200,200,0.00,skipped
 * baz.jpg,,,,,error
 *
 * 0	filename
 * 1	resolution
 * 2	color depth
 * 3	N(ormal)/P(rogressive)
 * 4	original size
 * 5	optimized size
 * 6	compression ratio
 * 7	optimized/skipped/error
 *
 * Fields are indexed from the end, as the filename is the only one which can contain commas.
 */

class Jpegoptim extends Optimizer {
	private static final long serialVersionUID = -2673614600679067178L;
	private static final String BIN = App.getContext().getApplicationInfo().nativeLibraryDir + "/libjpegoptim.so";
	private boolean compress;
	private int threshold;

	Jpegoptim(Vector<String> f, int q, boolean p, int t) {
		super(f, q, p);
		if(quality >= 0)
			compress = true;
		threshold = t;

		ArrayList<String> args = new ArrayList<String>();
		args.add(BIN);
		args.add("-b");
		args.add("-T" + threshold);
		if(compress)
			args.add("-m" + quality);
		if(preserve)
			args.add("-p");

		params = new String[args.size() + 1];
		args.toArray(params);
	}

	@Override
	protected Result parseOutput(String originalUri, String line) {
//		App.debug("jpegoptim: '" + line + "'");
		String res[] = line.split(",");
		int n = res.length;
		if(n < 8 || res[n - 1].equals("error"))
			return new Result();
		return new Result(originalUri, Long.parseLong(res[n - 4]), Long.parseLong(res[n - 3]), res[n - 1].equals("optimized"));
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
