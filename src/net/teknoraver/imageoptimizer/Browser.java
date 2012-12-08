package net.teknoraver.imageoptimizer;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;

import android.os.Bundle;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class Browser extends Activity implements FileFilter {
	ArrayList<File> all = new ArrayList<File>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_browser);

		scan(new File("/mnt/sdcard"), all);

		ListView g = (ListView)findViewById(R.id.gallery);
		g.setAdapter(new ImageAdapter(this, all));
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_browser, menu);
		return true;
	}

	private void scan(File file, ArrayList<File> all) {
		if(all.size() > 4)
			return;
		File children[] = file.listFiles(this);
		if (children != null) {
			for (File child : children) {
				if(child.isDirectory())
					scan(child, all);
				else
					all.add(child);
			}
		}
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
}

class ImageAdapter extends BaseAdapter
{
	private static final int W = 320;
	private static final int H = 240;
	private static final float RATIO = (float)W / H;

	private Activity activity;
	private ArrayList<File> files;

	public ImageAdapter(Activity a, ArrayList<File> f)
	{
		activity = a;
		files = f;
	}

	@Override
	public int getCount() 
	{
		return files.size();
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) 
	{
		View v = convertView;

		if(convertView == null)
		{
			LayoutInflater li = activity.getLayoutInflater();
			v = li.inflate(R.layout.listitem, null);

			ImageView iv = (ImageView)v.findViewById(R.id.grid_item_image);
//			iv.setImageDrawable(Drawable.createFromPath(files.get(position).getAbsolutePath()));
			Bitmap bigbitmap = BitmapFactory.decodeFile(files.get(position).getAbsolutePath());
			float ratio = (float)bigbitmap.getWidth() / bigbitmap.getHeight();

			Bitmap scaledbitmap;
			if(ratio > RATIO)
				scaledbitmap = Bitmap.createScaledBitmap(bigbitmap, W, (int)(W * ratio), true);
			if(ratio < RATIO)
				scaledbitmap = Bitmap.createScaledBitmap(bigbitmap, (int)(H * ratio), H, true);
			else
				scaledbitmap = Bitmap.createScaledBitmap(bigbitmap, W, H, true);
			iv.setImageBitmap(scaledbitmap);

			TextView name = (TextView) v.findViewById(R.id.name);
			name.setText(files.get(position).getName());
			
			TextView size = (TextView) v.findViewById(R.id.size);
			long len = files.get(position).length();
			String length;
			if(len < 1 << 10)
				length = len + " bytes";
			else if(len < 1 << 20)
				length = ((int)(len / 10.24)) / 100.0 + " Kb";
			else
				length = ((int)(len / 10485.76)) / 100.0 + " Mb";
			size.setText(length);
		}

		return v;
	}

	@Override
	public Object getItem(int position) {
		return null;
	}

	@Override
	public long getItemId(int position) {
		return 0;
	}

}