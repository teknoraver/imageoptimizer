package net.teknoraver.imageoptimizer;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

public class ProgressDialogFragment extends DialogFragment {
	static final String SIZE = "size";
	private TextView txt;
	private ProgressBar perc;
	private int size;

	@Override
	public void setArguments(Bundle args) {
		size = args.getInt(SIZE);
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

	@Override
	public void onCancel(DialogInterface dialog) {
		super.onCancel(dialog);
		System.out.println("Aborting!");
	}

	void advance(String msg) {
		perc.setProgress(perc.getProgress() + 1);
		txt.setText(msg);
	}
}
