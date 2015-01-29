package net.teknoraver.imageoptimizer;

import java.io.File;
import java.io.FileFilter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import android.app.Activity;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

class Image {
	String path;
	boolean compress;
	long size;
	long date;
	Bitmap thumb;
	private static final DateFormat dateParser = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.ENGLISH);

	Image(String f, long s, long d, boolean c) {
		path = f;
		size = s;
		date = d;
		compress = c;
	}

	String getName() {
		return path.substring(path.lastIndexOf('/') + 1);
	}

	long getDate() {
		try {
			ExifInterface ei = new ExifInterface(path);
			String exifDate = ei.getAttribute(ExifInterface.TAG_DATETIME);
			return dateParser.parse(exifDate).getTime();
		} catch (Exception e) { }
		return date;
	}
}

class Sorter implements Comparator<Image> {
	private static final int NAME = 0;
	private static final int SIZE = 1;
	private static final int DATE = 2;
	private int mode;
	private boolean asc;
	private SharedPreferences pm = PreferenceManager.getDefaultSharedPreferences(App.getContext());

	Sorter() {
		asc = pm.getBoolean(Settings.ASC, false);
		mode = pm.getInt(Settings.MODE, DATE);
	}

	@Override
	public int compare(Image lhs, Image rhs) {
		if(!asc) {
			Image tmp = lhs;
			lhs = rhs;
			rhs = tmp;
		}
		switch(mode) {
		case NAME:
			return lhs.path.compareToIgnoreCase(rhs.path);
		case SIZE:
			return (int)Math.signum(lhs.size - rhs.size);
		case DATE:
			return (int)Math.signum(lhs.getDate() - rhs.getDate());
		}
		return 0;
	}

	Sorter by(int newmode) {
		newmode = id2mode(newmode);
		// invert operands if the same order is applied for the second time
		asc = mode == newmode ? !asc : true;
		mode = newmode;
		Editor editor = pm.edit();
		editor.putBoolean(Settings.ASC, asc);
		editor.putInt(Settings.MODE, mode);
		editor.commit();
		return this;
	}

	private int id2mode(int newmode) {
		switch(newmode) {
		case R.id.sortby_name:
			return NAME;
		case R.id.sortby_size:
			return SIZE;
		case R.id.sortby_date:
		default:
			return DATE;
		}
	}
}

public class Browser extends ListActivity implements FileFilter, OnClickListener {
	private ProgressDialog pd;
	private ListView list;
	private ArrayList<Image> all = new ArrayList<Image>();
	private Sorter sorter = new Sorter();
	private Button go;
	private SharedPreferences pm;
	private SQLiteDatabase db;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.browser);

		list = getListView();

		go = (Button)findViewById(R.id.optimize);
		go.setOnClickListener(this);

		AdView adView = (AdView)findViewById(R.id.adView);
		adView.loadAd(new AdRequest.Builder().build());

		pm = PreferenceManager.getDefaultSharedPreferences(this);

		Optimizer.setupBinaries(this);
	}

	private void startScan() {
		go.setEnabled(false);
		setListAdapter(null);
		pd = ProgressDialog.show(this, getString(R.string.scanning_title), getString(R.string.scanning_txt));
		all.clear();
		db = openOrCreateDatabase("images", MODE_PRIVATE, null);
		db.execSQL("CREATE TABLE IF NOT EXISTS images(path TEXT PRIMARY KEY)");
		new FileScanner().execute();
	}

	private class FileScanner extends AsyncTask<Void, Void, Void> {
		@Override
		protected Void doInBackground(Void ... v) {
			for(String path : PathSelector.getPaths()) {
				File file = new File(path);
				if(file.isDirectory())
					scan(file);
			}
			Collections.sort(all, new Sorter());

			return null;
		}

		@Override
		protected void onPostExecute(Void v) {
			db.close();
			setListAdapter(new ImageAdapter(Browser.this, all));
			pd.dismiss();
			Toast.makeText(Browser.this, R.string.hint, Toast.LENGTH_LONG).show();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		startScan();
		refresh();
	}

	private void refresh() {
		if(getListAdapter() != null)
			((ImageAdapter)getListAdapter()).notifyDataSetChanged();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.browser, menu);
		return true;
	}

	private void scan(File file) {
		File children[] = file.listFiles(this);
		if (children != null)
			for (File child : children)
				if(child.isDirectory())
					scan(child);
				else
					all.add(new Image(child.getAbsolutePath(), child.length(), child.lastModified(),
						db.query("images", new String[]{"path"}, "path = '" + child.getAbsolutePath() + "'", null, null, null, null).getCount() == 0));
	}

	@Override
	public boolean accept(File pathname) {
		if(pathname.getName().charAt(0) == '.')
			return false;
		if(pathname.isDirectory())
			return true;
		return	pm.getBoolean(Settings.JPG, true) && pathname.toString().toLowerCase(Locale.US).endsWith(".jpg") ||
			pm.getBoolean(Settings.PNG, true) && pathname.toString().toLowerCase(Locale.US).endsWith(".png");
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		case R.id.menu_settings:
			startActivity(new Intent(this, Settings.class));
			return true;
		case R.id.menu_all:
		case R.id.menu_none:
			for(int i = 0; i < list.getCount(); i++) {
				Image row = (Image)list.getItemAtPosition(i);
				row.compress = item.getItemId() == R.id.menu_all;
			}
			for(int i = 0; i < list.getChildCount(); i++) {
				View row = list.getChildAt(i);
				ImageView b = (ImageView)row.findViewById(R.id.grid_item_image);
				int left = b.getPaddingLeft();
				int top = b.getPaddingTop();
				int right = b.getPaddingRight();
				int bottom = b.getPaddingBottom();
				if(item.getItemId() == R.id.menu_all)
					b.setBackgroundResource(R.drawable.borderh);
				else
					b.setBackgroundResource(R.drawable.border);
				b.setPadding(left, top, right, bottom);
			}
			go.setEnabled(item.getItemId() == R.id.menu_all);
//			((ImageAdapter)list.getAdapter()).notifyDataSetChanged();
			return true;
		case R.id.sortby_size:
		case R.id.sortby_name:
		case R.id.sortby_date:
			Collections.sort(all, sorter.by(item.getItemId()));
			refresh();
			return true;
		case R.id.menu_refresh:
			startScan();
			return true;
		}
		return super.onContextItemSelected(item);
	}

	@Override
	public void onClick(View v) {
		ArrayList<String> files = new ArrayList<String>(list.getCount());
		for(int i = 0; i < list.getCount(); i++) {
			Image row = (Image)list.getItemAtPosition(i);
			if(row.compress)
				files.add(row.path);
		}

		ArrayList<Optimizer> optimizers = OptimizerActivity.createOptimizers(files);

		if(optimizers != null)
			startActivity(new Intent(this, OptimizerActivity.class)
				.putExtra(OptimizerActivity.OPTIMIZER, optimizers));
	}


	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		Image i = (Image)list.getItemAtPosition(position);
		i.compress = !i.compress;

		ImageView b = (ImageView)v.findViewById(R.id.grid_item_image);
		int left = b.getPaddingLeft();
		int top = b.getPaddingTop();
		int right = b.getPaddingRight();
		int bottom = b.getPaddingBottom();
		if(i.compress)
			b.setBackgroundResource(R.drawable.borderh);
		else
			b.setBackgroundResource(R.drawable.border);
		b.setPadding(left, top, right, bottom);
		enableButton();
	}

	private void enableButton() {
		for(int i = 0; i < list.getCount(); i++) {
			Image row = (Image)list.getItemAtPosition(i);
			if(row.compress) {
				go.setEnabled(true);
				return;
			}
		}
		go.setEnabled(false);
	}
}
class ImageAdapter extends ArrayAdapter<Image>
{
	private int width;
	private int height;

