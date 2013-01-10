package net.teknoraver.imageoptimizer;

import java.util.ArrayList;
import java.util.List;

class Optipng extends Optimizer {
	private static final long serialVersionUID = 3211055437537470542L;

	Optipng(ArrayList<String> f, int q, boolean p, int t) {
		super(f, q, p, t);
	}

	@Override
	protected void compress(List<String> sublist) {
		for(int i = 0; i < sublist.size(); i++) {
			try {
				Thread.sleep((long) (Math.random() * 800 + 200));
			} catch (InterruptedException e) { }
			notifyObservers(new String[]{sublist.get(i),"2048x1536","24bit",(int)(Math.random() * 1000 + 200) + "",(int)(Math.random() * 200 + 50) + "",(int)(Math.random() * 80 + 20) + "","optimized"});
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
