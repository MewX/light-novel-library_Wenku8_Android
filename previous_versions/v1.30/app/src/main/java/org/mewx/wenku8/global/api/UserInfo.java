package org.mewx.wenku8.global.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import org.mewx.wenku8.api.Wenku8API;
import org.mewx.wenku8.util.CrashReporter;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;

/**
 * Created by MewX on 2015/6/14.
 * User Info.
 */
public class UserInfo {
    /*
     * <?xml version="1.0" encoding="utf-8"?>
     * <metadata>
     * <item name="uname"><![CDATA[apptest]]></item>
     * <item name="nickname"><![CDATA[apptest]]></item>
     * <item name="uid">123</item>
     * <item name="score">10</item>
     * <item name="experience">10</item>
     * <item name="rank"><![CDATA[新手上路]]></item>
     * </metadata>
     */

    public String username;
    public String nickyname;
    public int uid;
    public int score; // 现有积分
    public int experience; // 经验值
    public String rank;

    @Nullable
    public static UserInfo parseUserInfo(@NonNull String xml) {
        try {
            UserInfo ui = new UserInfo();
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser xmlPullParser = factory.newPullParser();
            xmlPullParser.setInput(new StringReader(xml));
            int eventType = xmlPullParser.getEventType();

            while (eventType != XmlPullParser.END_DOCUMENT) {
                switch (eventType) {
                    case XmlPullParser.START_DOCUMENT:
                        break;

                    case XmlPullParser.START_TAG:
                        if ("metadata".equals(xmlPullParser.getName())) {
                            // root tag
                            break;
                        } else if ("item".equals(xmlPullParser.getName())) {
                            if ("uname".equals(xmlPullParser.getAttributeValue(0))) {
                                ui.username = xmlPullParser.nextText();
                                Log.d("MewX", ui.username.isEmpty() ? Wenku8API.UNKNOWN : ui.username);
                            } else if ("nickname".equals(xmlPullParser.getAttributeValue(0))) {
                                ui.nickyname = xmlPullParser.nextText();
                                Log.d("MewX", ui.nickyname.isEmpty() ? Wenku8API.UNKNOWN : ui.nickyname);
                            } else if ("uid".equals(xmlPullParser.getAttributeValue(0))) {
                                ui.uid = Integer.valueOf(xmlPullParser.nextText());
                                Log.d("MewX", "uid:" + ui.uid);
                            } else if ("score".equals(xmlPullParser.getAttributeValue(0))) {
                                ui.score = Integer.valueOf(xmlPullParser.nextText());
                                Log.d("MewX", "score:" + ui.score);
                            } else if ("experience".equals(xmlPullParser.getAttributeValue(0))) {
                                ui.experience = Integer.valueOf(xmlPullParser.nextText());
                                Log.d("MewX", "experience:" + ui.experience);
                            } else if ("rank".equals(xmlPullParser.getAttributeValue(0))) {
                                ui.rank = xmlPullParser.nextText();
                                Log.d("MewX", ui.rank.isEmpty() ? Wenku8API.UNKNOWN : ui.rank);
                            }
                        }
                        break;
                }
                eventType = xmlPullParser.next();
            }

            // Until now the only way this returned null was XmlPullParser.next() throwing on
            // malformed input. Well-formed XML that simply is not a user-info response -- an
            // HTML maintenance page, a captive-portal or proxy interstitial, a CDN error page
            // -- walked the loop, matched no item, and came back as a blank but non-null
            // UserInfo. Every caller's != null check passed, so it surfaced as a logged-in
            // user with an empty name and zero score instead of as a parse error.
            if (ui.username == null) {
                CrashReporter.log("parseUserInfo: well-formed XML with no uname item, "
                        + "length=" + xml.length());
                return null;
            }
            return ui;
        } catch (Exception e) {
            CrashReporter.recordException("UserInfo.parseUserInfo", e);
            return null;
        }
    }
}
