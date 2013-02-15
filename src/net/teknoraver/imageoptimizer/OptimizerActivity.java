package net.teknoraver.imageoptimizer;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Observable;
import java.util.Observer;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
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
	private Optimizer currentOptim;
	private boolean refreshed;

	@SuppressWarnings("unchecked")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.optimizer);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		optimizers = (ArrayList<Optimizer>)getIntent().getSerializableExtra(OPTIMIZER);

		// called via Intent
		if(optimizers == null) {
			ArrayList<String> files = new ArrayList<String>();
			if(Intent.ACTION_SEND.equals(getIntent().getAction()))
				files.add(getRealPathFromURI((Uri)getIntent().getExtras().getParcelable(Intent.EXTRA_STREAM)));
			else if(Intent.ACTION_SEND_MULTIPLE.equals(getIntent().getAction()) && getIntent().getExtras().containsKey(Intent.EXTRA_STREAM))
				for(Parcelable uri : (ArrayList<Parcelable>)getIntent().getExtras().getParcelableArrayList(Intent.EXTRA_STREAM))
					files.add(getRealPathFromURI((Uri)uri));
			optimizers = createOptimizers(files);
		}

		currlabel = (TextView)findViewById(R.id.currlabel);
		currentfile = (TextView)findViewById(R.id.currentfile);
		progress = (ProgressBar)findViewById(R.id.progress);
		origs = (TextView)findViewById(R.id.origsize);
		news = (TextView)findViewById(R.id.newsize);
		saved = (TextView)findViewById(R.id.saved);

		update(null, null);
	}

	public String getRealPathFromURI(Uri contentUri) {
		String res = null;
		Cursor cursor = getContentResolver().query(contentUri, new String[]{MediaStore.Images.Media.DATA}, null, null, null);
		if(cursor.moveToFirst())
			res = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA));
		cursor.close();
		return res;
	}

	@Override
	public void update(Observable observable, Object data) {
		res = (Optimizer.Result)data;
		handler.post(this);
	}

	@Override
	public void run() {
		if(res != null) {
			if(res.error) {
				App.debug("error optimizing: " + res.path);
				return;
			}

			origsize += res.origsize;
			if(res.compressed)
				newsize += res.newsize;
			else
				newsize += res.origsize;

			currentfile.setText(res.getName());
			progress.setProgress(progress.getProgress() + 1);
			origs.setText(" " + sizeString(origsize));
			news.setText(" " + sizeString(newsize));
			if(origsize != 0)
				saved.setText(" " + (100 - (newsize * 100 / origsize) + " %"));
		} else {
			if(!optimizers.isEmpty()) {
				currentOptim = optimizers.remove(0);
				progress.setMax(currentOptim.count());
				progress.setProgress(0);

				currentOptim.addObserver(this);
				new Thread(currentOptim).start();

				currlabel.setText(getString(R.string.optimizing, currentOptim.getExt()));
			} else { // all done
				currlabel.setText(R.string.done);
				currentfile.setText(null);
				getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
				mediaRefresh();
			}
		}
	}

	@Override
	protected void onPause() {
		super.onPause();

		for(Optimizer optim : optimizers)
			optim.abort();
		if(currentOptim != null)
			currentOptim.abort();

		mediaRefresh();
	}

	private void mediaRefresh() {
		if(!refreshed) {
			App.debug("refreshing paths");
			for(String path : PathSelector.getPaths())
				sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.parse("file://" + path)));
			refreshed = true;
		}
	}

	static ArrayList<Optimizer> createOptimizers(ArrayList<String> files) {
		SharedPreferences pm = PreferenceManager.getDefaultSharedPreferences(App.getContext());
		ArrayList<String> jpgs = new ArrayList<String>(files.size());
		ArrayList<String> pngs = new ArrayList<String>(files.size());
		for(String file : files) {
			if(file.toLowerCase(Locale.US).endsWith(".jpg"))
				jpgs.add(file);
			else if(file.toLowerCase(Locale.US).endsWith(".png"))
				pngs.add(file);
		}
		if(jpgs.isEmpty() && pngs.isEmpty())
			return null;
	
		ArrayList<Optimizer> optimizers = new ArrayList<Optimizer>(2);
		if(!jpgs.isEmpty()) {
			int quality = -1;
			if(pm.getBoolean(Settings.LOSSY, true))
				quality = pm.getInt(Settings.JPEGQ, 75);
			optimizers.add(new Jpegoptim(	jpgs,
							quality,
							pm.getBoolean(Settings.TIMESTAMP, true),
							pm.getInt(Settings.THRESHOLD, 10)));
		}
		if(!pngs.isEmpty())
			optimizers.add(new Optipng(pngs, pm.getInt(Settings.PNGQ, 1), pm.getBoolean(Settings.TIMESTAMP, true)));
		return optimizers;
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
