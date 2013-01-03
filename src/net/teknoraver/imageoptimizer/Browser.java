package net.teknoraver.imageoptimizer;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

class Image {
	String path;
	boolean compress;
	long size;
	long date;

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
	private static final int BYNAME = 0;
	private static final int BYSIZE = 1;
	private static final int BYDATE = 2;
	private int mode;
	private boolean asc;

	@Override
	public int compare(Image lhs, Image rhs) {
		if(!asc) {
			Image tmp = lhs;
			lhs = rhs;
			rhs = tmp;
		}
		switch(mode) {
		case BYNAME:
			return lhs.path.compareToIgnoreCase(rhs.path);
		case BYSIZE:
			return (int)(lhs.size - rhs.size);
		case BYDATE:
			return (int)(lhs.date - rhs.date);
		}
		return 0;
	}

	// invert operands if the same order is applied for the second time
	Sorter byName() {
		asc = mode == BYNAME ? !asc : true;
		mode = BYNAME;
		return this;
	}

	Sorter bySize() {
		asc = mode == BYSIZE ? !asc : true;
		mode = BYSIZE;
		return this;
	}

	Sorter byDate() {
		asc = mode == BYDATE ? !asc : true;
		mode = BYDATE;
		return this;
	}
}

public class Browser extends ListActivity implements FileFilter, OnClickListener, Runnable {
	private final Handler handler = new Handler();
	private ProgressDialog pd;
	private ListView list;
	private ArrayList<Image> all = new ArrayList<Image>();
	private Sorter sorter = new Sorter();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.browser);

		pd = ProgressDialog.show(this, "Scanning files", "Scanning for picture files");
		list = getListView();

		((Button)findViewById(R.id.optimize)).setOnClickListener(this);
		getFile("jpegoptim");

		new Thread(this).start();
	}

	@Override
	protected void onResume() {
		super.onResume();

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
		if(pathname.getName().endsWith(".jpg"))
			return true;
		return false;
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
				ImageView c = (ImageView)row.findViewById(R.id.compress);
				if(item.getItemId() == R.id.menu_all)
					c.setImageResource(android.R.drawable.checkbox_on_background);
				else
					c.setImageResource(android.R.drawable.checkbox_off_background);
			}
