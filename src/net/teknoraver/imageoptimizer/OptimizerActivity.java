package net.teknoraver.imageoptimizer;

import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

public class OptimizerActivity extends Activity implements Observer, Runnable {

	public static final String OPTIMIZER = "optim";
	private ArrayList<Optimizer> optimizers;
	private Optimizer.Result res;
	private Handler handler = new Handler();
	private TextView currentfile;
	private TextView origs;
	private TextView news;
	private TextView saved;
	private ProgressBar progress;
	private long origsize, newsize;
	private TextView currlabel;

	@SuppressWarnings("unchecked")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.optimizer);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		optimizers = (ArrayList<Optimizer>)getIntent().getSerializableExtra(OPTIMIZER);

		currlabel = (TextView)findViewById(R.id.currlabel);
		currentfile = (TextView)findViewById(R.id.currentfile);
		progress = (ProgressBar)findViewById(R.id.progress);
		origs = (TextView)findViewById(R.id.origsize);
		news = (TextView)findViewById(R.id.newsize);
		saved = (TextView)findViewById(R.id.saved);

		update(null, null);
	}

	@Override
	public void update(Observable observable, Object data) {
		if(data != null) {
			res = (Optimizer.Result)data;
			if(res.error)
				return;
			origsize += res.origsize;
			if(res.compressed)
				newsize += res.newsize;
			else
				newsize += res.origsize;
		} else {
			if(!optimizers.isEmpty()) {
				Optimizer optim = optimizers.get(0);
				progress.setMax(optim.count());
				progress.setProgress(0);

				optim.addObserver(this);
				new Thread(optim).start();
			}
			res = null; // go to next optimizer
		}
		handler.post(this);
	}

	@Override
	public void run() {
		if(res != null)
			currentfile.setText(" " + res.path.substring(res.path.lastIndexOf('/') + 1));
		else {
			// next optimizer
			if(!optimizers.isEmpty()) {
				Optimizer optim = optimizers.remove(0);
				currlabel.setText(getString(R.string.optimizing) + ' ' + optim.getExt() + " files");
			} else { // all done
				currlabel.setText(R.string.done);
				currentfile.setText(null);
				getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			}
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
