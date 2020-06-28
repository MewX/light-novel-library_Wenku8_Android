package org.mewx.wenku8.util;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

@SmallTest
public class LightUserSessionTest {

    @Test
    public void decodeThenEncodeUserInfo() {
        final String src = "Z1NxZFhlPT0=|Z1dwUlhiPT0=";
        LightUserSession.decAndSetUserFile(src);
        assertEquals("abc", LightUserSession.getUsername());
        assertEquals("123", LightUserSession.getPassword());
        assertEquals(src, LightUserSession.encUserFile());
    }

    @Test
    public void encodeThenDecodeUserInfo() {
        LightUserSession.setUserInfo("xyz", "987");
        LightUserSession.decAndSetUserFile(LightUserSession.encUserFile());
        assertEquals("xyz", LightUserSession.getUsername());
        assertEquals("987", LightUserSession.getPassword());
    }
}
