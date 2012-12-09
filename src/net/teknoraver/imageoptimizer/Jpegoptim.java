package net.teknoraver.imageoptimizer;

import java.io.File;
import java.util.ArrayList;

public class Jpegoptim {

	public Jpegoptim(ArrayList<File> all, boolean lossy, int q) {
		if(lossy)
			System.out.println("starting lossless jpegoptim");
		else
			System.out.println("starting lossy jpegoptim with quality " + q);
	}
}