	@SuppressWarnings("deprecation")
	ImageAdapter(Activity a, ArrayList<Image> f)
	{
		super(a, R.layout.listitem, f);
		Display display = a.getWindowManager().getDefaultDisplay();
		width = display.getWidth() / 4;
		height = display.getHeight() / 4;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent)
	{
//		if(convertView == null) {
			convertView = ((Activity)getContext()).getLayoutInflater().inflate(R.layout.listitem, null);
//		}

		Image image = getItem(position);
		ImageView updatingImage = (ImageView)convertView.findViewById(R.id.grid_item_image);

		// show image directly if already in cache
		if(image.thumb != null)
			updatingImage.setImageBitmap(image.thumb);
		else {
			Thumber thumber = new Thumber(updatingImage, image);
			thumber.execute();
		}

		TextView name = (TextView)convertView.findViewById(R.id.name);
		name.setText(image.getName());

		TextView size = (TextView)convertView.findViewById(R.id.size);
		long len = image.size;
		size.setText(OptimizerActivity.sizeString(len));

		int left = updatingImage.getPaddingLeft();
		int top = updatingImage.getPaddingTop();
		int right = updatingImage.getPaddingRight();
		int bottom = updatingImage.getPaddingBottom();
		if(image.compress)
			updatingImage.setBackgroundResource(R.drawable.borderh);
		else
			updatingImage.setBackgroundResource(R.drawable.border);
		updatingImage.setPadding(left, top, right, bottom);

		return convertView;
	}

	private class Thumber extends AsyncTask<Void, Void, Void>
	{
		private ImageView iv;
		private Image image;
		private Bitmap scaledbitmap;

		private Thumber(ImageView i, Image im) {
			iv = i;
			image = im;
		}

		@Override
		protected Void doInBackground(Void... params) {
/*			try { Thread.sleep(200); } catch (InterruptedException e) { }

			Rect bounds = new Rect();
			iv.getDrawingRect(bounds);

			Rect listBounds = new Rect(list.getScrollX(), list.getScrollY(), list.getScrollX() + list.getWidth(), list.getScrollY() + list.getHeight());

			App.debug("bounds " + bounds);
			App.debug("listBounds " + listBounds);

			if(!Rect.intersects(listBounds, bounds)) {
				App.debug("skipping invisible " + path);
				return null;
			}*/

			// App.debug("decoding " + image.path);

			BitmapFactory.Options boundOpts = new BitmapFactory.Options();
			boundOpts.inJustDecodeBounds = true;
			scaledbitmap = BitmapFactory.decodeFile(image.path, boundOpts);

			BitmapFactory.Options scaleOpts = new BitmapFactory.Options();
			scaleOpts.inSampleSize = 1;
			// round scale value to smaller power of two
			if (boundOpts.outHeight > height || boundOpts.outWidth > width) {
				scaleOpts.inSampleSize = (int)Math.pow(2, /*Math.getExponent(Math.max(boundOpts.outWidth / W, boundOpts.outHeight / H))*/
					Math.ceil(
						Math.log(Math.max(boundOpts.outWidth / width, boundOpts.outHeight / height))
						/ Math.log(2)
					)
				);
			}

			scaledbitmap = BitmapFactory.decodeFile(image.path, scaleOpts);

			image.thumb = scaledbitmap;

			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			if(scaledbitmap != null)
				iv.setImageBitmap(scaledbitmap);
		}
	}
}
