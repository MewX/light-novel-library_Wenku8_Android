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

    // The cases below are the ones the old implementation got wrong. It only ever returned
    // null because XmlPullParser.next() threw, so anything well-formed came back as a blank
    // but non-null UserInfo and every caller's != null check passed.

    @Test
    public void parseUserInfoRejectsAWellFormedNonResponse() {
        // What a captive portal, a proxy interstitial or a CDN error page actually looks
        // like: perfectly well-formed, and not a user-info response.
        UserInfo ui = UserInfo.parseUserInfo(
                "<html><head><title>503 Service Unavailable</title></head>"
                        + "<body><p>Under maintenance.</p></body></html>");
        assertNull(ui);
    }

    @Test
    public void parseUserInfoRejectsMetadataWithNoItems() {
        assertNull(UserInfo.parseUserInfo(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<metadata>\n</metadata>"));
    }

    @Test
    public void parseUserInfoRejectsMetadataWithoutAUsername() {
        // Every other field present. Without the uname check this returned a UserInfo whose
        // username was null, which the UI rendered as a logged-in user with a blank name.
        assertNull(UserInfo.parseUserInfo("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<metadata>\n" +
                "<item name=\"nickname\"><![CDATA[apptest nick]]></item>\n" +
                "<item name=\"score\">100</item>\n" +
                "<item name=\"experience\">10</item>\n" +
                "<item name=\"rank\"><![CDATA[新手上路]]></item>\n" +
                "</metadata>"));
    }

    @Test
    public void parseUserInfoAcceptsAUsernameWithNoOtherFields() {
        // The check is deliberately only on uname. A real response always carries it, and
        // requiring more would reject accounts whose optional fields the server omits --
        // note the sample above has no uid, which is why uid is not required either.
        UserInfo ui = UserInfo.parseUserInfo("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<metadata>\n" +
                "<item name=\"uname\"><![CDATA[apptest]]></item>\n" +
                "</metadata>");
        assertNotNull(ui);
        assertEquals("apptest", ui.username);
        assertEquals(0, ui.score);
    }
}