package net.teknoraver.imageoptimizer;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Observable;
import java.util.Observer;
import java.util.Vector;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class OptimizerActivity extends Activity implements Observer {
	public static final String OPTIMIZER = "optim";
	private static final int NOTIFICATION_OPTIMIZING = 0;
	private static final int WRITE_REQUEST = 1;
	private static final int NOTIFICATION_REQUEST = 2;
	private ArrayList<Optimizer> optimizers;
	private TextView currentfile;
	private TextView origs;
	private TextView news;
	private TextView saved;
	private ProgressBar progress;
	private long origsize, newsize;
	private TextView currlabel;
	private Optimizer currentOptim;
	private SQLiteDatabase db;
	/** True from the start of a batch (including the write consent dialog) until it's done. */
	private boolean running;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.optimizer);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		db = openOrCreateDatabase("images", MODE_PRIVATE, null);
		optimizers = optimizersFromIntent(getIntent());

		currlabel = (TextView)findViewById(R.id.currlabel);
		currentfile = (TextView)findViewById(R.id.currentfile);
		progress = (ProgressBar)findViewById(R.id.progress);
		origs = (TextView)findViewById(R.id.origsize);
		news = (TextView)findViewById(R.id.newsize);
		saved = (TextView)findViewById(R.id.saved);

		running = true;
		maybeRequestWriteAccessThenStart();
	}

	/**
	 * Being singleTop, images shared while this activity is on top land here instead of
	 * in onCreate(). Intents with no images, e.g. from the notification, just bring it up.
	 */
	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		ArrayList<Optimizer> newOptimizers = optimizersFromIntent(intent);
		if(newOptimizers.isEmpty())
			return;
		if(running) {
			Toast.makeText(this, R.string.busy, Toast.LENGTH_LONG).show();
			return;
		}

		setIntent(intent);
		optimizers = newOptimizers;
		origsize = newsize = 0;
		progress.setProgress(0);
		currentfile.setText(null);
		origs.setText(null);
		news.setText(null);
		saved.setText(null);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		running = true;
		maybeRequestWriteAccessThenStart();
	}

	/** The optimizers prepared by Browser, or new ones for the images shared via SEND/SEND_MULTIPLE. */
	@SuppressWarnings("unchecked")
	private ArrayList<Optimizer> optimizersFromIntent(Intent intent) {
		ArrayList<Optimizer> o = (ArrayList<Optimizer>)intent.getSerializableExtra(OPTIMIZER);
		if(o != null)
			return o;

		// called via Intent
		Optimizer.setupBinaries(this);

		App.debug("Intent: " + intent.getAction());
		ArrayList<String> files = new ArrayList<String>();
		if(Intent.ACTION_SEND.equals(intent.getAction())) {
			Uri uri = (Uri)intent.getExtras().getParcelable(Intent.EXTRA_STREAM);
			if(uri != null)
				files.add(uri.toString());
		} else if(Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction()) && intent.getExtras().containsKey(Intent.EXTRA_STREAM))
			for(Parcelable p : (ArrayList<Parcelable>)intent.getExtras().getParcelableArrayList(Intent.EXTRA_STREAM))
				if(p instanceof Uri)
					files.add(p.toString());
		o = createOptimizers(files);
		App.debug("optimizers: " + o);
		return o;
	}

	/**
	 * On Android 11+, overwriting MediaStore images this app doesn't own requires
	 * up-front user consent for the whole batch. Below API 30 no such consent is needed.
	 */
	private void maybeRequestWriteAccessThenStart() {
		if(Build.VERSION.SDK_INT >= 30) {
			ArrayList<Uri> uris = new ArrayList<Uri>();
			for(Optimizer o : optimizers)
				for(String s : o.files) {
					Uri uri = Uri.parse(s);
					// createWriteRequest() only accepts MediaStore rows referenced by a
					// specific id; SAF documents already have a persisted write grant and
					// must not be included here, or the whole call throws.
					if(MediaStore.AUTHORITY.equals(uri.getAuthority()))
						uris.add(uri);
				}
			if(!uris.isEmpty()) {
				PendingIntent pi = MediaStore.createWriteRequest(getContentResolver(), uris);
				try {
					startIntentSenderForResult(pi.getIntentSender(), WRITE_REQUEST, null, 0, 0, 0);
					return;
				} catch (IntentSender.SendIntentException e) {
					e.printStackTrace();
				}
			}
		}
		start();
	}

	/**
	 * Starts the batch, asking first for the Android 13+ notification permission. It's
	 * requested only now so that it never stacks over the write consent dialog; if it's
	 * denied the progress notification just doesn't show.
	 */
	private void start() {
		if(Build.VERSION.SDK_INT >= 33
		    && ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
		       != PackageManager.PERMISSION_GRANTED)
			ActivityCompat.requestPermissions(this,
				new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_REQUEST);
		update(null, null);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(requestCode == WRITE_REQUEST) {
			// proceed regardless of the user's choice: declined items simply fail to write back
			start();
			return;
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		db.close();
		for(Optimizer optim : optimizers)
			optim.abort();
		if(currentOptim != null)
			currentOptim.abort();
	}

	private void updateNotification(String ext, int curr, int count) {
		NotificationCompat.Builder ncb =
			new NotificationCompat.Builder(this, App.CHANNEL)
			.setSmallIcon(R.drawable.notify)
			.setOnlyAlertOnce(true);

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
			.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		mNotificationManager.notify(NOTIFICATION_OPTIMIZING, ncb.build());
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
				db.insertWithOnConflict("images", null, contentValues, SQLiteDatabase.CONFLICT_IGNORE);
				origsize += res.origsize;
				if(res.compressed) {
					newsize += res.newsize;
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
					running = false;
					currlabel.setText(R.string.done);
					currentfile.setText(null);
					getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
					updateNotification(null, 0, 0);
				}
			}
		}
	}

	static ArrayList<Optimizer> createOptimizers(ArrayList<String> files) {
		ContentResolver cr = App.getContext().getContentResolver();
		SharedPreferences pm = PreferenceManager.getDefaultSharedPreferences(App.getContext());
		Vector<String> jpgs = new Vector<String>(files.size());
		Vector<String> pngs = new Vector<String>(files.size());
		for(String file : files) {
			String name = ContentUtil.displayName(cr, Uri.parse(file)).toLowerCase(Locale.US);
			if(name.endsWith(".jpg") || name.endsWith(".jpeg"))
				jpgs.add(file);
			else if(name.endsWith(".png"))
				pngs.add(file);
		}

		ArrayList<Optimizer> optimizers = new ArrayList<Optimizer>(2);
		if(!jpgs.isEmpty()) {
			int quality = -1;
			if(pm.getBoolean(Settings.LOSSY, true))
				quality = pm.getInt(Settings.JPEGQ, 75);
			optimizers.add(new Jpegoptim(
				jpgs,
				quality,
				pm.getBoolean(Settings.TIMESTAMP, true),
				pm.getInt(Settings.THRESHOLD, 10)));
		}
		if(!pngs.isEmpty())
			optimizers.add(new Oxipng(
				pngs,
				// old optipng based versions saved levels up to 8
				Math.min(pm.getInt(Settings.PNGQ, 2), Oxipng.MAX_LEVEL),
				pm.getBoolean(Settings.TIMESTAMP, true)));

		return optimizers;
	}

	public static String sizeString(long len) {
		if(len < 1 << 10)
			return len + " bytes";
		else if(len < 1 << 20)
			return ((int)(len / 10.24)) / 100.0 + " Kb";
		else if(len < 1 << 30)
			return ((int)(len / 10485.76)) / 100.0 + " Mb";
		else
			return ((int)(len / 107374182.4)) / 100.0 + " Gb";
	}

}
