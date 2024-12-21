package org.mewx.wenku8.util;

import android.content.ContentValues;
import androidx.annotation.Nullable;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

/**
 *  Light Network
 **
 *  This class achieve the basic network protocol:
 *      HttpPost ...
 **/

public class LightNetwork {

	/**
	 * Encode UTF-8 character to http postable style. For example: "妹" = "%E5%A6%B9"
	 * @param str input string
	 * @return result encoded string or empty string
	 */
	public static String encodeToHttp(String str) {
		return encodeToHttp(str, "UTF-8");
	}

	/**
	 * Universal encoding to percent-encoding.
	 * @param str the string to be encoded.
	 * @param encoding the encoding in text form.
	 * @return the percent-encoded text.
	 */
	public static String encodeToHttp(String str, String encoding) {
		String enc;
		try {
			enc = URLEncoder.encode(str, encoding);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			Log.v("MewX-Net", Objects.requireNonNull(e.getMessage()));
			enc = ""; // prevent crash
		}
		return enc;
	}

	/**
	 * A post method, the values must to be <String, String> pair.
	 * @param u base url
	 * @param values <String, String> pair
	 * @return raw bytes or null!
	 */
	@Nullable
	public static byte[] LightHttpPostConnection(String u, ContentValues values) {
		return LightHttpPostConnection(u, values, true);
	}

	@Nullable
	public static byte[] LightHttpPostConnection(String u, ContentValues values, boolean withSession) {
		// new API, initial
		URL url;
		HttpURLConnection http;
		try {
			url = new URL(u);
			http = (HttpURLConnection) url.openConnection();
            http.setRequestMethod("POST");
			http.setRequestProperty("Accept-Encoding", "gzip"); // set gzip
			if(withSession && !LightUserSession.getSession().isEmpty()) {
				http.setRequestProperty("Cookie", "PHPSESSID=" + LightUserSession.getSession());
			}
			http.setConnectTimeout(3000);
			http.setReadTimeout(5000);
			http.setDoOutput(true); // has input name value pair
			http.setInstanceFollowRedirects(true); // enable redirects
		} catch (Exception e) {
			e.printStackTrace();
			return null; // null means failure
		}

		// make request args
		StringBuilder params = new StringBuilder();
		for( String key : values.keySet() ) {
			if( !(values.get(key) instanceof String)) continue;
			params.append("&").append(key).append("=").append(values.get(key)); // now, like "&a=1&b=1&c=1"
		}

		// request
		byte[] bytes = params.toString().getBytes();
		try {
			http.getOutputStream().write(bytes); // set args

			InputStream inStream=http.getInputStream(); // input stream
			ByteArrayOutputStream outStream = new ByteArrayOutputStream(); // output stream

			if(http.getContentEncoding() != null && http.getContentEncoding().toLowerCase().contains("gzip")) {
				// using 'gzip'
				inStream = new GZIPInputStream(new BufferedInputStream(inStream));
			}

			// get session, save it all the time, prevent getting new session id
			if (http.getHeaderField("Set-Cookie") != null && http.getHeaderField("Set-Cookie").contains("PHPSESSID")) {
				int index =http.getHeaderField("Set-Cookie").indexOf("PHPSESSID");
				LightUserSession.setSession(
						http.getHeaderField("Set-Cookie").substring(index + 9 + 1, http.getHeaderField("Set-Cookie").indexOf(";", index))
				);
			}

			byte[] buffer = new byte[1024];
			int len;
			while( (len = inStream.read(buffer)) !=-1 )
				outStream.write(buffer, 0, len); // read to outStream
			byte[] data = outStream.toByteArray(); // copy to ByteArray
			outStream.close();
			inStream.close();

			return data; // return value

		} catch (IOException e) {
			e.printStackTrace();
			return null; // null means failure
		}
	}

	/**
	 * LightHttpDownload:
	 * 
	 * Give direct url to download file in one time, so this only fits small
	 * size files.
	 * 
	 * @param url
	 *            : direct file url with extension
	 * @return return correct bytes or null
	 */
	@Nullable
	public static byte[] LightHttpDownload(String url) {

		InputStream inputStream;
		try {
			URL localURL = new URL(url);
			HttpURLConnection httpURLConnection = (HttpURLConnection)localURL.openConnection();
            httpURLConnection.setConnectTimeout(3000);
            httpURLConnection.setReadTimeout(8000);

			if (httpURLConnection.getResponseCode() != HttpURLConnection.HTTP_OK)
				throw new Exception("HTTP Request is not success, Response code is " + httpURLConnection.getResponseCode());

			inputStream = httpURLConnection.getInputStream();

            byte[] b = new byte[1024];
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            int len;
            while ((len = inputStream.read(b)) != -1) byteArrayOutputStream.write(b, 0, len);
            byteArrayOutputStream.close();

			inputStream.close();
            byteArrayOutputStream.close();
			return byteArrayOutputStream.toByteArray();
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

//	/**
//	 * Download to file, part by part, prevent OOM error.
//	 * @param url
//	 * @param filepath
//	 * @return
//	 */
//	public static boolean LightHttpDownloadToFile(String url, String filepath) {
//
//		// a replacer
////		url = url.replace(fromEle, toEle);
//
//		return false;
//	}

}
