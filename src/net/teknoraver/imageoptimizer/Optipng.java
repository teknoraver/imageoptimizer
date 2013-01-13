package net.teknoraver.imageoptimizer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

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

	Optipng(ArrayList<String> f, int q, boolean p) {
		super(f, q, p);
	}

	@Override
	protected void compress(List<String> sublist) {
		try {
			ArrayList<String> args = new ArrayList<String>(sublist.size() + 4);
			args.add(BIN);
			args.add("-csv");
			args.add("-o" + quality);
			args.addAll(sublist);
			App.debug("starting optipng on " + sublist.size() + " files");
			Process optipng = Runtime.getRuntime().exec(args.toArray(new String[0]));
			BufferedReader stdout = new BufferedReader(new InputStreamReader(optipng.getInputStream()), 1024);
			String line;
			while(run && (line = stdout.readLine()) != null) {
				try {
//					App.debug(line);
					String res[] = line.split(",");
					if(res[4].equals("error"))
						notifyObservers(new Result());
					else
						notifyObservers(new Result(res[0], Integer.parseInt(res[1]), Integer.parseInt(res[2]), res[4].equals("optimized")));
				} catch(RuntimeException r) {
					notifyObservers(new Result());
				}
			}
			stdout.close();
			optipng.destroy();
		} catch(IOException ioe) {
			ioe.printStackTrace();
		}
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
