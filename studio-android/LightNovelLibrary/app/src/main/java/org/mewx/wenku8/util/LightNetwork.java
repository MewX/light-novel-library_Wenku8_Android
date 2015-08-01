/**
 *  Light Network
 **
 *  This class achieve the basic network protocol:
 *      HttpPost ...
 **/

package org.mewx.wenku8.util;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.mewx.wenku8.global.api.Wenku8API;

import android.content.ContentValues;
import android.util.Log;

public class LightNetwork {
//    final static private String fromEle = ".cn/";
//    final static private String toEle = ".com/";

	/**
	 * "Status" is an entry to monitor vars 0 - resting 1 -
	 **/
	int status; // reserved

	LightNetwork() {
		status = 0;
		return;
	}

	/**
	 * encodeToHttp:
	 * 
	 * Encode UTF-8 character to http postable style. For example: "妹" =
	 * "%E5%A6%B9"
	 * 
	 * @param str
	 *            : input string
	 * @return: result encoded string or empty string
	 */
	public static String encodeToHttp(String str) {
		String enc;
		try {
			enc = URLEncoder.encode(str, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			Log.v("MewX-Net", e.getMessage());
			enc = ""; // prevent crash
		}
		return enc;
	}

	/**
	 * LightHttpPost:
	 * 
	 * Simple http post function, give url and NVP to get result from server
	 * 
	 * @param URL
	 *            : base url to post
	 * @param params
	 *            : post content NVP
	 * @return: return correct bytes or null
	 */
	@Deprecated
	public static byte[] LightHttpPost(String URL, List<NameValuePair> params) {
		// Post transfer through NameValuePair[ ] array
		// on server: request.getParameter("name")
		// List<NameValuePair> params = new ArrayList<NameValuePair>();
		// params.add(new BasicNameValuePair("name", "this is post"));

		// a replacer
//		URL = URL.replace(fromEle, toEle);

		HttpPost httpRequest = new HttpPost(URL);
        httpRequest.addHeader("Accept-Encoding", "gzip");
        Log.v("MewX-Net", "In LightHttpPost");
		try {

			// send HTTP request
			httpRequest.setEntity(new UrlEncodedFormEntity(params, HTTP.UTF_8));
			Log.v("MewX-Net", "httpRequest.");
			// get HTTP response
			HttpResponse httpResponse = new DefaultHttpClient()
					.execute(httpRequest);
			Log.v("MewX-Net", "httpResponse.");

			// if status code is 200, that's ok
			if (httpResponse.getStatusLine().getStatusCode() == 200) {
				Log.v("MewX-Net", "httpResponse.StatusCode == 200");
				// get result byte!!! CANNOT just get String, I need raw bytes
				//byte[] strResult = EntityUtils.toByteArray(httpResponse
				//		.getEntity());

                InputStream is = httpResponse.getEntity().getContent();
                Header contentEncoding = httpResponse
                        .getFirstHeader("Content-Encoding");
                if (contentEncoding != null
                        && contentEncoding.getValue().equalsIgnoreCase("gzip")) {
                    is = new GZIPInputStream(new BufferedInputStream(is));

                }

                byte[] buf = new byte[1024], strResult=null;
                int num = -1;
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                while ((num = is.read(buf, 0, buf.length)) != -1)
                    baos.write(buf, 0, num);
                strResult = baos.toByteArray();
                baos.close();

                Log.v("MewX-Net", new String(strResult, "utf-8"));

                return strResult;
			} else {
				Log.v("MewX-Net", "Error Response"
						+ httpResponse.getStatusLine().toString());
				return null;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * A post method, the values must to be <String, String> pair.
	 * @param u base url
	 * @param values <String, String> pair
	 * @return raw bytes or null!
	 */
	public static byte[] LightHttpPostConnection(String u, ContentValues values) {

		// a replacer
//		u = u.replace(fromEle, toEle);

		// new API, initial
		URL url = null;
		HttpURLConnection http = null;
		try {
			url = new URL(u);
			http = (HttpURLConnection) url.openConnection();
			http.setRequestMethod("POST");
			http.setRequestProperty("Accept-Encoding", "gzip"); // set gzip
			if(LightUserSession.getSession().length() != 0) {
				http.setRequestProperty("Cookie", "PHPSESSID=" + LightUserSession.getSession());
			}
			http.setConnectTimeout(5000);
			http.setReadTimeout(5000);
			http.setDoOutput(true); // has input name value pair
			http.setInstanceFollowRedirects(true); // enable redirects
		} catch (Exception e) {
			e.printStackTrace();
			return null; // null means failure
		}

		// make request args
		StringBuffer params = new StringBuffer("");
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

			if(http.getContentEncoding() != null && http.getContentEncoding().toLowerCase().indexOf("gzip") >= 0) {
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
			int len = 0;
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
	 * @return: return correct bytes or null
	 */
	public static byte[] LightHttpDownload(String url) {
//		// httpGet connection object
//		HttpGet httpRequest = new HttpGet(url);
//		// get HttpClient object
//		HttpClient httpclient = new DefaultHttpClient();
//		try {
//			// request httpClient, get HttpRestponse
//			HttpResponse httpResponse = httpclient.execute(httpRequest);
//			if (httpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
//				// // get HttpEntiy
//				// HttpEntity httpEntity = httpResponse.getEntity();
//				// // get InputStream
//				// InputStream is = httpEntity.getContent();
//				// Bitmap bitmap = BitmapFactory.decodeStream(is);
//				// is.close();
//
//				byte[] strResult = EntityUtils.toByteArray(httpResponse.getEntity());
//				return strResult;
//			} else {
//				Log.i("MewX", "HttpStatus bad. code: " + httpResponse.getStatusLine().getStatusCode());
//				return null;
//			}
//
//		} catch (Exception e) {
//			e.printStackTrace();
//			return null;
//		}

		// a replacer
//		url = url.replace(fromEle, toEle);

		InputStream inputStream = null;
		try {
			URL localURL = new URL(url);
			HttpURLConnection httpURLConnection = (HttpURLConnection)localURL.openConnection();

			if (httpURLConnection.getResponseCode() != HttpURLConnection.HTTP_OK)
				throw new Exception("HTTP Request is not success, Response code is " + httpURLConnection.getResponseCode());

			inputStream = httpURLConnection.getInputStream();

            byte[] b = new byte[1024];
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            int len = -1;
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

	/**
	 * Download to file, part by part, prevent OOM error.
	 * @param url
	 * @param filepath
	 * @return
	 */
	public static boolean LightHttpDownloadToFile(String url, String filepath) {

		// a replacer
//		url = url.replace(fromEle, toEle);

		return false;
	}

}
