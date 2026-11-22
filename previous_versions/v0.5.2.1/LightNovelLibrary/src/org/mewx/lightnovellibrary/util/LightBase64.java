/**
 *  Light Base64
 **
 *  This class achieve the basic base64 en/decryption:
 *      use "Base64.DEFAULT" to encrypt or decrypt text.
 **/

package org.mewx.lightnovellibrary.util;

import java.io.UnsupportedEncodingException;

import android.util.Base64;

public class LightBase64 {
	LightBase64( ) {
		return;
	}
	
	static public String EncodeBase64(byte[] b) {
        String str = Base64.encodeToString( b, Base64.DEFAULT);
        return str;
	}

	static public String EncodeBase64( String s ) {
        try {
			return EncodeBase64( s.getBytes("UTF-8") );
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}
	
	static public byte[ ] DecodeBase64( String s ) {
		byte[] b;
        b = Base64.decode( s, Base64.DEFAULT);
        return b;
	}
}
