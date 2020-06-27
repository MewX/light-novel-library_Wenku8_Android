package org.mewx.wenku8.util;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@SmallTest
public class LightBase64Test {
    private final byte[] source;
    private final String sourceStr, encodedStr;

    public LightBase64Test() {
        source = new byte[]{0x66, 0x6f, 0x6f, 0x62, 0x61, 0x72};
        sourceStr = "foobar";
        encodedStr = "Zm9vYmFy";
    }

    @Test
    public void encodeBase64ByteToString() {
        assertEquals(encodedStr, LightBase64.EncodeBase64(source));
    }

    @Test
    public void encodeBase64ByteToStringEmpty() {
        assertTrue(LightBase64.EncodeBase64(new byte[0]).isEmpty());
    }

    @Test
    public void encodeBase64StringToStringEmpty() {
        assertTrue(LightBase64.EncodeBase64("").isEmpty());
    }

    @Test
    public void decodeBase64StringToString() {
        assertEquals(sourceStr, LightBase64.DecodeBase64String(encodedStr));
    }

    @Test
    public void decodeBase64StringToStringInvalid() {
        assertTrue(LightBase64.DecodeBase64String("!@#$%^&*()_+").isEmpty());
    }

    @Test
    public void decodeBase64StringToByte() {
        assertArrayEquals(source, LightBase64.DecodeBase64(encodedStr));
    }

    @Test
    public void decodeBase64StringToByteInvalid() {
        assertEquals(0, LightBase64.DecodeBase64("!@#$%^&*()_+").length);
    }
}