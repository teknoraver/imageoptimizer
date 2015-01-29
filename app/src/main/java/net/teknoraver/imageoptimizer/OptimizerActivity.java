package net.teknoraver.imageoptimizer;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Observable;
import java.util.Observer;
import java.util.Vector;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

public class OptimizerActivity extends Activity implements Observer {
	public static final String OPTIMIZER = "optim";
	private static final int NOTIFICATION_OPTIMIZING = 0;
	private ArrayList<Optimizer> optimizers;
	private TextView currentfile;
	private TextView origs;
	private TextView news;
	private TextView saved;
	private ProgressBar progress;
	private long origsize, newsize;
	private TextView currlabel;
	private Optimizer currentOptim;
	private ArrayList<String> compressed = new ArrayList<String>();
	private SQLiteDatabase db;

	@SuppressWarnings("unchecked")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.optimizer);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		optimizers = (ArrayList<Optimizer>)getIntent().getSerializableExtra(OPTIMIZER);

		db = openOrCreateDatabase("images", MODE_PRIVATE, null);

		// called via Intent
		if(optimizers == null) {
			Optimizer.setupBinaries(this);

			App.debug("Intent: " + getIntent().getAction());
			ArrayList<String> files = new ArrayList<String>();
			if(Intent.ACTION_SEND.equals(getIntent().getAction())) {
				String file = getRealPathFromURI((Uri)getIntent().getExtras().getParcelable(Intent.EXTRA_STREAM));
				if(file != null)
					files.add(file);
			} else if(Intent.ACTION_SEND_MULTIPLE.equals(getIntent().getAction()) && getIntent().getExtras().containsKey(Intent.EXTRA_STREAM))
				for(Parcelable uri : (ArrayList<Parcelable>)getIntent().getExtras().getParcelableArrayList(Intent.EXTRA_STREAM)) {
					String file = getRealPathFromURI((Uri)uri);
					if(file != null)
						files.add(file);
				}
			optimizers = createOptimizers(files);
			App.debug("optimizers: " + optimizers);
		}

		currlabel = (TextView)findViewById(R.id.currlabel);
		currentfile = (TextView)findViewById(R.id.currentfile);
		progress = (ProgressBar)findViewById(R.id.progress);
		origs = (TextView)findViewById(R.id.origsize);
		news = (TextView)findViewById(R.id.newsize);
		saved = (TextView)findViewById(R.id.saved);

		update(null, null);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		db.close();
		for(Optimizer optim : optimizers)
			optim.abort();
		if(currentOptim != null)
			currentOptim.abort();
		mediaRefresh();
	}

	private void updateNotification(String ext, int curr, int count) {
		NotificationCompat.Builder ncb =
			new NotificationCompat.Builder(this)
			.setSmallIcon(R.drawable.notify);

		if(ext == null) {
			ncb.setContentTitle(getString(R.string.notify_done))
			.setAutoCancel(true);
		} else {
			ncb.setProgress(count, curr, false)
			.setContentTitle(getString(R.string.notify_optimizing))
			.setContentText(getString(R.string.notify_progress, ext, curr, count))
			.setOngoing(true);
		}

		// The stack builder object will contain an artificial back stack for the
		// started Activity.
		// This ensures that navigating backward from the Activity leads out of
		// your application to the Home screen.
		ncb.setContentIntent(TaskStackBuilder.create(this)
			.addParentStack(OptimizerActivity.class)
			.addNextIntent(new Intent(this, OptimizerActivity.class))
			.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT));
		//ncb.setContentIntent(PendingIntent.getActivity(this, 0, new Intent(), 0));

		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		mNotificationManager.notify(NOTIFICATION_OPTIMIZING, ncb.build());
	}

	public String getRealPathFromURI(Uri contentUri) {
		String res = null;
		Cursor cursor = getContentResolver().query(contentUri, new String[]{MediaStore.Images.Media.DATA}, null, null, null);
		if(cursor == null)
			return null;
		if(cursor.moveToFirst())
			res = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA));
		cursor.close();
		return res;
	}

	@Override
	public void update(Observable observable, Object data) {
		new UpdateProgress().execute((Optimizer.Result)data);
	}

	private class UpdateProgress extends AsyncTask<Optimizer.Result, Void, Void> {
		Optimizer.Result res;
		@Override
		protected Void doInBackground(Optimizer.Result ... or) {
			res = or[0];
			return null;
		}

		@Override
		protected void onPostExecute(Void v) {
			if(res != null) {
				if(res.error) {
					App.debug("error optimizing: " + res.path);
					return;
				}

				ContentValues contentValues = new ContentValues();
				contentValues.put("path", res.path);
				db.insert("images", null, contentValues);
				origsize += res.origsize;
				if(res.compressed) {
					newsize += res.newsize;
					compressed.add(res.getName());
				} else
					newsize += res.origsize;

				currentfile.setText(res.getName());
				progress.setProgress(progress.getProgress() + 1);
				origs.setText(" " + sizeString(origsize));
				news.setText(" " + sizeString(newsize));
				if(origsize != 0)
					saved.setText(" " + (100 - (newsize * 100 / origsize) + " %"));

				updateNotification(currentOptim.getExt(), progress.getProgress(), currentOptim.count());
			} else {
				if(!optimizers.isEmpty()) {
					currentOptim = optimizers.remove(0);
					progress.setMax(currentOptim.count());
					progress.setProgress(0);

					currentOptim.addObserver(OptimizerActivity.this);
					new Thread(currentOptim).start();

					currlabel.setText(getString(R.string.optimizing, currentOptim.getExt()));
				} else { // all done
					currlabel.setText(R.string.done);
					currentfile.setText(null);
					getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
					updateNotification(null, 0, 0);
					mediaRefresh();
				}
			}
		}
	}

	private void mediaRefresh() {
		App.debug("refreshing paths");
		if(!compressed.isEmpty())
			MediaScannerConnection.scanFile(this, compressed.toArray(new String[0]), null, null);
	}

	static ArrayList<Optimizer> createOptimizers(ArrayList<String> files) {
		SharedPreferences pm = PreferenceManager.getDefaultSharedPreferences(App.getContext());
		Vector<String> jpgs = new Vector<String>(files.size());
		Vector<String> pngs = new Vector<String>(files.size());
		for(String file : files) {
			if(file.toLowerCase(Locale.US).endsWith(".jpg"))
				jpgs.add(file);
			else if(file.toLowerCase(Locale.US).endsWith(".png"))
				pngs.add(file);
		}

		ArrayList<Optimizer> optimizers = new ArrayList<Optimizer>(2);
		String destdir = null;
		if(pm.getBoolean(Settings.DEST, false))
			destdir = pm.getString(Settings.OUT, null);
		if(!jpgs.isEmpty()) {
			int quality = -1;
			if(pm.getBoolean(Settings.LOSSY, true))
				quality = pm.getInt(Settings.JPEGQ, 75);
			optimizers.add(new Jpegoptim(
				jpgs,
				quality,
				pm.getBoolean(Settings.TIMESTAMP, true),
				pm.getInt(Settings.THRESHOLD, 10),
				destdir));
		}
		if(!pngs.isEmpty())
			optimizers.add(new Optipng(
				pngs,
				pm.getInt(Settings.PNGQ, 1),
				pm.getBoolean(Settings.TIMESTAMP, true),
				destdir));

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
