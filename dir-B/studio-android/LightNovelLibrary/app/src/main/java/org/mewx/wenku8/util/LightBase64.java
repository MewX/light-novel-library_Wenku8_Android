/**
 *  Light Base64
 **
 *  This class achieve the basic base64 en/decryption:
 *      use "Base64.DEFAULT" to encrypt or decrypt text.
 **/

package org.mewx.wenku8.util;

import java.io.UnsupportedEncodingException;

import android.util.Base64;

public class LightBase64 {
	static public String EncodeBase64(byte[] b) {
		return Base64.encodeToString( b, Base64.DEFAULT);
	}

	static public String EncodeBase64( String s ) {
        try {
			return EncodeBase64( s.getBytes("UTF-8") );
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	static public byte[ ] DecodeBase64( String s ) {
		try {
			byte[] b;
			b = Base64.decode(s, Base64.DEFAULT);
			return b;
		}
		catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	static public String DecodeBase64String(String s) {
		try {
			return new String( DecodeBase64(s), "UTF-8" );
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}
