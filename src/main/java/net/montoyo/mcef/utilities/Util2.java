package net.montoyo.mcef.utilities;

import net.montoyo.mcef.MCEF;
import net.montoyo.mcef.remote.Mirror;
import net.montoyo.mcef.remote.MirrorManager;
import org.cef.OS;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Util2 {
	
	private static final DummyProgressListener DPH = new DummyProgressListener();
	private static final String HEX = "0123456789abcdef";
	
	/**
	 * Clamps d between min and max.
	 *
	 * @param d   The value to clamp.
	 * @param min The minimum.
	 * @param max The maximum.
	 * @return The clamped value.
	 */
	public static double clamp(double d, double min, double max) {
		if (d < min)
			return min;
		else if (d > max)
			return max;
		else
			return d;
	}
	
	/**
	 * Extracts a ZIP archive into a folder.
	 *
	 * @param zip The ZIP archive file to extract.
	 * @param out The output directory for the ZIP content.
	 * @return true if the extraction was successful.
	 */
	public static boolean extract(File zip, File out) {
		// For macOS, the "unzip" utility seems to be reliable at setting certain flags on executables when extracting
		// Otherwise, extracting a .app is a pain. It refuses to run without setting executable flags on the contents, etc
		if (OS.isMacintosh()) {
			try {
				Process unzip = Runtime.getRuntime().exec(new String[]{"/usr/bin/unzip", zip.getAbsolutePath(), "-d", out.getAbsolutePath()});
				unzip.waitFor();
				return true;
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}
		}
		
		ZipInputStream zis;
		
		try {
			zis = new ZipInputStream(new FileInputStream(zip));
		} catch (FileNotFoundException e) {
			Log.error("Couldn't extract %s: File not found.", zip.getName());
			e.printStackTrace();
			return false;
		}
		
		try {
			ZipEntry ze;
			while ((ze = zis.getNextEntry()) != null) {
				if (ze.isDirectory())
					continue;
				
				File dst = new File(out, ze.getName());
				delete(dst);
				mkdirs(dst);
				
				FileOutputStream fos = new FileOutputStream(dst);
				byte[] data = new byte[65536];
				int read;
				
				while ((read = zis.read(data)) > 0)
					fos.write(data, 0, read);
				
				close(fos);
			}
			
			return true;
		} catch (FileNotFoundException e) {
			Log.error("Couldn't extract a file from %s. Maybe you're missing some permissions?", zip.getName());
			e.printStackTrace();
			return false;
		} catch (IOException e) {
			Log.error("IOException while extracting %s.", zip.getName());
			e.printStackTrace();
			return false;
		} finally {
			close(zis);
		}
	}
	
	/**
	 * Returns the SHA-1 checksum of a file.
	 *
	 * @param fle The file to be hashed.
	 * @return The hash of the file or null if an error occurred.
	 */
	public static String hash(File fle) {
		FileInputStream fis;
		
		try {
			fis = new FileInputStream(fle);
		} catch (FileNotFoundException e) {
			Log.error("Couldn't hash %s: File not found.", fle.getName());
			e.printStackTrace();
			return null;
		}
		
		try {
			MessageDigest sha = MessageDigest.getInstance("SHA-1");
			sha.reset();
			
			int read = 0;
			byte buffer[] = new byte[65536];
			
			while ((read = fis.read(buffer)) > 0)
				sha.update(buffer, 0, read);
			
			byte digest[] = sha.digest();
			String hash = "";
			
			for (int i = 0; i < digest.length; i++) {
				int b = digest[i] & 0xFF;
				int left = b >>> 4;
				int right = b & 0x0F;
				
				hash += HEX.charAt(left);
				hash += HEX.charAt(right);
			}
			
			return hash;
		} catch (IOException e) {
			Log.error("IOException while hashing file %s", fle.getName());
			e.printStackTrace();
			return null;
		} catch (NoSuchAlgorithmException e) {
			Log.error("Holy crap this shouldn't happen. SHA-1 not found!!!!");
			e.printStackTrace();
			return null;
		} finally {
			close(fis);
		}
	}
	
	/**
	 * Downloads a remote resource.
	 *
	 * @param res  The filename of the resource relative to the mirror root.
	 * @param dst  The destination file.
	 * @param gzip Also extract the content using GZipInputStream.
	 * @param ph   The progress handler. May be null.
	 * @return true if the download was successful.
	 */
	public static boolean download(String res, File dst, boolean gzip, IProgressListener ph) {
		String err = "Couldn't download " + dst.getName() + "!";
		
		ph = secure(ph);
		ph.onTaskChanged("2:Downloading " + dst.getName());
		
		if (MCEF.writeMirrorData) {
            try {
                SizedInputStream sis = openStream(res, err);
                String path = res;
				File dst1 = new File("data/" + path);
				if (!dst1.exists()) dst1.getParentFile().mkdirs();
				FileOutputStream outputStream = new FileOutputStream(dst1);
				outputStream.write(sis.readAllBytes());
                sis.close();
                outputStream.flush();
                outputStream.close();
            } catch (Throwable e) {
            	e.printStackTrace();
			}
		}
		
		SizedInputStream sis = openStream(res, err);
		if (sis == null)
			return false;
		
		InputStream is;
		if (gzip) {
			try {
				is = new GZIPInputStream(sis);
			} catch (IOException e) {
				Log.error("Couldn't create GZIPInputStream: IOException.");
				e.printStackTrace();
				close(sis);
				return false;
			}
		} else
			is = sis;
		
		delete(dst);
		mkdirs(dst);
		
		FileOutputStream fos;
		try {
			fos = new FileOutputStream(dst);
		} catch (FileNotFoundException e) {
			Log.error("%s Couldn't open the destination file. Maybe you're missing rights.", err);
			e.printStackTrace();
			close(is);
			return false;
		}
		
		int read;
		byte[] data = new byte[65536];
		double total = (double) sis.getContentLength();
		double cur = .0d;
		
		try {
			while ((read = is.read(data)) > 0) {
				fos.write(data, 0, read);
				
				cur += (double) sis.resetLengthCounter();
				ph.onProgressed(cur / total * 100.d);
			}
			
			return true;
		} catch (IOException e) {
			Log.error("%s IOException while downloading.", err);
			e.printStackTrace();
			return false;
		} finally {
			close(is);
			close(fos);
		}
	}
	
	/**
	 * Same as {@link #download(String, File, boolean, IProgressListener) download}, but with gzip set to false.
	 *
	 * @param res
	 * @param dst
	 * @param ph
	 * @return
	 */
	public static boolean download(String res, File dst, IProgressListener ph) {
		return download(res, dst, false, ph);
	}
	
	/**
	 * Convenience function. Secures a progress listener.
	 * If pl is null, then a dummy empty progress listener will be returned.
	 *
	 * @param pl The progress handler to secure.
	 * @return A progress handler that is never null.
	 * @see IProgressListener
	 */
	public static IProgressListener secure(IProgressListener pl) {
		return (pl == null) ? DPH : pl;
	}
	
	/**
	 * Renames a file using a string.
	 *
	 * @param src  The file to rename.
	 * @param name The new name of the file.
	 * @return the new file or null if it failed.
	 */
	public static File rename(File src, String name) {
		File ret = new File(src.getParentFile(), name);
		
		if (src.renameTo(ret))
			return ret;
		else
			return null;
	}
	
	/**
	 * Makes sure that the directory in which the file is exists.
	 * If this one doesn't exist, i'll be created.
	 *
	 * @param f The file.
	 */
	public static void mkdirs(File f) {
		File p = f.getParentFile();
		if (!p.exists())
			p.mkdirs();
	}
	
	/**
	 * Tries to delete a file in an advanced way.
	 * Does a warning in log if it couldn't delete it neither rename it.
	 *
	 * @param f The file to be deleted.
	 * @see #delete(File)
	 */
	public static void delete(String f) {
		delete(new File(f));
	}
	
	/**
	 * Tries to delete a file in an advanced way.
	 * Does a warning in log if it couldn't delete it neither rename it.
	 *
	 * @param f The file to be deleted.
	 * @see #delete(String)
	 */
	public static void delete(File f) {
		if (!f.exists() || f.delete())
			return;
		
		File mv = new File(f.getParentFile(), "deleteme" + ((int) (Math.random() * 100000.d)));
		if (f.renameTo(mv)) {
			if (!mv.delete())
				mv.deleteOnExit();
			
			return;
		}
		
		Log.warning("Couldn't delete file! If there's any problems, please try to remove it yourself. Path: %s", f.getAbsolutePath());
	}
	
	/**
	 * Tries to open an InputStream to the following remote resource.
	 * Automatically handles broken mirrors and other errors.
	 *
	 * @param res The resource filename relative to the root of the mirror.
	 * @param err An error string in case it fails.
	 * @return The opened input stream.
	 */
	public static SizedInputStream openStream(String res, String err) {
		do {
			URLConnection conn;
			
			try {
				Mirror m = MirrorManager.INSTANCE.getCurrent();
				conn = m.getResource(res);
			} catch (MalformedURLException e) {
				Log.error("%s Is the mirror list broken?", err);
				e.printStackTrace();
				return null;
			} catch (IOException e) {
				Log.error("%s Is your antivirus or firewall blocking the connection?", err);
				e.printStackTrace();
				return null;
			}
			
			try {
				long len = -1;
				boolean failed = true;
				
				if (conn instanceof HttpURLConnection) {
					//Java 6 support
					try {
						Method m = HttpURLConnection.class.getMethod("getContentLengthLong");
						len = (Long) m.invoke(conn);
						failed = false;
					} catch (NoSuchMethodException me) {
					} catch (IllegalAccessException ae) {
					} catch (InvocationTargetException te) {
						if (te.getTargetException() instanceof IOException)
							throw (IOException) te.getTargetException();
					}
					
					if (failed)
						len = (long) conn.getContentLength();
					
					return new SizedInputStream(conn.getInputStream(), len);
				} else {
					Mirror m = MirrorManager.INSTANCE.getCurrent();
					String dir = (m.getURL() + "/" + res).substring("file://".length());
					len = new File(dir).length();
					return new SizedInputStream(new FileInputStream(dir), len);
				}
			} catch (IOException e) {
				int rc = -1;
				
				try {
					if (conn instanceof HttpURLConnection) {
						rc = ((HttpURLConnection) conn).getResponseCode();
					}
				} catch (IOException ie) {
					Log.error("%s Couldn't even get the HTTP response code!", err);
					ie.printStackTrace();
					
					return null;
				}
				
				Log.error("%s HTTP response is %d; trying with another mirror.", err, rc);
			}
		} while (MirrorManager.INSTANCE.markCurrentMirrorAsBroken());
		
		Log.error("%s All mirrors seems broken.", err);
		return null;
	}
	
	/**
	 * Calls "close" on the specified object without throwing any exceptions.
	 * This is usefull with input and output streams.
	 *
	 * @param o The object to call close on.
	 */
	public static void close(Object o) {
		try {
			o.getClass().getMethod("close").invoke(o);
		} catch (Throwable t) {
		}
	}
	
	/**
	 * Same as {@link Files#isSameFile(Path, Path)} but if an {@link IOException} is thrown,
	 * return false.
	 *
	 * @param p1 Path 1
	 * @param p2 Path 2
	 * @return true if the paths are the same, false if they are not or if an exception is thrown during the comparison
	 */
	public static boolean isSameFile(Path p1, Path p2) {
		try {
			return Files.isSameFile(p1, p2);
		} catch (IOException e) {
			return false;
		}
	}
	
	/**
	 * Same as {@link System#getenv(String)}, but if no such environment variable is
	 * defined, will return an empty string instead of null.
	 *
	 * @param name Name of the environment variable to get
	 * @return The value of this environment variable (may be empty but never null)
	 */
	public static String getenv(String name) {
		String ret = System.getenv(name);
		return ret == null ? "" : ret;
	}
	
}
