package net.teknoraver.imageoptimizer;

import java.util.Observable;
import java.util.Observer;

import android.os.Bundle;
import android.os.Handler;
import android.app.Activity;
import android.widget.ProgressBar;
import android.widget.TextView;

public class Compress extends Activity implements Observer, Runnable {

	public static final String OPTIMIZER = "optim";
	private Jpegoptim jo;
	private String msg;
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
		setContentView(R.layout.compress);

		jo = (Jpegoptim)getIntent().getSerializableExtra(OPTIMIZER);

		currentfile = (TextView)findViewById(R.id.currentfile);
		progress = (ProgressBar)findViewById(R.id.progress);
		origs = (TextView)findViewById(R.id.origsize);
		news = (TextView)findViewById(R.id.newsize);
		saved = (TextView)findViewById(R.id.saved);

		progress.setMax(jo.count());

		jo.addObserver(this);
		new Thread(jo).start();
	}

	@Override
	public void update(Observable observable, Object data) {
		if(data == null)
			return;

		String res[] = (String[])data;
		msg = res[0].substring(res[0].lastIndexOf('/') + 1);
		origsize += Integer.parseInt(res[3]);
		newsize += Integer.parseInt(res[4]);
		handler.post(this);
	}

	@Override
	public void run() {
		currentfile.setText(msg);
		progress.setProgress(progress.getProgress() + 1);
		origs.setText(" " + sizeString(origsize));
		news.setText(" " + sizeString(newsize));
		saved.setText(" " + (100 - (newsize * 100 / origsize) + " %"));
	}

	@Override
	protected void onStop() {
		super.onStop();

		jo.abort();
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
