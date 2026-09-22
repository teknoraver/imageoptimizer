package net.teknoraver.imageoptimizer;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

import androidx.documentfile.provider.DocumentFile;
import androidx.exifinterface.media.ExifInterface;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import android.app.Activity;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;
import android.provider.MediaStore;
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
	String uri;
	String name;
	boolean compress;
	long size;
	long date;
	Bitmap thumb;
	private static final DateFormat dateParser = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.ENGLISH);

	Image(String u, String n, long s, long d, boolean c) {
		uri = u;
		name = n;
		size = s;
		date = d;
		compress = c;
	}

	String getName() {
		return name;
	}

	long getDate() {
		try (InputStream in = App.getContext().getContentResolver().openInputStream(Uri.parse(uri))) {
			if (in == null)
				return date;
			ExifInterface ei = new ExifInterface(in);
			String exifDate = ei.getAttribute(ExifInterface.TAG_DATETIME);
			/* no EXIF, return file date */
			if (exifDate == null)
				return date;
			return dateParser.parse(exifDate).getTime();
		} catch (Exception e) {
			e.printStackTrace();
		}
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
			return lhs.name.compareToIgnoreCase(rhs.name);
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
		if (newmode == R.id.sortby_name)
			return NAME;
		if (newmode == R.id.sortby_size)
			return SIZE;
		return DATE;
	}
}

public class Browser extends ListActivity implements OnClickListener {
	private static final int PERMISSION_REQUEST = 1;
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

	private static String readImagesPermission() {
		return Build.VERSION.SDK_INT >= 33
			? android.Manifest.permission.READ_MEDIA_IMAGES
			: android.Manifest.permission.READ_EXTERNAL_STORAGE;
	}

	private void startScan() {
		if (ContextCompat.checkSelfPermission(this, readImagesPermission())
		    != PermissionChecker.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(this, new String[]{readImagesPermission()}, PERMISSION_REQUEST);
			return;
		}

		go.setEnabled(false);
		setListAdapter(null);
		pd = ProgressDialog.show(this, getString(R.string.scanning_title), getString(R.string.scanning_txt));
		all.clear();
		db = openOrCreateDatabase("images", MODE_PRIVATE, null);
		db.execSQL("CREATE TABLE IF NOT EXISTS images(path TEXT PRIMARY KEY)");
		new FileScanner().execute();
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode == PERMISSION_REQUEST && grantResults.length > 0
		    && grantResults[0] == PackageManager.PERMISSION_GRANTED)
			startScan();
	}

	private class FileScanner extends AsyncTask<Void, Void, Void> {
		@Override
		protected Void doInBackground(Void ... v) {
			scanMediaStore(all);
			for(String treeUri : PathSelector.getPaths()) {
				DocumentFile dir = DocumentFile.fromTreeUri(Browser.this, Uri.parse(treeUri));
				if(dir != null)
					scanSaf(dir, all);
			}
			Collections.sort(all, new Sorter());

			return null;
		}

		@Override
		protected void onPostExecute(Void v) {
			db.close();
			setListAdapter(new ImageAdapter(Browser.this, all));
			pd.dismiss();
			enableButton();
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

	private void scanMediaStore(ArrayList<Image> out) {
		ContentResolver cr = getContentResolver();
		String[] projection = {
			MediaStore.Images.Media._ID,
			MediaStore.Images.Media.DISPLAY_NAME,
			MediaStore.Images.Media.SIZE,
			MediaStore.Images.Media.DATE_MODIFIED,
		};
		String selection = MediaStore.Images.Media.MIME_TYPE + "=? OR " + MediaStore.Images.Media.MIME_TYPE + "=?";
		String[] args = {"image/jpeg", "image/png"};
		try (Cursor c = cr.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection, args, null)) {
			if(c == null)
				return;
			int idIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
			int nameIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
			int sizeIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE);
			int dateIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED);
			while(c.moveToNext()) {
				String name = c.getString(nameIdx);
				if(name == null || name.charAt(0) == '.' || !isWanted(name))
					continue;
				Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(idIdx));
				addImage(out, uri, name, c.getLong(sizeIdx), c.getLong(dateIdx) * 1000);
			}
		}
	}

	private void scanSaf(DocumentFile dir, ArrayList<Image> out) {
		DocumentFile[] children = dir.listFiles();
		if(children == null)
			return;
		for(DocumentFile child : children) {
			if(child.isDirectory()) {
				scanSaf(child, out);
				continue;
			}
			String name = child.getName();
			if(name == null || name.charAt(0) == '.' || !isWanted(name))
				continue;
			addImage(out, child.getUri(), name, child.length(), child.lastModified());
		}
	}

	private boolean isWanted(String name) {
		String lower = name.toLowerCase(Locale.US);
		return	pm.getBoolean(Settings.JPG, true) && lower.endsWith(".jpg") ||
			pm.getBoolean(Settings.PNG, true) && lower.endsWith(".png");
	}

	private void addImage(ArrayList<Image> out, Uri uri, String name, long size, long date) {
		String uriStr = uri.toString();
		Cursor c = db.query("images", new String[]{"COUNT(path)"}, "path=?", new String[]{uriStr}, null, null, null);
		c.moveToFirst();
		boolean notSeen = c.getInt(0) == 0;
		c.close();
		out.add(new Image(uriStr, name, size, date, notSeen));
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.menu_settings) {
			startActivity(new Intent(this, Settings.class));
			return true;
		} else if (id == R.id.menu_all || id == R.id.menu_none) {
			for(int i = 0; i < list.getCount(); i++) {
				Image row = (Image)list.getItemAtPosition(i);
				row.compress = id == R.id.menu_all;
			}
			for(int i = 0; i < list.getChildCount(); i++) {
				View row = list.getChildAt(i);
				ImageView b = (ImageView)row.findViewById(R.id.grid_item_image);
				int left = b.getPaddingLeft();
				int top = b.getPaddingTop();
				int right = b.getPaddingRight();
				int bottom = b.getPaddingBottom();
				if(id == R.id.menu_all)
					b.setBackgroundResource(R.drawable.borderh);
				else
					b.setBackgroundResource(R.drawable.border);
				b.setPadding(left, top, right, bottom);
			}
			go.setEnabled(id == R.id.menu_all);
			return true;
		} else if (id == R.id.sortby_size || id == R.id.sortby_name || id == R.id.sortby_date) {
			Collections.sort(all, sorter.by(id));
			refresh();
			return true;
		} else if (id == R.id.menu_refresh) {
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
				files.add(row.uri);
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
			ContentResolver cr = getContext().getContentResolver();
			Uri u = Uri.parse(image.uri);

			BitmapFactory.Options boundOpts = new BitmapFactory.Options();
			boundOpts.inJustDecodeBounds = true;
			try (InputStream in = cr.openInputStream(u)) {
				if(in != null)
					BitmapFactory.decodeStream(in, null, boundOpts);
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}

			BitmapFactory.Options scaleOpts = new BitmapFactory.Options();
			scaleOpts.inSampleSize = 1;
			// round scale value to smaller power of two
			if (boundOpts.outHeight > height || boundOpts.outWidth > width) {
				scaleOpts.inSampleSize = (int)Math.pow(2,
					Math.ceil(
						Math.log(Math.max(boundOpts.outWidth / width, boundOpts.outHeight / height))
						/ Math.log(2)
					)
				);
			}

			try (InputStream in = cr.openInputStream(u)) {
				if(in != null)
					scaledbitmap = BitmapFactory.decodeStream(in, null, scaleOpts);
			} catch (IOException e) {
				e.printStackTrace();
			}

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