//			((ImageAdapter)list.getAdapter()).notifyDataSetChanged();
			return true;
		case R.id.sortby_size:
			Collections.sort(all, sorter.bySize());
			refresh();
			return true;
		case R.id.sortby_name:
			Collections.sort(all, sorter.byName());
			refresh();
			return true;
		case R.id.sortby_date:
			Collections.sort(all, sorter.byDate());
			refresh();
			return true;
		}
		return super.onContextItemSelected(item);
	}

	@Override
	public void onClick(View v) {
		SharedPreferences pm = PreferenceManager.getDefaultSharedPreferences(this);
		ArrayList<String> checked = new ArrayList<String>(list.getCount());
		for(int i = 0; i < list.getCount(); i++) {
			Image row = (Image)list.getItemAtPosition(i);
			if(row.compress)
				checked.add(row.path);
		}
		if(checked.isEmpty())
			return;

		Jpegoptim jo = new Jpegoptim(
			getFilesDir() + "/jpegoptim",
			checked,
			pm.getBoolean("lossy", false),
			Integer.parseInt(pm.getString("jpegquality", "75")),
			pm.getBoolean("timestamp", true),
			Integer.parseInt(pm.getString("threshold", "10")));

		Intent comp = new Intent(this, Optimizer.class);
		comp.putExtra(Optimizer.OPTIMIZER, jo);
		startActivity(comp);
	}

	private void getFile(final String name) {
                final File file = new File(getFilesDir(), name);
                if (!file.exists())
                        try {
                                getFilesDir().mkdirs();
                                final InputStream in = getAssets().open(name);
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

	@Override
	public void run() {
		Runnable pdclose = new Runnable() {
			@Override
			public void run() {
				setListAdapter(new ImageAdapter(Browser.this, all));
				pd.dismiss();
			}
		};
		scan(new File(Environment.getExternalStorageDirectory() + "/DCIM"));
		handler.post(pdclose); 
	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		ImageView c = (ImageView)v.findViewById(R.id.compress);
		Image i = (Image)list.getItemAtPosition(position);
		i.compress = !i.compress;
		if(i.compress)
			c.setImageResource(android.R.drawable.checkbox_on_background);
		else
			c.setImageResource(android.R.drawable.checkbox_off_background);
	}

}
class ImageAdapter extends ArrayAdapter<Image>
{
	private static final int W = 320;
	private static final int H = 240;
	private static final float RATIO = (float)W / H;
	private ImageView updatingImage;
	private String updatingPath;
	private static final HashMap<String, Bitmap> cache = new HashMap<String, Bitmap>();

	ImageAdapter(Activity a, ArrayList<Image> f)
	{
		super(a, R.layout.listitem, f);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) 
	{
//		if(convertView == null) {
			convertView = ((Activity)getContext()).getLayoutInflater().inflate(R.layout.listitem, null);
//		}

		updatingImage = (ImageView)convertView.findViewById(R.id.grid_item_image);
		updatingPath = getItem(position).path;

		Thumber thumber = new Thumber(updatingImage, updatingPath);
		thumber.execute();

		TextView name = (TextView)convertView.findViewById(R.id.name);
		name.setText(getItem(position).getName());

		TextView size = (TextView)convertView.findViewById(R.id.size);
		long len = getItem(position).size;
		size.setText(Optimizer.sizeString(len));

		ImageView c = (ImageView)convertView.findViewById(R.id.compress);
		Image i = (Image)getItem(position);
		if(i.compress)
			c.setImageResource(android.R.drawable.checkbox_on_background);
		else
			c.setImageResource(android.R.drawable.checkbox_off_background);

		return convertView;
	}

	private class Thumber extends AsyncTask<Void, Void, Void>
	{
		private ImageView iv;
		private String path;
		private Bitmap scaledbitmap;

		private Thumber(ImageView i, String p) {
			iv = i;
			path = p;
		}

		@Override
		protected Void doInBackground(Void... params) {
/*			try { Thread.sleep(200); } catch (InterruptedException e) { }

			Rect bounds = new Rect();
			iv.getDrawingRect(bounds);

			Rect listBounds = new Rect(list.getScrollX(), list.getScrollY(), list.getScrollX() + list.getWidth(), list.getScrollY() + list.getHeight());

			System.out.println("bounds " + bounds);
			System.out.println("listBounds " + listBounds);

			if(!Rect.intersects(listBounds, bounds)) {
				System.out.println("skipping invisible " + path);
				return null;
			}*/

			scaledbitmap = cache.get(path);
			if(scaledbitmap == null) {
				System.out.println("decoding " + path);

				BitmapFactory.Options boundOpts = new BitmapFactory.Options();
				boundOpts.inJustDecodeBounds = true;
				Bitmap bigbitmap = BitmapFactory.decodeFile(path, boundOpts);

				float ratio = (float)boundOpts.outWidth / boundOpts.outHeight;

				int scale = 1;
				// round scale value to smaller power of two
				if (boundOpts.outHeight > H || boundOpts.outWidth > W) {
					scale = (int)Math.pow(2,
							Math.floor(
								Math.log(Math.max(boundOpts.outWidth / W, boundOpts.outHeight / H))
								/ Math.log(2)
							)
						);
				}

				BitmapFactory.Options scaleOpts = new BitmapFactory.Options();
				scaleOpts.inSampleSize = scale;

				bigbitmap = BitmapFactory.decodeFile(path, scaleOpts);

				if(ratio > RATIO)
					scaledbitmap = Bitmap.createScaledBitmap(bigbitmap, W, (int)(W / ratio), true);
				if(ratio < RATIO)
					scaledbitmap = Bitmap.createScaledBitmap(bigbitmap, (int)(H * ratio), H, true);
				else
					scaledbitmap = Bitmap.createScaledBitmap(bigbitmap, W, H, true);

				cache.put(path, scaledbitmap);
			}

			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			if(scaledbitmap != null)
				iv.setImageBitmap(scaledbitmap);
		}
	}
}
