package net.teknoraver.imageoptimizer;

import java.util.ArrayList;

import androidx.documentfile.provider.DocumentFile;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class PathSelector extends ListActivity {
	private static final String PATHS = "paths";
	private ArrayList<String> paths = new ArrayList<String>();
	private static final int ADDPATH = 1;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.pathselector);

		paths = getPaths();

		setListAdapter(new PathAdapter(this, paths));
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		String removed = paths.remove(position);
		getContentResolver().releasePersistableUriPermission(Uri.parse(removed),
			Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
		((PathAdapter)getListAdapter()).notifyDataSetChanged();
	}

	@Override
	protected void onPause() {
		super.onPause();

		int i = 1;
		Editor values = getSharedPreferences(PATHS, MODE_PRIVATE).edit().clear();
		for(String s : paths)
			values.putString("path" + i++, s);
		values.commit();
	}

	public void addPath(View v)
	{
		startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), ADDPATH);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(requestCode != ADDPATH) {
			super.onActivityResult(requestCode, resultCode, data);
			return;
		}
		if(data == null || data.getData() == null)
			return;
		Uri treeUri = data.getData();
		getContentResolver().takePersistableUriPermission(treeUri,
			Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
		String newPath = treeUri.toString();
		if(!paths.contains(newPath)) {
			paths.add(newPath);
			((PathAdapter)getListAdapter()).notifyDataSetChanged();
		} else
			Toast.makeText(this, R.string.alreadypath, Toast.LENGTH_SHORT).show();
	}

	static ArrayList<String> getPaths()
	{
		ArrayList<String> p = new ArrayList<String>();
		SharedPreferences values = App.getContext().getSharedPreferences(PATHS, MODE_PRIVATE);
		for(Object o : values.getAll().values())
			p.add(o.toString());

		return p;
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

		String uriStr = getItem(position);
		DocumentFile dir = DocumentFile.fromTreeUri(getContext(), Uri.parse(uriStr));
		path.setText(dir != null && dir.getName() != null ? dir.getName() : uriStr);
		path.setTextSize(20);
		path.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.ic_delete, 0);

		return path;
	}
}
