/**
 *  Light Network
 **
 *  This class achieve the basic network protocol:
 *      HttpPost ...
 **/

package org.mewx.wenku8.util;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
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

import android.util.Log;

public class LightNetwork {
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
	public static byte[] LightHttpPost(String URL, List<NameValuePair> params) {
		// Post transfer through NameValuePair[ ] array
		// on server: request.getParameter("name")
		// List<NameValuePair> params = new ArrayList<NameValuePair>();
		// params.add(new BasicNameValuePair("name", "this is post"));

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
			Log.v("MewX-Net", e.getMessage());
			e.printStackTrace();
			return null;
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
		// httpGet connection object
		HttpGet httpRequest = new HttpGet(url);
		// get HttpClient object
		HttpClient httpclient = new DefaultHttpClient();
		try {
			// request httpClient, get HttpRestponse
			HttpResponse httpResponse = httpclient.execute(httpRequest);
			if (httpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				// // get HttpEntiy
				// HttpEntity httpEntity = httpResponse.getEntity();
				// // get InputStream
				// InputStream is = httpEntity.getContent();
				// Bitmap bitmap = BitmapFactory.decodeStream(is);
				// is.close();

				byte[] strResult = EntityUtils.toByteArray(httpResponse
						.getEntity());
				return strResult;
			} else {
				Log.i("MewX", "HttpStatus bad.");
				return null;
			}

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}

}
