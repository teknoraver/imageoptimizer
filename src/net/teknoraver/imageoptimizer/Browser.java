package net.teknoraver.imageoptimizer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
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

	Image(String f, long s, long d) {
		path = f;
		size = s;
		date = d;
	}

	String getName() {
		return path.substring(path.lastIndexOf('/') + 1);
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
			return (int)Math.signum(lhs.date - rhs.date);
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
		switch(mode) {
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

public class Browser extends ListActivity implements FileFilter, OnClickListener, Runnable, DialogInterface.OnClickListener {
	private static final String CPUINFO = "/proc/cpuinfo";

	private final Handler handler = new Handler();
	private ProgressDialog pd;
	private ListView list;
	private ArrayList<Image> all = new ArrayList<Image>();
	private Sorter sorter = new Sorter();
	private Button go;
	private SharedPreferences pm;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.browser);

		list = getListView();

		go = (Button)findViewById(R.id.optimize);
		go.setOnClickListener(this);

		pm = PreferenceManager.getDefaultSharedPreferences(this);

		try {
			getFile("jpegoptim");
			getFile("optipng");
		} catch (IOException ioe) {
			ioe.printStackTrace();
			Toast.makeText(this, R.string.ioerror, Toast.LENGTH_LONG).show();
			finish();
		}
	}

	private void startScan() {
		setListAdapter(null);
		pd = ProgressDialog.show(this, getString(R.string.scanning_title), getString(R.string.scanning_txt));
		new Thread(this).start();
	}

	@Override
	public void run() {
		all.clear();
		Runnable pdclose = new Runnable() {
			@Override
			public void run() {
				Collections.sort(all, new Sorter());
				setListAdapter(new ImageAdapter(Browser.this, all));
				pd.dismiss();
				Toast.makeText(Browser.this, R.string.hint, Toast.LENGTH_LONG).show();
			}
		};
		for(String path : PathSelector.getPaths()) {
			File dir = new File(path);
			if(dir.isDirectory())
				scan(new File(path));
		}

		handler.post(pdclose);
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
					all.add(new Image(child.getAbsolutePath(), child.length(), child.lastModified()));
	}

	@Override
	public boolean accept(File pathname) {
		if(pathname.getName().charAt(0) == '.')
			return false;
		if(pathname.isDirectory())
			return true;
		return	pm.getBoolean(Settings.JPG, true) && pathname.toString().endsWith(".jpg") ||
			pm.getBoolean(Settings.PNG, true) && pathname.toString().endsWith(".png");
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
		ArrayList<String> jpgs = new ArrayList<String>(list.getCount());
		ArrayList<String> pngs = new ArrayList<String>(list.getCount());
		for(int i = 0; i < list.getCount(); i++) {
			Image row = (Image)list.getItemAtPosition(i);
			if(row.compress) {
				if(row.path.toString().endsWith(".jpg"))
					jpgs.add(row.path);
				else if(row.path.toString().endsWith(".png"))
					pngs.add(row.path);
			}
		}
		if(jpgs.isEmpty() && pngs.isEmpty())
			return;

		ArrayList<Optimizer> optimizers = new ArrayList<Optimizer>(2);
		if(pm.getBoolean(Settings.JPG, true)) {
			int quality = -1;
			if(pm.getBoolean(Settings.LOSSY, true))
				quality = pm.getInt(Settings.JPEGQ, 75);
			optimizers.add(new Jpegoptim(	jpgs,
							quality,
							pm.getBoolean(Settings.TIMESTAMP, true),
							pm.getInt(Settings.THRESHOLD, 10)));
		}
		if(pm.getBoolean(Settings.PNG, true))
			optimizers.add(new Optipng(pngs, pm.getInt(Settings.PNGQ, 1), pm.getBoolean(Settings.TIMESTAMP, true)));

		startActivity(new Intent(this, OptimizerActivity.class)
			.putExtra(OptimizerActivity.OPTIMIZER, optimizers)
		);
	}

	private void getFile(final String name) throws IOException {
		String binary = detectCpu();
		if(binary == null) {
			new AlertDialog.Builder(this)
				.setTitle(getString(R.string.arch))
				.setMessage(getString(R.string.sendmail))
				.setNegativeButton(android.R.string.no, this)
				.setPositiveButton(android.R.string.yes, this)
				.setIcon(android.R.drawable.ic_dialog_email)
				.create().show();
		} else {
			final File file = new File(getFilesDir(), name);
			if (!file.exists())
				try {
					getFilesDir().mkdirs();
					final InputStream in = getAssets().open(name + '/' + binary);
					final FileOutputStream out = new FileOutputStream(file);
					final byte[] buf = new byte[65536];
					int len;
					while ((len = in.read(buf)) > 0)
						out.write(buf, 0, len);
					in.close();
					out.close();
					Runtime.getRuntime().exec(new String[]{"chmod", "755", file.getAbsolutePath()});
				} catch (final IOException ex) {
					ex.printStackTrace();
					finish();
					return;
				}
		}
        }

	private String detectCpu() throws IOException {
		if(Build.CPU_ABI.startsWith("arm")) {
			BufferedReader cpuiinfo = new BufferedReader(new FileReader(CPUINFO), 1024);
			String line;
			while((line = cpuiinfo.readLine()) != null)
				if(line.startsWith("Features"))
					break;
			cpuiinfo.close();

			if(line == null)
				return null;

			if(line.contains("neon"))
				return "arm/neon";
			if(line.contains("vfp"))
				return "arm/vfp";
			if(line.contains("thumb"))
				return "arm/soft";

			return null;
		}
		if(Build.CPU_ABI.startsWith("x86"))
			return "x86";
		if(Build.CPU_ABI.startsWith("mips"))
			return "mips";

		return null;
	}

	@Override
	public void onClick(DialogInterface dialog, int which) {
		if(which == DialogInterface.BUTTON_POSITIVE) {
			Intent intent = new Intent(Intent.ACTION_SEND);
			intent.setType("message/rfc822");
			intent.putExtra(Intent.EXTRA_EMAIL, new String[] {getString(R.string.email)});
			intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.support_sub));
			intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.support_msg));
			File file = new File(CPUINFO);
			if (!file.exists() || !file.canRead()) {
				Toast.makeText(this, getString(R.string.support_atterror), Toast.LENGTH_LONG).show();
				finish();
				return;
			}
			intent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + CPUINFO));
			startActivity(Intent.createChooser(intent, getString(R.string.support_intent)));
		}
		finish();
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

		ImageView b = (ImageView)convertView.findViewById(R.id.grid_item_image);
		int left = b.getPaddingLeft();
		int top = b.getPaddingTop();
		int right = b.getPaddingRight();
		int bottom = b.getPaddingBottom();
		if(image.compress)
			b.setBackgroundResource(R.drawable.borderh);
		else
			b.setBackgroundResource(R.drawable.border);
		b.setPadding(left, top, right, bottom);

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
