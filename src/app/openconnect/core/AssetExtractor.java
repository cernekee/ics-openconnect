package app.openconnect.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import android.content.Context;
import android.util.Log;

public class AssetExtractor {

	public static final String TAG = "OpenConnect";

	public static final int FL_FORCE = 0x01;
	public static final int FL_NOEXEC = 0x02;

    private static long crc32(File f)
    		throws FileNotFoundException, IOException {
        FileInputStream in = new FileInputStream(f);
        CRC32 crcMaker = new CRC32();
        byte[] buffer = new byte[65536];

        while (true) {
        	int len = in.read(buffer);
        	if (len == -1) {
        		break;
        	}
            crcMaker.update(buffer, 0, len);
        }
        in.close();
        return crcMaker.getValue();
    }

    private static void writeStream(InputStream in, File file)
    		throws FileNotFoundException, IOException {
    	FileOutputStream out = new FileOutputStream(file);

    	byte[] buffer = new byte[65536];

    	while (true) {
    		int len = in.read(buffer);
    		if (len == -1) {
    			break;
    		}
    	    out.write(buffer, 0, len);
    	}
    	out.close();
    }

    private static String getArch() {
        String prop = System.getProperty("os.arch");
        if (prop.contains("x86") || prop.contains("i686") || prop.contains("i386")) {
            return "x86";
        } else if (prop.contains("mips")) {
            return "mips";
        } else {
            return "armeabi";
        }
    }

	public static boolean extractAll(Context ctx, int flags, String path) {
		String patterns[] = { "assets/raw/noarch", "assets/raw/" + getArch() };

		if (path == null) {
			path = ctx.getFilesDir().getAbsolutePath();
		} else if (!path.endsWith(File.separator)) {
			path = path + File.separator;
		}

		try {
			ZipFile zf = new ZipFile(ctx.getPackageCodePath());
			for (Enumeration<?> e = zf.entries(); e.hasMoreElements(); ) {
				ZipEntry ze = (ZipEntry)e.nextElement();
				String fname = ze.getName();

				for (String prefix : patterns) {
					if (!fname.startsWith(prefix)) {
						continue;
					}
					fname = path + fname.substring(prefix.length());

					File file = new File(fname);
					if ((flags & FL_FORCE) == 0 && file.exists() && crc32(file) == ze.getCrc()) {
						Log.d(TAG, "AssetExtractor: skipping " + fname);
						continue;
					}
					Log.i(TAG, "AssetExtractor: writing " + fname);
					writeStream(zf.getInputStream(ze), file);
					if ((flags & FL_NOEXEC) == 0) {
						file.setExecutable(true);
					}
				}
			}
			zf.close();
		} catch (IOException e) {
			Log.e(TAG, "AssetExtractor: caught exception", e);
			return false;
		}
		return true;
	}

	public static boolean extractAll(Context ctx) {
		return extractAll(ctx, 0, null);
	}
}
