package net.teknoraver.imageoptimizer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

/**
 * Helpers for treating images identified by content:// Uri (MediaStore rows
 * or SAF documents) as local files, since jpegoptim/optipng are native
 * binaries that only understand real filesystem paths.
 */
final class ContentUtil {
	private ContentUtil() { }

	static String displayName(ContentResolver cr, Uri uri) {
		try (Cursor c = cr.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
			if (c != null && c.moveToFirst()) {
				int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
				if (idx >= 0) {
					String name = c.getString(idx);
					if (name != null)
						return name;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		String last = uri.getLastPathSegment();
		return last != null ? last : uri.toString();
	}

	/** Copies the content behind uri into a private cache file the native optimizers can operate on. */
	static File copyToCache(Context context, Uri uri, String name) throws IOException {
		File dir = new File(context.getCacheDir(), "optimize/" + Integer.toHexString(uri.hashCode()));
		dir.mkdirs();
		File out = new File(dir, name);
		try (InputStream in = context.getContentResolver().openInputStream(uri)) {
			if (in == null)
				throw new IOException("cannot open " + uri);
			try (OutputStream os = new FileOutputStream(out)) {
				byte[] buf = new byte[65536];
				int len;
				while ((len = in.read(buf)) > 0)
					os.write(buf, 0, len);
			}
		}
		return out;
	}

	static void deleteCacheDir(File cacheFile) {
		File parent = cacheFile.getParentFile();
		cacheFile.delete();
		if (parent != null)
			parent.delete();
	}
}
