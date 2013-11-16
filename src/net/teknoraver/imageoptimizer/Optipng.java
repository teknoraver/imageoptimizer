package net.teknoraver.imageoptimizer;

import java.util.ArrayList;

/*
 * Optipng output sample is CSV format
 * 222039.png,1238,1238,0,skipped
 * 091514.png,123786,120811,97,optimized
 *
 * 0	filename
 * 1	original size
 * 2	optimized size
 * 3	compression ratio
 * 4	optimized/skipped/error
 */

class Optipng extends Optimizer {
	private static final long serialVersionUID = 3211055437537470542L;
	private static final String BIN = App.getContext().getFilesDir() + "/optipng";

	Optipng(ArrayList<String> f, int q, boolean p, String o) {
		super(f, q, p, o);

		ArrayList<String> args = new ArrayList<String>();
		args.add(BIN);
		args.add("-csv");
		args.add("-o" + quality);
		if(preserve)
			args.add("-preserve");
		if(outdir != null) {
			args.add("-clobber");
			args.add("-dir");
			args.add(outdir);
		}

		params = new String[args.size() + 1];
		args.toArray(params);
	}

	@Override
	protected void parseOutput(String line) {
		String res[] = line.split(",");
		if(res[4].equals("error"))
			notifyObservers(new Result());
		else
			notifyObservers(new Result(res[0], Integer.parseInt(res[1]), Integer.parseInt(res[2]), res[4].equals("optimized")));
	}

	@Override
	String getExt() {
		return "png";
	}

	@Override
	String version() {
		return "0.7.4";
	}

}
