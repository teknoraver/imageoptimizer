package net.teknoraver.imageoptimizer;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Observable;
import java.util.Observer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.app.Activity;
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
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

class Image {
	String path;
	boolean compress;
	long size;

	Image(String f, long s) {
		path = f;
		size = s;
	}

	String getName() {
		return path.substring(path.lastIndexOf('/') + 1);
	}
}

public class Browser extends FragmentActivity implements FileFilter, OnClickListener, Observer, Runnable, OnItemClickListener {
	private ListView list;
	private ProgressDialogFragment progress;
	private final Handler handler = new Handler();
	private String msg;
	private ProgressDialog pd;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.browser);

		pd = ProgressDialog.show(this, "Scanning files", "Scanning for picture files");
		list = (ListView)findViewById(R.id.gallery);
		list.setOnItemClickListener(this);

		((Button)findViewById(R.id.optimize)).setOnClickListener(this);
		getFile("jpegoptim");

		new Thread(this).start();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.browser, menu);
		return true;
	}

	private void scan(File file, ArrayList<Image> all) {
		File children[] = file.listFiles(this);
		if (children != null)
			for (File child : children)
				if(child.isDirectory())
					scan(child, all);
				else
					all.add(new Image(child.getAbsolutePath(), child.length()));
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
			((ImageAdapter)list.getAdapter()).notifyDataSetChanged();
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
		progress = new ProgressDialogFragment();
		Bundle b = new Bundle();
		b.putInt(ProgressDialogFragment.SIZE, checked.size());
		progress.setArguments(b);
		progress.show(getSupportFragmentManager(), "compress");

		Jpegoptim jo = new Jpegoptim(
				getFilesDir() + "/jpegoptim",
				checked,
				pm.getBoolean("lossy", false),
				Integer.parseInt(pm.getString("jpegquality", "75")),
				pm.getBoolean("timestamp", true),
				Integer.parseInt(pm.getString("threshold", "10")));
		jo.addObserver(this);
		new Thread(jo).start();
	}

	@Override
	public void update(Observable observable, Object data) {
		if(data == null) {
			progress.dismiss();
			progress = null;
		} else {
			String res[] = (String[])data;
			msg = res[0].substring(res[0].lastIndexOf('/') + 1);
			for(int i = 0; i < list.getCount(); i++) {
				Image row = (Image)list.getItemAtPosition(i);
				if(row.path.equals(res[0])) {
					row.size = Long.parseLong(res[4]);
					break;
				}
			}
			System.out.println("update(): " + msg);
		}
		handler.post(compresser);
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

	private Runnable compresser = new Runnable() {
		@Override
		public void run() {
			if(progress != null)
				progress.advance(msg);
			else
				((ImageAdapter)list.getAdapter()).notifyDataSetChanged();
		}
	};

	@Override
	public void run() {
		final ArrayList<Image> all = new ArrayList<Image>();
		Runnable pdclose = new Runnable() {
			@Override
			public void run() {
				list.setAdapter(new ImageAdapter(Browser.this, all));
				pd.dismiss();
			}
		};
		scan(new File(Environment.getExternalStorageDirectory() + "/DCIM"), all);
		handler.post(pdclose); 
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		ImageView c = (ImageView)view.findViewById(R.id.compress);
		Image i = (Image)parent.getItemAtPosition(position);
		i.compress = !i.compress;
		if(i.compress)
			c.setImageResource(R.drawable.on);
		else
			c.setImageResource(R.drawable.off);
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
		String length;
		if(len < 1 << 10)
			length = len + " bytes";
		else if(len < 1 << 20)
			length = ((int)(len / 10.24)) / 100.0 + " Kb";
		else
			length = ((int)(len / 10485.76)) / 100.0 + " Mb";
		size.setText(length);

		ImageView c = (ImageView)convertView.findViewById(R.id.compress);
		Image i = (Image)getItem(position);
		if(i.compress)
			c.setImageResource(R.drawable.on);
		else
			c.setImageResource(R.drawable.off);

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
