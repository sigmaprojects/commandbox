/**
 * Copyright (C) 2014 Ortus Solutions, Corp
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALING
 * IN THE SOFTWARE.
 */
package cliloader;

import java.io.*;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;
import java.util.jar.*;
import java.util.zip.GZIPInputStream;

/**
 * Our system utility class
 * @author Ortus Solutions <info@ortussolutions.com>
 */
public final class Util {

	private static final int KB = 1024;

	/**
	 * 
	 * @param classLoader
	 * @param resourcePath
	 * @param libDir
	 * @param debug 
	 */
	public static void unzipInteralZip(ClassLoader classLoader, String resourcePath, File libDir, boolean debug) {
		if (debug)
			System.out.println("Extracting " + resourcePath);
		libDir.mkdir();
		URL resource = classLoader.getResource(resourcePath);
		if (resource == null) {
			System.err.println("Could not find the " + resourcePath + " on classpath!");
			System.exit(1);
		}
		class PrintDot extends TimerTask {
			public void run() {
				System.out.print(".");
			}
		}
		Timer timer = new Timer();
		PrintDot task = new PrintDot();
		timer.schedule(task, 0, 2000);

		try {

			BufferedInputStream bis = new BufferedInputStream(resource.openStream());
			JarInputStream jis = new JarInputStream(bis);
			JarEntry je = null;
			while ((je = jis.getNextJarEntry()) != null) {
				java.io.File f = new java.io.File(libDir.toString() + java.io.File.separator + je.getName());
				if (je.isDirectory()) {
					f.mkdir();
					continue;
				}
				File parentDir = new File(f.getParent());
				if (!parentDir.exists()) {
					parentDir.mkdir();
				}
				writeStreamTo(jis, new FileOutputStream(f), 8 * KB);
				if (f.getPath().endsWith("pack.gz")) {
					unpack(f);
					f.delete();
				}
			}

		} catch (Exception exc) {
			task.cancel();
			exc.printStackTrace();
		}
		task.cancel();

	}
	
	/**
	 * 
	 * @param classLoader
	 * @param resourcePath
	 * @param dest 
	 */
	public static void copyInternalFile(ClassLoader classLoader, String resourcePath, File dest) {
		URL resource = classLoader.getResource(resourcePath);
		try {
			BufferedInputStream bis = new BufferedInputStream(resource.openStream());
			FileOutputStream output = new FileOutputStream(dest);
			writeStreamTo(bis, output, 8 * KB);
			output.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
	}
	
	/**
	 * 
	 * @param inFile 
	 */
	public static void unpack(File inFile) {

		JarOutputStream out = null;
		InputStream in = null;
		String inName = inFile.getPath();
		String outName;

		if (inName.endsWith(".pack.gz")) {
			outName = inName.substring(0, inName.length() - 8);
		} else if (inName.endsWith(".pack")) {
			outName = inName.substring(0, inName.length() - 5);
		} else {
			outName = inName + ".unpacked";
		}
		try {
			Pack200.Unpacker unpacker = Pack200.newUnpacker();
			out = new JarOutputStream(new FileOutputStream(outName));
			in = new FileInputStream(inName);
			if (inName.endsWith(".gz"))
				in = new GZIPInputStream(in);
			unpacker.unpack(in, out);
		} catch (IOException ex) {
			ex.printStackTrace();
		} finally {
			if (in != null) {
				try { in.close();} catch (IOException ex) {
					System.err.println("Error closing file: " + ex.getMessage());
				}
			}
			if (out != null) {
				try { out.flush(); out.close(); } catch (IOException ex) {
					System.err.println("Error closing file: " + ex.getMessage());
				}
			}
		}
	}

	/**
	 * 
	 * @param input
	 * @param output
	 * @param bufferSize
	 * @return
	 * @throws IOException 
	 */
	public static int writeStreamTo(final InputStream input, final OutputStream output, int bufferSize)
			throws IOException {
		int available = Math.min(input.available(), 256 * KB);
		byte[] buffer = new byte[Math.max(bufferSize, available)];
		int answer = 0;
		int count = input.read(buffer);
		while (count >= 0) {
			output.write(buffer, 0, count);
			answer += count;
			count = input.read(buffer);
		}
		return answer;
	}

}
