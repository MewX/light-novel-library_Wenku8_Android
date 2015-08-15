package org.mewx.wenku8.global.api;

import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;

/**
 * Created by MewX on 2015/6/14.
 * User Info.
 */
public class UserInfo {
    /**
     * <?xml version="1.0" encoding="utf-8"?>
     * <metadata>
     * <item name="uname"><![CDATA[apptest]]></item>
     * <item name="nickname"><![CDATA[apptest]]></item>
     * <item name="score">10</item>
     * <item name="experience">10</item>
     * <item name="rank"><![CDATA[新手上路]]></item>
     * </metadata>
     */

    public String username;
    public String nickyname;
    public int score; // 现有积分
    public int experience; // 经验值
    public String rank;

    public static UserInfo parseUserInfo(UserInfo ui, String xml) {

        try {
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
                            if("uname".equals(xmlPullParser.getAttributeValue(0))) {
                                ui.username = xmlPullParser.nextText();
                                Log.e("MewX", ui.username.length() == 0 ? "unknown" : ui.username);
                            }
                            else if("nickname".equals(xmlPullParser.getAttributeValue(0))) {
                                ui.nickyname = xmlPullParser.nextText();
                                Log.e("MewX", ui.nickyname.length() == 0 ? "unknown" : ui.nickyname);
                            }
                            else if("score".equals(xmlPullParser.getAttributeValue(0))) {
                                ui.score = new Integer(xmlPullParser.nextText());
                                Log.e("MewX", "score:" + ui.score);
                            }
                            else if("experience".equals(xmlPullParser.getAttributeValue(0))) {
                                ui.experience = new Integer(xmlPullParser.nextText());
                                Log.e("MewX", "experience:" + ui.experience);
                            }
                            else if("rank".equals(xmlPullParser.getAttributeValue(0))) {
                                ui.rank = xmlPullParser.nextText();
                                Log.e("MewX", ui.rank.length() == 0 ? "unknown" : ui.rank);
                            }
                        }
                        break;
                }
                eventType = xmlPullParser.next();
            }
            return ui;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
