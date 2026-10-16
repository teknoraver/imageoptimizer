package net.teknoraver.imageoptimizer;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Vector;

import org.json.JSONException;
import org.json.JSONObject;

/*
 * Oxipng --json output sample, on a single line
 * {"results":[{"input":"foo.png","status":"success","output":"foo.png","insize":123786,"outsize":120811}]}
 * {"results":[{"input":"bar.png","status":"error","error":"..."}]}
 *
 * If the file can't be shrunk, outsize equals insize and the file is left untouched.
 */

class Oxipng extends Optimizer {
	private static final long serialVersionUID = 3211055437537470542L;
	private static final String BIN = App.getContext().getApplicationInfo().nativeLibraryDir + "/liboxipng.so";
	static final int MAX_LEVEL = 7;

	Oxipng(Vector<String> f, int q, boolean p) {
		super(f, q, p);

		ArrayList<String> args = new ArrayList<String>();
		args.add(BIN);
		args.add("--json");
		args.add("-q");
		// files are already processed in parallel, one per CPU, by Optimizer.run()
		args.add("-t1");
		args.add("-o");
		args.add(quality >= MAX_LEVEL ? "max" : String.valueOf(quality));
		if(preserve)
			args.add("-p");

		params = new String[args.size() + 1];
		args.toArray(params);
	}

	@Override
	protected Result parseOutput(String originalUri, String line) {
		try {
			JSONObject res = new JSONObject(line).getJSONArray("results").getJSONObject(0);
			if(!res.getString("status").equals("success"))
				return new Result();
			long insize = res.getLong("insize");
			long outsize = res.getLong("outsize");
			return new Result(originalUri, insize, outsize, outsize < insize);
		} catch (JSONException | NullPointerException e) {
			e.printStackTrace();
			return new Result();
		}
	}

	@Override
	String getExt() {
		return "png";
	}

	@Override
	String version() {
		try {
			// prints "oxipng 10.2.1"
			Process ver = Runtime.getRuntime().exec(new String[]{BIN, "-V"});
			byte buf[] = new byte[256];
			InputStream in = ver.getInputStream();
			String line = new String(buf, 0, in.read(buf)).trim();
			in.close();
			ver.destroy();
			return line.substring(line.indexOf(' ') + 1);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
}
