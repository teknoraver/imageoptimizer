package net.teknoraver.imageoptimizer;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;

import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

public class Browser extends FragmentActivity implements FileFilter, OnClickListener, Observer {
	private ListView list;
	private ProgressDialogFragment progress;
	private final Handler handler = new Handler();
	private String msg;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_browser);

//		ProgressDialog pd = ProgressDialog.show(this, "Scanning files", "Scanning for picture files");

		ArrayList<File> all = new ArrayList<File>();
		scan(new File("/mnt/sdcard/optim"), all);

//		pd.cancel();

		list = (ListView)findViewById(R.id.gallery);
		list.setAdapter(new ImageAdapter(this, all));

		((Button)findViewById(R.id.optimize)).setOnClickListener(this);
		getFile("jpegoptim");
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_browser, menu);
		return true;
	}

	private void scan(File file, ArrayList<File> all) {
		File children[] = file.listFiles(this);
		if (children != null) {
System.out.println("Scanning " + file.getAbsolutePath());
			for (File child : children) {
				if(child.isDirectory())
					scan(child, all);
				else {
System.out.println("adding " + child.getAbsolutePath());
					all.add(child);
				}
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

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if(item.getItemId() == R.id.menu_settings) {
			startActivity(new Intent(this, Settings.class));
			return true;
		}
		return super.onContextItemSelected(item);
	}

	@Override
	public void onClick(View v) {
		SharedPreferences pm = PreferenceManager.getDefaultSharedPreferences(this);
		ArrayList<String> checked = new ArrayList<String>(list.getCount());
		for(int i = 0; i < list.getChildCount(); i++) {
			View row = (View)list.getChildAt(i);
			CheckBox ch = (CheckBox)row.findViewById(R.id.checkbox);
			if(ch.isChecked())
				checked.add(((File)list.getItemAtPosition(i)).getAbsolutePath());
		}
		progress = new ProgressDialogFragment(checked.size());
		progress.show(getSupportFragmentManager(), "compress");

		Jpegoptim jo = new Jpegoptim(getFilesDir() + "/jpegoptim", checked, pm.getBoolean("lossy", false), Integer.parseInt(pm.getString("quality", "75")));
		jo.addObserver(this);
		new Thread(jo).start();
	}

	@Override
	public void update(Observable observable, Object data) {
		if(data == null)
			progress.dismiss();
		else {
			msg = (String)data;
			System.out.println("update(): " + msg);
			handler.post(compresser);
		}
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
			progress.advance(msg);
		}
	};
}

class ProgressDialogFragment extends DialogFragment {
	private TextView txt;
	private ProgressBar perc;
	private int size;

	ProgressDialogFragment(int i) {
		size = i;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.progress, container);
		txt = (TextView)v.findViewById(R.id.text);
		perc = (ProgressBar)v.findViewById(R.id.progress);
		perc.setMax(size);
		return v;
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		Dialog dialog = super.onCreateDialog(savedInstanceState);
		dialog.setTitle(R.string.compress);
		return dialog;
	}

	public void advance(String msg) {
		perc.setProgress(perc.getProgress() + 1);
		txt.setText(msg);
	}
}

class ImageAdapter extends ArrayAdapter<File>
{
	private static final int W = 320;
	private static final int H = 240;
	private static final float RATIO = (float)W / H;

	public ImageAdapter(Activity a, ArrayList<File> f)
	{
		super(a, R.layout.listitem, f);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) 
	{
		if(convertView == null) {
			System.out.println("position " + position + ": " + getItem(position).getAbsolutePath());
			convertView = ((Activity)getContext()).getLayoutInflater().inflate(R.layout.listitem, null);
		}

		ImageView iv = (ImageView)convertView.findViewById(R.id.grid_item_image);
		Bitmap bigbitmap = BitmapFactory.decodeFile(getItem(position).getAbsolutePath());
		float ratio = (float)bigbitmap.getWidth() / bigbitmap.getHeight();

		Bitmap scaledbitmap;
		if(ratio > RATIO)
			scaledbitmap = Bitmap.createScaledBitmap(bigbitmap, W, (int)(W * ratio), true);
		if(ratio < RATIO)
			scaledbitmap = Bitmap.createScaledBitmap(bigbitmap, (int)(H * ratio), H, true);
		else
			scaledbitmap = Bitmap.createScaledBitmap(bigbitmap, W, H, true);
		iv.setImageBitmap(scaledbitmap);

		TextView name = (TextView)convertView.findViewById(R.id.name);
		name.setText(getItem(position).getName());

		TextView size = (TextView)convertView.findViewById(R.id.size);
		long len = getItem(position).length();
		String length;
		if(len < 1 << 10)
			length = len + " bytes";
		else if(len < 1 << 20)
			length = ((int)(len / 10.24)) / 100.0 + " Kb";
		else
			length = ((int)(len / 10485.76)) / 100.0 + " Mb";
		size.setText(length);

		return convertView;
	}
}