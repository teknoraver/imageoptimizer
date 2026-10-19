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
import android.provider.MediaStore;
import android.provider.OpenableColumns;

/**
 * Helpers for treating images identified by content:// Uri (MediaStore rows
 * or SAF documents) as local files, since jpegoptim/oxipng are native
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

	/**
	 * Google Photos shares local photos through its own read only provider, embedding the
	 * MediaStore Uri of the photo in the path: use that one instead, which can be written
	 * once the user consents. Any other Uri is returned as it is.
	 */
	static Uri writableUri(ContentResolver cr, Uri uri) {
		if(MediaStore.AUTHORITY.equals(uri.getAuthority()))
			return uri;
		for(String segment : uri.getPathSegments()) {
			if(!segment.startsWith("content://" + MediaStore.AUTHORITY + "/"))
				continue;
			Uri media = Uri.parse(segment);
			// make sure it still exists, and that it can be read
			try (Cursor c = cr.query(media, new String[]{MediaStore.MediaColumns._ID}, null, null, null)) {
				if(c != null && c.moveToFirst())
					return media;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return uri;
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
