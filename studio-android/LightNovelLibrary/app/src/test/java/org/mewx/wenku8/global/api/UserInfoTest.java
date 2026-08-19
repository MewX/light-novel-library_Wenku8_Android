package org.mewx.wenku8.global.api;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class UserInfoTest {
    private static final String USER_INFO_XML = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<metadata>\n" +
            "<item name=\"uname\"><![CDATA[apptest]]></item>\n" +
            "<item name=\"nickname\"><![CDATA[apptest nick]]></item>\n" +
            "<item name=\"score\">100</item>\n" +
            "<item name=\"experience\">10</item>\n" +
            "<item name=\"rank\"><![CDATA[新手上路]]></item>\n" +
            "</metadata>";

    @Test
    public void parseUserInfo() {
        UserInfo ui = UserInfo.parseUserInfo(USER_INFO_XML);
        assertNotNull(ui);
        assertEquals("apptest", ui.username);
        assertEquals("apptest nick", ui.nickyname);
        assertEquals(10, ui.experience);
        assertEquals(100, ui.score);
        assertEquals("新手上路", ui.rank);
    }

    @Test
    public void parseInvalidUserInfo() {
        UserInfo ui = UserInfo.parseUserInfo("adfsdfasdfasdf");
        assertNull(ui);
    }
}