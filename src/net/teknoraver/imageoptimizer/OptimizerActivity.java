package net.teknoraver.imageoptimizer;

import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

public class OptimizerActivity extends Activity implements Observer, Runnable {

	public static final String OPTIMIZER = "optim";
	private ArrayList<Optimizer> optimizers;
	private String res[];
	private Handler handler = new Handler();
	private TextView currentfile;
	private TextView origs;
	private TextView news;
	private TextView saved;
	private ProgressBar progress;
	private long origsize, newsize;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.optimizer);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		optimizers = (ArrayList<Optimizer>)getIntent().getSerializableExtra(OPTIMIZER);

		currentfile = (TextView)findViewById(R.id.currentfile);
		progress = (ProgressBar)findViewById(R.id.progress);
		origs = (TextView)findViewById(R.id.origsize);
		news = (TextView)findViewById(R.id.newsize);
		saved = (TextView)findViewById(R.id.saved);

		for(Optimizer optim : optimizers) {
			progress.setMax(optim.count());
	
			optim.addObserver(this);
			new Thread(optim).start();
		}
	}

	@Override
	public void update(Observable observable, Object data) {
		if(data != null) {
			res = (String[])data;
			if(res[6].equals("error"))
				return;
			origsize += Integer.parseInt(res[3]);
			if(res[6].equals("optimized"))
				newsize += Integer.parseInt(res[4]);
			else
				newsize += Integer.parseInt(res[3]);
		} else
			res = null;
		handler.post(this);
	}

	@Override
	public void run() {
		if(res != null)
			currentfile.setText(" " + res[0].substring(res[0].lastIndexOf('/') + 1));
		else {
			((TextView)findViewById(R.id.currlabel)).setText(R.string.done);
			currentfile.setText(null);
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
		progress.setProgress(progress.getProgress() + 1);
		origs.setText(" " + sizeString(origsize));
		news.setText(" " + sizeString(newsize));
		if(origsize != 0)
			saved.setText(" " + (100 - (newsize * 100 / origsize) + " %"));
	}

	@Override
	protected void onStop() {
		super.onStop();

		for(Optimizer optim : optimizers)
			optim.abort();
	}

	public static String sizeString(long len) {
		if(len < 1 << 10)
			return len + " bytes";
		else if(len < 1 << 20)
			return ((int)(len / 10.24)) / 100.0 + " Kb";
		else
			return ((int)(len / 10485.76)) / 100.0 + " Mb";
	}
	
}
