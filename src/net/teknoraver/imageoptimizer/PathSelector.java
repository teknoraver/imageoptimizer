package net.teknoraver.imageoptimizer;

import java.io.File;
import java.util.ArrayList;

import com.lamerman.FileDialog;
import com.lamerman.SelectionMode;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class PathSelector extends ListActivity {
	private static final String PATHS = "paths";
	private ArrayList<String> paths = new ArrayList<String>();
	private static final int ADDPATH = 1;
	private static final String[] builtins = new String[]{
		"/mnt/sdcard/DCIM",
		"/mnt/sdcard/Pictures/Screenshots",
		"/mnt/extSdCard/DCIM"};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.pathselector);

		SharedPreferences values =  getSharedPreferences(PATHS, MODE_PRIVATE);
		for(Object o : values.getAll().values())
			paths.add(o.toString());

		if(paths.isEmpty()) {
			for(String path : builtins)
				if(new File(path).isDirectory())
					paths.add(path);
		}

		setListAdapter(new PathAdapter(this, paths));
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		paths.remove(position);
		((PathAdapter)getListAdapter()).notifyDataSetChanged();
	}

	@Override
	protected void onPause() {
		super.onPause();

		int i = 1;
		Editor values =  getSharedPreferences(PATHS, MODE_PRIVATE).edit().clear();
		for(String s : paths)
			values.putString("path" + i++, s);
		values.commit();
	}

	public void addPath(View v)
	{
		startActivityForResult(new Intent(getBaseContext(), FileDialog.class)
			.putExtra(FileDialog.START_PATH, Environment.getExternalStorageDirectory().getPath())
			.putExtra(FileDialog.CAN_SELECT_DIR, true)
			.putExtra(FileDialog.SELECTION_MODE, SelectionMode.MODE_OPEN)
			/*.putExtra(FileDialog.ONLY_SELECT_DIR, true)*/, ADDPATH);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(requestCode != ADDPATH) {
			super.onActivityResult(requestCode, resultCode, data);
			return;
		}
		paths.add(data.getStringExtra(FileDialog.RESULT_PATH));
		((PathAdapter)getListAdapter()).notifyDataSetChanged();
	}
}

class PathAdapter extends ArrayAdapter<String>
{
	public PathAdapter(Context context, ArrayList<String> list) {
		super(context, 0, list);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		TextView path = (TextView)convertView;
		if(path == null)
			path = new TextView(getContext());

		path.setText(getItem(position));
		path.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.ic_delete, 0);

		return path;
	}
}